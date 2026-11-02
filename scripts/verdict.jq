# 입력: verify.sh 가 만든 객체(verdict/inconclusive 제외) -> {verdict, inconclusive}
# 순서: OVERSOLD(최우선) -> INCONCLUSIVE(실행 자체를 못 믿음) -> UNDERSOLD -> OK
["SOLD_OUT","STOCK_CONFLICT","INVENTORY_TIMEOUT","PAYMENT_TIMEOUT","PRODUCT_TIMEOUT","ORDER_DB_TIMEOUT"] as $characteristic
| ([(.failedByReason // {} | keys[]), (.preOrderFailedByReason // {} | keys[])]
   | map(select(. as $k | $characteristic | index($k) | not))) as $bad
| [ (if .pending > 0 then "pending" else empty end),
    (if (.paid + .failed + .pending + .preOrderFailed) != .users then "order-total" else empty end),
    (if ($bad | length) > 0 then "non-characteristic-reason" else empty end),
    (if .k6.other > 0 then "k6-unclassified" else empty end),
    (if .k6.total != .users then "k6-total" else empty end),
    (if (.k6.paid <= .paid and .paid <= (.k6.paid + .k6.gatewayTimeout + .k6.clientTimeout)) then empty else "k6-db-paid" end)
  ] as $inc
| if .paid > .saleQuantity then {verdict: "OVERSOLD", inconclusive: []}
  elif ($inc | length) > 0 then {verdict: "INCONCLUSIVE", inconclusive: $inc}
  elif .paid < .saleQuantity then {verdict: "UNDERSOLD", inconclusive: []}
  else {verdict: "OK", inconclusive: []} end
