#!/usr/bin/env bash
# source 해서 쓰는 헬퍼. 실패 시 비-0 을 반환한다.

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
USER_URL="${USER_URL:-http://localhost:8081}"
PRODUCT_URL="${PRODUCT_URL:-http://localhost:8082}"
INVENTORY_URL="${INVENTORY_URL:-http://localhost:8083}"
ORDER_URL="${ORDER_URL:-http://localhost:8084}"
PAYMENT_URL="${PAYMENT_URL:-http://localhost:8085}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:${PROMETHEUS_PORT:-9090}}"

wait_healthy() {
  local timeout="${1:-180}" deadline url
  deadline=$(( $(date +%s) + timeout ))
  for url in "$GATEWAY_URL" "$USER_URL" "$PRODUCT_URL" "$INVENTORY_URL" "$ORDER_URL" "$PAYMENT_URL"; do
    until curl -fsS "$url/actuator/health" 2>/dev/null | jq -e '.status == "UP"' >/dev/null 2>&1; do
      if [ "$(date +%s)" -ge "$deadline" ]; then
        echo "timeout waiting for $url" >&2
        return 1
      fi
      sleep 2
    done
  done
}

reset_data() {
  curl -fsS -X DELETE "$ORDER_URL/internal/orders" >/dev/null || return 1
  curl -fsS -X DELETE "$PAYMENT_URL/internal/payments" >/dev/null || return 1
  curl -fsS -X DELETE "$INVENTORY_URL/internal/inventory/stats" >/dev/null || return 1
}

# stdout: "<eventId> <productId>"
create_event() {
  local qty="$1" open_at epoch
  epoch=$(date +%s)
  open_at=$(date -u -r $(( epoch - 60 )) +%Y-%m-%dT%H:%M:%SZ)
  curl -fsS -X POST "$PRODUCT_URL/internal/events" \
    -H 'Content-Type: application/json' \
    -d "{\"productName\":\"flash-${epoch}\",\"price\":10000,\"openAt\":\"${open_at}\",\"saleQuantity\":${qty}}" \
    | jq -r '"\(.eventId) \(.productId)"'
}

set_stock() {
  curl -fsS -X PUT "$INVENTORY_URL/internal/inventory/$1" \
    -H 'Content-Type: application/json' \
    -d "{\"quantity\":$2}" >/dev/null
}

issue_tokens() {
  curl -fsS -X POST "$USER_URL/internal/test-users" \
    -H 'Content-Type: application/json' \
    -d "{\"count\":$1}" -o "$2"
}

current_strategy() {
  curl -fsS "$INVENTORY_URL/internal/strategy" | jq -r '.strategy'
}

payment_config() {
  curl -fsS "$PAYMENT_URL/internal/payments/config"
}

order_stats() {
  curl -fsS "$ORDER_URL/internal/orders/stats?eventId=$1"
}

# {"conflicts":n,"retries":n,"exhausted":n}. 요청이 진행 중이 아닐 때만 정확하다.
inventory_stats() {
  curl -fsS "$INVENTORY_URL/internal/inventory/stats"
}

# {"waits":n,"timeMs":n} (누적값; 실행 전후 차이를 쓴다). 조회 실패나 빈 결과면 두 값 모두 "n/a" (0 으로 기록하지 않는다).
mysql_lock_status() {
  local out
  out=$(docker compose exec -T mysql mysql -uroot -proot -N -e \
    "SHOW GLOBAL STATUS WHERE Variable_name IN ('Innodb_row_lock_waits','Innodb_row_lock_time')" 2>/dev/null) || out=""
  printf '%s\n' "$out" | awk '$1=="Innodb_row_lock_waits"{w=$2} $1=="Innodb_row_lock_time"{t=$2}
    END{ if (w ~ /^[0-9]+$/ && t ~ /^[0-9]+$/) printf "{\"waits\":%d,\"timeMs\":%d}\n", w, t;
         else print "{\"waits\":\"n/a\",\"timeMs\":\"n/a\"}" }'
}

# {"isolation":"...","lockWaitTimeout":n}
mysql_settings() {
  docker compose exec -T mysql mysql -uroot -proot -N -e \
    "SELECT @@transaction_isolation, @@innodb_lock_wait_timeout" 2>/dev/null \
    | awk '{printf "{\"isolation\":\"%s\",\"lockWaitTimeout\":%d}\n", $1, $2}'
}

# prom_query <promql> <unix_time> : 첫 결과 값, 없거나 NaN 이면 n/a
prom_query() {
  curl -fsS --max-time 10 "$PROMETHEUS_URL/api/v1/query" \
    --data-urlencode "query=$1" --data-urlencode "time=$2" 2>/dev/null \
    | jq -r '.data.result[0].value[1] // "n/a" | if . == "NaN" or . == "+Inf" or . == "-Inf" then "n/a" else . end' 2>/dev/null \
    || echo "n/a"
}

