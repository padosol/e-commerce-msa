#!/usr/bin/env bash
# 초기화 -> 행사/재고/사용자 준비 -> k6 -> 검증 -> 결과 기록. env: USERS(500) SALE_QUANTITY(100) WARMUP(0; 1 이면 워밍업으로 표시)
# ARRIVAL_SECONDS(1): USERS 건의 요청이 이 시간(초) 동안 고르게 도착. 0 이면 USERS 명이 같은 순간에 요청(0단계~1단계 4차 방식, USERS 5000)
# K6_RUNNER: docker(기본) = Docker 네트워크 안 k6 컨테이너가 gateway:8080 에 직접 연결 / host = 호스트 k6 가 localhost:8080(Docker Desktop 포트 포워딩, 1단계 3차까지)
# K6_IMAGE(grafana/k6:2.2.0) K6_NETWORK(e-commerce_default) K6_CPUS(4) K6_MEMORY(2g): docker 실행 시 k6 컨테이너 자원 한도(빈 값이면 제한 없음)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/scripts/lib.sh"
cd "$ROOT"

USERS="${USERS:-500}"
ARRIVAL_SECONDS="${ARRIVAL_SECONDS:-1}"
SALE_QUANTITY="${SALE_QUANTITY:-100}"
WARMUP="${WARMUP:-0}"

command -v jq >/dev/null || { echo "jq 가 필요하다" >&2; exit 1; }
K6_RUNNER="${K6_RUNNER:-docker}"
K6_IMAGE="${K6_IMAGE:-grafana/k6:2.2.0}"
K6_NETWORK="${K6_NETWORK:-e-commerce_default}"
K6_CPUS="${K6_CPUS-4}"
K6_MEMORY="${K6_MEMORY-2g}"
case "$K6_RUNNER" in
  docker) command -v docker >/dev/null || { echo "docker 가 필요하다" >&2; exit 1; } ;;
  host) command -v k6 >/dev/null || { echo "k6 가 필요하다" >&2; exit 1; } ;;
  *) echo "K6_RUNNER 는 docker 또는 host 여야 한다: $K6_RUNNER" >&2; exit 1 ;;
esac

if [ "$(ulimit -n)" != "unlimited" ] && [ "$(ulimit -n)" -lt 65536 ]; then
  ulimit -n 65536 2>/dev/null || echo "WARN: ulimit -n 을 65536 으로 올리지 못했다" >&2
fi

wait_healthy
strategy=$(current_strategy)
RUN_ID="$(date +%Y%m%d-%H%M%S)-${strategy}"
kind=measure
if [ "$WARMUP" = "1" ]; then RUN_ID="${RUN_ID}-warmup"; kind=warmup; fi
RUN_DIR="$ROOT/docs/results/runs/$RUN_ID"
mkdir -p "$RUN_DIR"
TOKENS_FILE="$ROOT/load-test/k6/.data/tokens.json"
mkdir -p "$(dirname "$TOKENS_FILE")"

reset_data
out=$(create_event "$SALE_QUANTITY")
read -r EVENT_ID PRODUCT_ID <<<"$out"
set_stock "$PRODUCT_ID" "$SALE_QUANTITY"
issue_tokens "$USERS" "$TOKENS_FILE"
read -r drain_wait_sec time_wait_before <<<"$(wait_tcp_drained 500 120)"
started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)

if [ "$K6_RUNNER" = docker ]; then
  k6_args=(run --summary-export /out/k6-summary.json --tag "testid=$RUN_ID"
    -e "BASE_URL=http://gateway:8080" -e "EVENT_ID=$EVENT_ID" -e "USERS=$USERS" -e "ARRIVAL_SECONDS=$ARRIVAL_SECONDS" -e "TOKENS_FILE=/k6/.data/tokens.json")
  rw_url="http://prometheus:9090/api/v1/write"
else
  k6_args=(run --summary-export "$RUN_DIR/k6-summary.json" --tag "testid=$RUN_ID"
    -e "EVENT_ID=$EVENT_ID" -e "USERS=$USERS" -e "ARRIVAL_SECONDS=$ARRIVAL_SECONDS" -e "TOKENS_FILE=$TOKENS_FILE")
  rw_url="$PROMETHEUS_URL/api/v1/write"
fi
rw_env=()
if curl -sf --max-time 3 "$PROMETHEUS_URL/-/ready" >/dev/null 2>&1; then
  k6_args+=(-o experimental-prometheus-rw)
  rw_env=(K6_PROMETHEUS_RW_SERVER_URL="$rw_url" K6_PROMETHEUS_RW_TREND_STATS='p(95),p(99),avg,max')
else
  echo "WARN: Prometheus 가 준비되지 않아 k6 지표를 원격 기록하지 않는다" >&2
fi

