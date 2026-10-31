#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

wait_healthy
reset_data
read -r event_id product_id <<<"$(create_event 1)"
set_stock "$product_id" 1

tokens=$(mktemp)
trap 'rm -f "$tokens"' EXIT
issue_tokens 1 "$tokens"
token=$(jq -r '.[0].accessToken' "$tokens")

body=$(mktemp)
status=$(curl -sS -o "$body" -w '%{http_code}' -X POST "$GATEWAY_URL/api/orders" \
  -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
  -d "{\"eventId\":$event_id}")
order_status=$(jq -r '.status' "$body")
rm -f "$body"
if [ "$status" != "201" ] || [ "$order_status" != "PAID" ]; then
  echo "SMOKE FAIL: http=$status status=$order_status" >&2
  exit 1
fi

paid=$(order_stats "$event_id" | jq -r '.paid')
if [ "$paid" != "1" ]; then
  echo "SMOKE FAIL: paid=$paid" >&2
  exit 1
fi
echo "SMOKE OK"