# pending == 0 이고 paid + failed + preOrderFailed == users 가 될 때까지 대기(기본 60초).
# 시간 초과 시 그대로 진행한다(verdict 가 INCONCLUSIVE 가 된다).
wait_orders_settled() {
  local event_id="$1" users="$2" timeout="${3:-60}" deadline
  deadline=$(( $(date +%s) + timeout ))
  until order_stats "$event_id" | jq -e --argjson u "$users" \
      '.pending == 0 and (.paid + .failed + ((.preOrderFailedByReason // {}) | add // 0)) == $u' >/dev/null; do
    if [ "$(date +%s)" -ge "$deadline" ]; then
      echo "WARN: 주문이 다 정리되지 않은 채 대기 시간 초과" >&2
      return 0
    fi
    sleep 1
  done
}

# 호스트의 TIME_WAIT 소켓 수. macOS 는 `netstat -an -p tcp`, 지원하지 않으면(Linux) `netstat -an`.
# 상태 열(마지막 열)이 정확히 TIME_WAIT 인 줄만 센다.
tcp_time_wait_count() {
  { netstat -an -p tcp 2>/dev/null || netstat -an 2>/dev/null; } | awk '$NF == "TIME_WAIT" {n++} END {print n + 0}'
}

# wait_tcp_drained [threshold=500] [timeout=120]
# 호스트 TIME_WAIT 소켓이 threshold 미만이 될 때까지 1초마다 확인한다(부하 생성기 pacing; 대상 시스템 설정과 무관).
# stdout: "<기다린 초> <마지막 개수>". 시간 초과여도 0 을 반환하고 stderr 에 WARN 을 남긴다.
wait_tcp_drained() {
  local threshold="${1:-500}" timeout="${2:-120}" start count waited
  start=$(date +%s)
  while :; do
    count=$(tcp_time_wait_count)
    waited=$(( $(date +%s) - start ))
    if [ "$count" -lt "$threshold" ]; then
      echo "$waited $count"
      return 0
    fi
    if [ "$waited" -ge "$timeout" ]; then
      echo "WARN: TIME_WAIT 소켓이 ${timeout}초 안에 ${threshold}개 미만으로 줄지 않았다(현재 ${count}개)" >&2
      echo "$waited $count"
      return 0
    fi
    sleep 1
  done
}

# prom_vector <promql> <unix_time> : 결과 벡터를 [{"m": {라벨}, "v": 숫자}] JSON 으로. NaN/Inf 는 빼고, 실패하면 []
prom_vector() {
  local out
  out=$(curl -fsS --max-time 10 "$PROMETHEUS_URL/api/v1/query" \
      --data-urlencode "query=$1" --data-urlencode "time=$2" 2>/dev/null \
    | jq -c '[.data.result[] | {m: .metric, v: (.value[1] | tonumber? // null)}
              | select(.v != null and (.v | isnan | not) and (.v | isinfinite | not))]' 2>/dev/null) || out=""
  [ -n "$out" ] && echo "$out" || echo "[]"
}

# counter_delta <counter> <by-labels> <W> : 평가 시각 기준 W 전과의 카운터 차이(외삽 없음).
# 스크레이프가 실패하면(예: GC 정지 중) Prometheus 가 시계열을 stale 로 표시하므로, 두 시점 모두 최근 2분 안의 마지막 표본을 쓴다.
# 2분 안에 표본이 없던 시계열(구간 안에서 처음 생긴 시계열, 예: 첫 Full GC)은 W 전 값을 0 으로 본다.
counter_delta() {
  echo "sum by ($2) (last_over_time($1[2m])) - (sum by ($2) (last_over_time($1[2m] offset $3)) or sum by ($2) (last_over_time($1[2m])) * 0)"
}

APP_SERVICES=(gateway user-service product-service inventory-service order-service payment-service)
svc_key() { [ "$1" = gateway ] && echo gateway || echo "${1%-service}"; }