lock_before=$(mysql_lock_status)
t_start=$(date +%s)
if [ "$K6_RUNNER" = docker ]; then
  docker_opts=(--rm --network "$K6_NETWORK" --ulimit nofile=65536:65536
    -v "$ROOT/load-test/k6:/k6:ro" -v "$RUN_DIR:/out")
  [ -n "$K6_CPUS" ] && docker_opts+=(--cpus "$K6_CPUS")
  [ -n "$K6_MEMORY" ] && docker_opts+=(--memory "$K6_MEMORY")
  for kv in ${rw_env[@]+"${rw_env[@]}"}; do docker_opts+=(-e "$kv"); done
  docker run "${docker_opts[@]}" "$K6_IMAGE" "${k6_args[@]}" /k6/flash-sale.js 2>&1 | tee "$RUN_DIR/k6-stdout.txt"
else
  env ${rw_env[@]+"${rw_env[@]}"} k6 "${k6_args[@]}" load-test/k6/flash-sale.js 2>&1 | tee "$RUN_DIR/k6-stdout.txt"
fi
t_end=$(date +%s)
duration=$(( t_end - t_start ))

wait_orders_settled "$EVENT_ID" "$USERS" 60
sleep 2   # 마지막 스크레이프
lock_after=$(mysql_lock_status)
"$ROOT/scripts/verify.sh" "$EVENT_ID" "$PRODUCT_ID" "$SALE_QUANTITY" "$USERS" "$RUN_DIR/k6-summary.json" > "$RUN_DIR/verify.json"
order_stats "$EVENT_ID" > "$RUN_DIR/order-stats.json"
inv_stats=$(inventory_stats)

commit=$(git rev-parse --short HEAD)
[ -n "$(git status --porcelain -- . ':!docs/results')" ] && commit="${commit}-dirty"
pay=$(payment_config)
my_settings=$(mysql_settings)

if [ "$K6_RUNNER" = docker ]; then
  k6_desc="$K6_IMAGE"; k6_cpus_rec="${K6_CPUS:-none}"; k6_mem_rec="${K6_MEMORY:-none}"
else
  k6_desc="$(k6 version 2>/dev/null | head -1 | cut -d' ' -f1-2 || true)"; k6_cpus_rec="n/a"; k6_mem_rec="n/a"
fi
jvm=$(jvm_settings "$(( t_end + 2 ))")
jq -n --arg r "$RUN_ID" --arg s "$strategy" --arg k "$kind" --argjson u "$USERS" --argjson q "$SALE_QUANTITY" --arg c "$commit" \
  --argjson dw "$drain_wait_sec" --argjson tw "$time_wait_before" \
  --arg kr "$K6_RUNNER" --arg ki "$k6_desc" --arg kc "$k6_cpus_rec" --arg km "$k6_mem_rec" --argjson jvm "$jvm" \
  --argjson as "$ARRIVAL_SECONDS" \
  '{runId: $r, strategy: $s, kind: $k, users: $u, saleQuantity: $q, arrivalSeconds: $as, commit: $c,
    tcpDrainWaitSec: $dw, tcpTimeWaitBefore: $tw,
    k6Runner: $kr, k6Image: $ki, k6Cpus: $kc, k6Memory: $km, jvm: $jvm}' > "$RUN_DIR/meta.json"

# Prometheus 구간 질의: 구간 W = 실행 시간 + 4s, 평가 시각 = t_end + 2
W="$(( t_end - t_start + 4 ))s"
t_eval=$(( t_end + 2 ))
prom_max() { prom_query "max(max_over_time($1{job=\"$2\"}[$W]))" "$t_eval"; }
num_or_na() { jq -n --arg v "$1" '$v | tonumber? // "n/a"'; }
svc_json() { # <metric> <jobprefix...> -> {name: value}
  local metric="$1" name val out="{}"; shift
  for name in "$@"; do
    val=$(prom_max "$metric" "$([ "$name" = gateway ] && echo gateway || echo "$name-service")")
    out=$(jq -c --arg n "$name" --argjson v "$(num_or_na "$val")" '. + {($n): $v}' <<<"$out")
  done
  echo "$out"
}
hikari=$(svc_json hikaricp_connections_pending inventory order payment product)
tomcat=$(svc_json tomcat_threads_busy_threads gateway inventory order payment product)
max_waits=$(prom_query "max_over_time(mysql_global_status_innodb_row_lock_current_waits[$W])" "$t_eval")
p95=$(prom_query "histogram_quantile(0.95, sum by (le) (increase(http_server_requests_seconds_bucket{job=\"inventory-service\",uri=\"/internal/inventory/{productId}/decrease\"}[$W]))) * 1000" "$t_eval")
hikari_acq=$(hikari_acquire_json "$W" "$t_eval")
gc_pause=$(gc_pause_json "$W" "$t_eval")

