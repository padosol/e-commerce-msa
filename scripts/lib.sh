#!/usr/bin/env bash
# source 해서 쓰는 헬퍼. 실패 시 비-0 을 반환한다.

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
USER_URL="${USER_URL:-http://localhost:8081}"
PRODUCT_URL="${PRODUCT_URL:-http://localhost:8082}"
INVENTORY_URL="${INVENTORY_URL:-http://localhost:8083}"
ORDER_URL="${ORDER_URL:-http://localhost:8084}"
PAYMENT_URL="${PAYMENT_URL:-http://localhost:8085}"

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

# pending 주문이 0 이 될 때까지 대기(기본 60초). 시간 초과 시 그대로 진행한다(verdict 가 INCONCLUSIVE 가 된다).
wait_orders_settled() {
  local event_id="$1" timeout="${2:-60}" deadline
  deadline=$(( $(date +%s) + timeout ))
  until [ "$(order_stats "$event_id" | jq -r '.pending')" = "0" ]; do
    if [ "$(date +%s)" -ge "$deadline" ]; then
      echo "WARN: pending 주문이 남은 채 대기 시간 초과" >&2
      return 0
    fi
    sleep 1
  done
}
