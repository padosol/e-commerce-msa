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

# {"waits":n,"timeMs":n} (누적값; 실행 전후 차이를 쓴다)
mysql_lock_status() {
  docker compose exec -T mysql mysql -uroot -proot -N -e \
    "SHOW GLOBAL STATUS WHERE Variable_name IN ('Innodb_row_lock_waits','Innodb_row_lock_time')" 2>/dev/null \
    | awk '$1=="Innodb_row_lock_waits"{w=$2} $1=="Innodb_row_lock_time"{t=$2} END{printf "{\"waits\":%d,\"timeMs\":%d}\n", w, t}'
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