jq -n --argjson b "$lock_before" --argjson a "$lock_after" --argjson mw "$(num_or_na "$max_waits")" \
  --argjson hk "$hikari" --argjson tc "$tomcat" --argjson inv "$inv_stats" --argjson p95 "$(num_or_na "$p95")" \
  --argjson ha "$hikari_acq" --argjson gp "$gc_pause" \
  --slurpfile v "$RUN_DIR/verify.json" '
  $v[0] as $v
  | (if ($a.waits | type) == "number" and ($b.waits | type) == "number" then $a.waits - $b.waits else "n/a" end) as $w
  | (if ($a.timeMs | type) == "number" and ($b.timeMs | type) == "number" then $a.timeMs - $b.timeMs else "n/a" end) as $t
  | {mysql: {lockWaits: $w, lockTimeMs: $t,
             avgLockWaitMs: (if ($w | type) != "number" or ($t | type) != "number" then "n/a"
                             elif $w > 0 then ($t / $w * 100 | round / 100) else 0 end),
             maxCurrentWaits: $mw},
     hikariPendingMax: $hk,
     tomcatBusyMax: $tc,
     hikariAcquire: $ha,
     gcPause: $gp,
     optimistic: {conflicts: $inv.conflicts, retries: $inv.retries, exhausted: $inv.exhausted},
     timeouts: $v.timeouts,
     inventoryDecreaseP95Ms: (if ($p95|type) == "number" then ($p95 * 100 | round / 100) else $p95 end),
     decreaseCalls: ($v.paid + $v.failed),
     paymentCalls: ($v.paid + ($v.failedByReason.PAYMENT_TIMEOUT // 0) + ($v.failedByReason.PAYMENT_ERROR // 0) + ($v.failedByReason.PAYMENT_DECLINED // 0))}' \
  > "$RUN_DIR/causes.json"

scrape_interval=$(awk '/^global:/{g=1} g && /scrape_interval:/{print $2; exit}' infra/prometheus/prometheus.yml)

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
  echo "- kind: $kind"
  echo "- USERS: $USERS"
  echo "- 요청 도착: $(jq -r 'if .arrivalSeconds > 0 then "\(.users)명이 \(.arrivalSeconds)초 동안 같은 간격으로 1번씩" else "\(.users)명이 같은 순간에 1번씩" end' "$RUN_DIR/meta.json")"
  echo "- SALE_QUANTITY: $SALE_QUANTITY"
  echo "- payment 설정: \`$(echo "$pay" | jq -c .)\`"
  echo
  echo "## 검증"
  jq -r '"- saleQuantity: \(.saleQuantity)\n- paid: \(.paid)\n- undersold: \(.undersold)\n- failed: \(.failed)\n- pending: \(.pending)\n- preOrderFailed: \(.preOrderFailed)\n- finalStock: \(.finalStock)\n- oversold: \(.oversold)\n- stockMismatch: \(.stockMismatch)\n- conflicts: \(.conflicts)\n- timeouts: \(.timeouts | to_entries | map("\(.key) \(.value)") | join(", "))\n- k6 응답: \(.k6 | to_entries | map("\(.key) \(.value)") | join(", "))\n- verdict: \(.verdict)\n- inconclusive: \(if (.inconclusive | length) == 0 then "없음" else (.inconclusive | join(", ")) end)"' "$RUN_DIR/verify.json"
  echo
  echo "## 원인 지표"
  jq -r '"- MySQL 행 락 대기 횟수: \(.mysql.lockWaits)\n- MySQL 행 락 누적 대기 시간(ms): \(.mysql.lockTimeMs)\n- MySQL 행 락 평균 대기(ms): \(.mysql.avgLockWaitMs)\n- MySQL 현재 락 대기 수 최댓값: \(.mysql.maxCurrentWaits) (1s 표본)\n- Hikari pending 최댓값: \(.hikariPendingMax | to_entries | map("\(.key) \(.value)") | join(", ")) (1s 표본)\n- Tomcat busy threads 최댓값: \(.tomcatBusyMax | to_entries | map("\(.key) \(.value)") | join(", ")) (1s 표본)\n- 낙관적 락: 충돌 \(.optimistic.conflicts), 재시도 \(.optimistic.retries), 최종 CONFLICT \(.optimistic.exhausted)\n- 계층별 타임아웃: \(.timeouts | to_entries | map("\(.key) \(.value)") | join(", "))\n- inventory 차감 서버 측 p95(ms): \(.inventoryDecreaseP95Ms) (1s 표본)\n- 차감 호출 수: \(.decreaseCalls)\n- 결제 호출 수: \(.paymentCalls)"' "$RUN_DIR/causes.json"
  jq -r '"- Hikari 커넥션 획득 평균 대기(ms): \(.hikariAcquire | to_entries | map("\(.key) \(.value.avgMs) (\(.value.acquisitions)회)") | join(", ")) (카운터 차이)",
    "- GC 정지(구간 안, 횟수 / 합 / 1s 표본 최대): \(.gcPause | to_entries | map(select(.value | length > 0) | "\(.key) " + (.value | to_entries | map("\(.key) \(.value.count)회 / \(.value.totalMs)ms / \(.value.max1sMs)ms") | join(", "))) | if length == 0 then "없음" else join("; ") end)"' "$RUN_DIR/causes.json"
  echo
  echo "## 고정 설정"
  echo "- order-service -> inventory/payment 클라이언트 connect/read timeout: $(order_cfg connect-timeout) / $(order_cfg read-timeout) (order-service application.yml)"
  echo "- gateway 하위 호출 read timeout: $(gw_cfg) (gateway application.yml)"
  echo "- k6 요청 timeout: $(grep -o "timeout: '[0-9a-z]*'" load-test/k6/flash-sale.js | head -1 | cut -d"'" -f2), executor: per-vu-iterations, 사용자당 요청 1번"
  echo "- 앱 컨테이너(실행 중 컨테이너·Prometheus 에서 확인): $(jq -r '.jvm | to_entries | map("\(.key) cpus \(.value.cpus) / \(.value.memoryMiB)MiB / \(.value.gc) GC / JAVA_TOOL_OPTIONS `\(.value.javaToolOptions)`") | join(", ")' "$RUN_DIR/meta.json")"
  echo "- MySQL 컨테이너 제한: cpus 4 / 1536m (compose.yaml)"
  echo "- Tomcat/Hikari 기본값, virtual threads off"
  echo "- MySQL 격리 수준: $(jq -r .isolation <<<"$my_settings"), innodb_lock_wait_timeout: $(jq -r .lockWaitTimeout <<<"$my_settings")s"
  echo "- Hikari connectionTimeout: 30s (기본값)"
  echo "- Prometheus 스크레이프 간격: $scrape_interval (infra/prometheus/prometheus.yml)"
  echo "- k6 시작 전 호스트 TIME_WAIT 대기: ${drain_wait_sec}초 (시작 시 ${time_wait_before}개, 기준 < 500)"
  if [ "$K6_RUNNER" = docker ]; then
    echo "- k6 실행 위치: Docker 네트워크 안 컨테이너 ($k6_desc, network $K6_NETWORK → gateway:8080 직접, cpus ${K6_CPUS:-제한 없음} / memory ${K6_MEMORY:-제한 없음})"
  else
    echo "- k6 실행 위치: 호스트 ($k6_desc, localhost:8080 → Docker Desktop 포트 포워딩)"
  fi
  echo "- kind: $kind"
  echo
  echo "## 실패 사유별 건수"
  jq -r 'if (.failedByReason | length) == 0 then "- 없음" else (.failedByReason | to_entries[] | "- \(.key): \(.value)") end' "$RUN_DIR/order-stats.json"
  echo
  echo "## k6"
  jq -r --arg d "$duration" '.metrics as $m |
    "- 총 요청 수: \($m.http_reqs.count)\n- RPS: \($m.http_reqs.rate * 100 | round / 100)\n- http_req_duration avg(ms): \($m.http_req_duration.avg * 100 | round / 100)\n- http_req_duration p95(ms): \($m.http_req_duration["p(95)"] * 100 | round / 100)\n- http_req_duration p99(ms): \($m.http_req_duration["p(99)"] * 100 | round / 100)\n- http_req_duration max(ms): \($m.http_req_duration.max * 100 | round / 100)\n- http_req_failed 비율: \($m.http_req_failed.value)\n- orders_paid: \($m.orders_paid.count // 0)\n- orders_sold_out: \($m.orders_sold_out.count // 0)\n- orders_other: \($m.orders_other.count // 0)\n- 테스트 소요 시간(초): \($d)"' "$S"
} > "$RUN_DIR/report.md"

row=$(jq -r --arg run "$RUN_ID" --arg st "$strategy" --arg kind "$kind" --argjson u "$USERS" --argjson q "$SALE_QUANTITY" \
  --argjson inv "$inv_stats" --slurpfile v "$RUN_DIR/verify.json" '
  .metrics as $m | $v[0] as $v |
  "| \($run) | \($kind) | \($st) | \($u) | \($q) | \($v.paid) | \($v.oversold) | \($v.undersold) | \($v.finalStock) | \($v.conflicts) | \($v.timeouts.total) | \($inv.retries) | \($v.verdict) | \($m.http_req_duration["p(95)"] * 100 | round / 100) | \($m.http_req_duration["p(99)"] * 100 | round / 100) | \($m.http_reqs.rate * 100 | round / 100) |"' "$S")
echo "$row" >> "$ROOT/docs/results/README.md"

jq -r '.verdict' "$RUN_DIR/verify.json"
