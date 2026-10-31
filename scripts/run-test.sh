#!/usr/bin/env bash
# 초기화 -> 행사/재고/사용자 준비 -> k6 -> 검증 -> 결과 기록. env: USERS(5000) SALE_QUANTITY(100)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/scripts/lib.sh"
cd "$ROOT"

USERS="${USERS:-5000}"
SALE_QUANTITY="${SALE_QUANTITY:-100}"

command -v jq >/dev/null || { echo "jq 가 필요하다" >&2; exit 1; }
command -v k6 >/dev/null || { echo "k6 가 필요하다" >&2; exit 1; }

if [ "$(ulimit -n)" != "unlimited" ] && [ "$(ulimit -n)" -lt 65536 ]; then
  ulimit -n 65536 2>/dev/null || echo "WARN: ulimit -n 을 65536 으로 올리지 못했다" >&2
fi

wait_healthy
strategy=$(current_strategy)
RUN_ID="$(date +%Y%m%d-%H%M%S)-${strategy}"
RUN_DIR="$ROOT/docs/results/runs/$RUN_ID"
mkdir -p "$RUN_DIR"
TOKENS_FILE="$ROOT/load-test/k6/.data/tokens.json"
mkdir -p "$(dirname "$TOKENS_FILE")"

reset_data
out=$(create_event "$SALE_QUANTITY")
read -r EVENT_ID PRODUCT_ID <<<"$out"
set_stock "$PRODUCT_ID" "$SALE_QUANTITY"
issue_tokens "$USERS" "$TOKENS_FILE"
started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)

k6_args=(run --summary-export "$RUN_DIR/k6-summary.json" --tag "testid=$RUN_ID"
  -e "EVENT_ID=$EVENT_ID" -e "USERS=$USERS" -e "TOKENS_FILE=$TOKENS_FILE")
if curl -sf --max-time 3 http://localhost:9090/-/ready >/dev/null 2>&1; then
  k6_args+=(-o experimental-prometheus-rw)
  export K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write
  export K6_PROMETHEUS_RW_TREND_STATS='p(95),p(99),avg,max'
else
  echo "WARN: Prometheus 가 준비되지 않아 k6 지표를 원격 기록하지 않는다" >&2
fi

t0=$(date +%s)
k6 "${k6_args[@]}" "$ROOT/load-test/k6/flash-sale.js" 2>&1 | tee "$RUN_DIR/k6-stdout.txt"
duration=$(( $(date +%s) - t0 ))

wait_orders_settled "$EVENT_ID" 60
"$ROOT/scripts/verify.sh" "$EVENT_ID" "$PRODUCT_ID" "$SALE_QUANTITY" > "$RUN_DIR/verify.json"
order_stats "$EVENT_ID" > "$RUN_DIR/order-stats.json"

commit=$(git rev-parse --short HEAD)
[ -n "$(git status --porcelain -- . ':!docs/results')" ] && commit="${commit}-dirty"
pay=$(payment_config)

S="$RUN_DIR/k6-summary.json"
order_cfg() { grep -E "^  $1:" order-service/src/main/resources/application.yml | awk '{print $2}'; }
gw_cfg() { grep -E '^    read-timeout:' gateway/src/main/resources/application.yml | awk '{print $2}'; }
{
  echo "# 실행 결과 $RUN_ID"
  echo
  echo "- RUN_ID: $RUN_ID"
  echo "- 실행 시각: $started_at"
  echo "- git 커밋: $commit"
  echo "- 전략: $strategy"
  echo "- USERS: $USERS"
  echo "- SALE_QUANTITY: $SALE_QUANTITY"
  echo "- payment 설정: \`$(echo "$pay" | jq -c .)\`"
  echo
  echo "## 검증"
  jq -r '"- saleQuantity: \(.saleQuantity)\n- paid: \(.paid)\n- failed: \(.failed)\n- pending: \(.pending)\n- finalStock: \(.finalStock)\n- oversold: \(.oversold)\n- stockMismatch: \(.stockMismatch)\n- verdict: \(.verdict)"' "$RUN_DIR/verify.json"
  echo
  k6paid=$(jq -r '.metrics.orders_paid.count // 0' "$S")
  dbpaid=$(jq -r '.paid' "$RUN_DIR/verify.json")
  if [ "$k6paid" = "$dbpaid" ]; then
    echo "- k6 orders_paid vs DB paid: $k6paid / $dbpaid (일치)"
  else
    echo "- k6 orders_paid vs DB paid: $k6paid / $dbpaid (불일치: 결과를 신뢰하지 말 것)"
  fi
  echo
  echo "## 고정 설정"
  echo "- order-service -> inventory/payment 클라이언트 connect/read timeout: $(order_cfg connect-timeout) / $(order_cfg read-timeout) (order-service application.yml)"
  echo "- gateway 하위 호출 read timeout: $(gw_cfg) (gateway application.yml)"
  echo "- k6 요청 timeout: $(grep -o "timeout: '[0-9a-z]*'" load-test/k6/flash-sale.js | head -1 | cut -d"'" -f2), executor: $(grep -o "executor: '[a-z-]*'" load-test/k6/flash-sale.js | cut -d"'" -f2), VU당 iterations: $(grep -o 'iterations: [0-9]*' load-test/k6/flash-sale.js | cut -d' ' -f2)"
  echo "- 컨테이너 제한: 앱 cpus 2 / 640m, MySQL cpus 4 / 1536m (compose.yaml)"
  echo "- Tomcat/Hikari 기본값, virtual threads off"
  echo
  echo "## 실패 사유별 건수"
  jq -r 'if (.failedByReason | length) == 0 then "- 없음" else (.failedByReason | to_entries[] | "- \(.key): \(.value)") end' "$RUN_DIR/order-stats.json"
  echo
  echo "## k6"
  jq -r --arg d "$duration" '.metrics as $m |
    "- 총 요청 수: \($m.http_reqs.count)\n- RPS: \($m.http_reqs.rate * 100 | round / 100)\n- http_req_duration avg(ms): \($m.http_req_duration.avg * 100 | round / 100)\n- http_req_duration p95(ms): \($m.http_req_duration["p(95)"] * 100 | round / 100)\n- http_req_duration p99(ms): \($m.http_req_duration["p(99)"] * 100 | round / 100)\n- http_req_duration max(ms): \($m.http_req_duration.max * 100 | round / 100)\n- http_req_failed 비율: \($m.http_req_failed.value)\n- orders_paid: \($m.orders_paid.count // 0)\n- orders_sold_out: \($m.orders_sold_out.count // 0)\n- orders_other: \($m.orders_other.count // 0)\n- 테스트 소요 시간(초): \($d)"' "$S"
} > "$RUN_DIR/report.md"

row=$(jq -r --arg run "$RUN_ID" --arg st "$strategy" --argjson u "$USERS" --argjson q "$SALE_QUANTITY" \
  --slurpfile v "$RUN_DIR/verify.json" '
  .metrics as $m | $v[0] as $v |
  "| \($run) | \($st) | \($u) | \($q) | \($v.paid) | \($v.oversold) | \($v.verdict) | \($m.http_req_duration["p(95)"] * 100 | round / 100) | \($m.http_req_duration["p(99)"] * 100 | round / 100) | \($m.http_reqs.rate * 100 | round / 100) |"' "$S")
echo "$row" >> "$ROOT/docs/results/README.md"

jq -r '.verdict' "$RUN_DIR/verify.json"
