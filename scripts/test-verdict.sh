#!/usr/bin/env bash
# verdict.jq 단위 테스트. 서비스 없이 bash + jq 만 필요.
set -uo pipefail
cd "$(dirname "$0")/.."

pass=0; fail=0

# base: users 10, saleQuantity 5. $2 는 덮어쓸 필드(jq 객체 리터럴).
mk() {
  jq -n --argjson o "$1" '
    {saleQuantity: 5, users: 10, paid: 0, failed: 0, pending: 0, preOrderFailed: 0,
     failedByReason: {}, preOrderFailedByReason: {},
     k6: {paid: 0, soldOut: 0, conflict: 0, orderTimeout: 0, gatewayTimeout: 0, clientTimeout: 0, other: 0, total: 10}}
    | . * $o'
}

t() { # name input expectedVerdict expectedInconclusiveJson
  local out v i
  out=$(jq -c -f scripts/verdict.jq <<<"$2") || { echo "FAIL $1: jq error"; fail=$((fail+1)); return; }
  v=$(jq -r '.verdict' <<<"$out"); i=$(jq -c '.inconclusive' <<<"$out")
  if [ "$v" = "$3" ] && [ "$i" = "$4" ]; then pass=$((pass+1))
  else echo "FAIL $1: got $v $i, want $3 $4"; fail=$((fail+1)); fi
}

t ok "$(mk '{"paid":5,"failed":5,"failedByReason":{"SOLD_OUT":5},"k6":{"paid":5,"soldOut":5}}')" OK '[]'
t oversold "$(mk '{"paid":7,"failed":3,"failedByReason":{"SOLD_OUT":3},"k6":{"paid":7,"soldOut":3}}')" OVERSOLD '[]'
t oversoldWinsOverPending "$(mk '{"paid":7,"pending":2,"failed":1,"failedByReason":{"SOLD_OUT":1},"k6":{"paid":7,"soldOut":1}}')" OVERSOLD '[]'
t undersoldByConflict "$(mk '{"paid":3,"failed":7,"failedByReason":{"SOLD_OUT":4,"STOCK_CONFLICT":3},"k6":{"paid":3,"soldOut":4,"conflict":3}}')" UNDERSOLD '[]'
t undersoldByTimeouts "$(mk '{"paid":4,"failed":5,"failedByReason":{"SOLD_OUT":3,"INVENTORY_TIMEOUT":2},"preOrderFailed":1,"preOrderFailedByReason":{"PRODUCT_TIMEOUT":1},"k6":{"paid":4,"soldOut":3,"orderTimeout":3}}')" UNDERSOLD '[]'
t gatewayTimeoutExplainsPaidGap "$(mk '{"paid":5,"failed":5,"failedByReason":{"SOLD_OUT":5},"k6":{"paid":4,"soldOut":5,"gatewayTimeout":1}}')" OK '[]'
t pendingIsInconclusive "$(mk '{"paid":4,"pending":1,"failed":5,"failedByReason":{"SOLD_OUT":5},"k6":{"paid":4,"soldOut":5,"gatewayTimeout":1}}')" INCONCLUSIVE '["pending"]'
t nonCharacteristicReason "$(mk '{"paid":5,"failed":5,"failedByReason":{"SOLD_OUT":4,"INVENTORY_ERROR":1},"k6":{"paid":5,"soldOut":4,"other":1}}')" INCONCLUSIVE '["non-characteristic-reason","k6-unclassified"]'
t orderTotalMismatch "$(mk '{"paid":5,"failed":4,"failedByReason":{"SOLD_OUT":4},"k6":{"paid":5,"soldOut":5,"total":10}}')" INCONCLUSIVE '["order-total"]'
t k6PaidExceedsDb "$(mk '{"paid":5,"failed":5,"failedByReason":{"SOLD_OUT":5},"k6":{"paid":6,"soldOut":4}}')" INCONCLUSIVE '["k6-db-paid"]'

if [ "$fail" -ne 0 ]; then echo "verdict tests: $fail failed, $pass passed"; exit 1; fi
echo "verdict tests: $pass passed"