# hikari_acquire_json <W> <unix_time> : 구간 W 동안 서비스별 Hikari 커넥션 획득 횟수와 평균 획득 대기(ms).
# {"order": {"acquisitions": n, "avgMs": x}, ...}. 획득 대기 최댓값은 Micrometer 가 감쇠 최댓값(실측: 60초마다 갱신, 한 값이 120~180초 남음)으로만 내보내
# 실행(약 10초 간격) 단위로 나눌 수 없어서 여기에 넣지 않는다.
hikari_acquire_json() {
  local cnt sum
  cnt=$(prom_vector "$(counter_delta hikaricp_connections_acquire_seconds_count job "$1")" "$2")
  sum=$(prom_vector "$(counter_delta hikaricp_connections_acquire_seconds_sum job "$1")" "$2")
  jq -c -n --argjson c "$cnt" --argjson s "$sum" '
    ($s | map({key: .m.job, value: .v}) | from_entries) as $sm
    | $c | map({key: (if .m.job == "gateway" then "gateway" else .m.job | sub("-service$"; "") end),
                value: {acquisitions: (.v | round),
                        avgMs: (if .v > 0 then (($sm[.m.job] // 0) / .v * 1000 * 100 | round / 100) else 0 end)}})
    | from_entries'
}

# gc_pause_json <W> <unix_time> : 구간 W 동안 서비스별·GC별 정지 횟수, 정지 시간 합(ms),
# 1초 표본 하나에 들어온 정지 시간 합의 최댓값(max1sMs, 정지가 한 번이면 그 정지 시간). 정지가 없던 GC 는 뺀다.
# {"order": {"MarkSweepCompact": {"count": 1, "totalMs": 3269.0, "max1sMs": 3269.0}}, "gateway": {}, ...}
gc_pause_json() {
  local W="$1" t="$2" cnt sum mx
  cnt=$(prom_vector "$(counter_delta jvm_gc_pause_seconds_count 'job, gc' "$W")" "$t")
  sum=$(prom_vector "$(counter_delta jvm_gc_pause_seconds_sum 'job, gc' "$W")" "$t")
  mx=$(prom_vector "max_over_time(($(counter_delta jvm_gc_pause_seconds_sum 'job, gc' 1s))[$W:1s])" "$t")
  jq -c -n --argjson c "$cnt" --argjson s "$sum" --argjson x "$mx" --argjson svcs "$(printf '%s\n' "${APP_SERVICES[@]}" | jq -R . | jq -s .)" '
    def key: if . == "gateway" then "gateway" else sub("-service$"; "") end;
    def ms: . * 1000 * 10 | round / 10;
    def at($v; $j; $g): ([$v[] | select(.m.job == $j and .m.gc == $g) | .v] | first) // 0;
    [$svcs[] as $j
      | {key: ($j | key),
         value: ([$c[] | select(.m.job == $j and .v > 0.5) | .m.gc] | unique
                 | map({key: ., value: {count: (at($c; $j; .) | round), totalMs: (at($s; $j; .) | ms), max1sMs: (at($x; $j; .) | ms)}})
                 | from_entries)}]
    | from_entries'
}

# jvm_settings <unix_time> : 실행 중인 앱 컨테이너의 실제 자원 한도와 JVM 설정.
# GC 는 Prometheus 의 heap 메모리 풀 이름으로 판별한다(G1 ... → G1, Tenured Gen → Serial, PS ... → Parallel). 모르면 "n/a".
# {"order-service": {"cpus": 2, "memoryMiB": 1024, "javaToolOptions": "...", "gc": "G1"}, ...}
jvm_settings() {
  local pools out="{}" s cid lim jto
  pools=$(prom_vector 'count by (job, id) (jvm_memory_max_bytes{area="heap"})' "$1")
  for s in "${APP_SERVICES[@]}"; do
    cid=$(docker compose ps -q "$s" 2>/dev/null) || cid=""
    lim="null null"; jto=""
    if [ -n "$cid" ]; then
      lim=$(docker inspect -f '{{.HostConfig.NanoCpus}} {{.HostConfig.Memory}}' "$cid" 2>/dev/null) || lim="null null"
      jto=$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$cid" 2>/dev/null | sed -n 's/^JAVA_TOOL_OPTIONS=//p') || jto=""
    fi
    out=$(jq -c --arg s "$s" --arg lim "$lim" --arg jto "$jto" --argjson pools "$pools" '
      ($lim | split(" ") | map(tonumber? // null)) as [$cpu, $mem]
      | [$pools[] | select(.m.job == $s) | .m.id] as $ids
      | . + {($s): {cpus: (if $cpu != null and $cpu > 0 then $cpu / 1e9 else "n/a" end),
                     memoryMiB: (if $mem != null and $mem > 0 then $mem / 1048576 | round else "n/a" end),
                     javaToolOptions: (if $jto == "" then "n/a" else $jto end),
                     gc: (if any($ids[]; startswith("G1 ")) then "G1"
                          elif any($ids[]; . == "Tenured Gen") then "Serial"
                          elif any($ids[]; startswith("PS ")) then "Parallel"
                          elif ($ids | length) > 0 then ($ids | join(",")) else "n/a" end)}}' <<<"$out")
  done
  echo "$out"
}
