#!/usr/bin/env bash
# usage: verify.sh <event_id> <product_id> <sale_quantity> <users> <k6_summary_json>  -> JSON on stdout. 항상 exit 0.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

event_id="$1"; product_id="$2"; sale_quantity="$3"; users="$4"; k6_summary="$5"

stats=$(order_stats "$event_id")
inv=$(curl -fsS --max-time 10 "$INVENTORY_URL/internal/inventory/$product_id")

obj=$(jq -n --argjson s "$stats" --argjson i "$inv" --argjson q "$sale_quantity" --argjson u "$users" \
  --slurpfile k "$k6_summary" '
  ($k[0].metrics // {}) as $m
  | def c($n): ($m[$n].count // $m[$n].values.count // 0);
  ($s.paid) as $paid
  | ($i.quantity) as $stock
  | ($s.failedByReason // {}) as $fr
  | ($s.preOrderFailedByReason // {}) as $pr
  | {saleQuantity: $q, users: $u,
     paid: $paid, failed: $s.failed, pending: $s.pending,
     preOrderFailed: ($pr | add // 0),
     finalStock: $stock,
     oversold: ($paid - $q),
     undersold: ([$q - $paid, 0] | max),
     stockMismatch: (($q - $stock) - $paid),
     conflicts: ($fr.STOCK_CONFLICT // 0),
     failedByReason: $fr,
     preOrderFailedByReason: $pr}
  | .timeouts = {
      inventory: (($fr.INVENTORY_TIMEOUT // 0) + ($pr.INVENTORY_TIMEOUT // 0)),
      payment: (($fr.PAYMENT_TIMEOUT // 0) + ($pr.PAYMENT_TIMEOUT // 0)),
      product: (($fr.PRODUCT_TIMEOUT // 0) + ($pr.PRODUCT_TIMEOUT // 0)),
      orderDb: (($fr.ORDER_DB_TIMEOUT // 0) + ($pr.ORDER_DB_TIMEOUT // 0)),
      gateway: c("orders_gateway_timeout"),
      client: c("orders_client_timeout")}
  | .timeouts.total = (.timeouts | add)
  | .k6 = {paid: c("orders_paid"), soldOut: c("orders_sold_out"), conflict: c("orders_conflict"),
           orderTimeout: c("orders_order_timeout"), gatewayTimeout: c("orders_gateway_timeout"),
           clientTimeout: c("orders_client_timeout"), other: c("orders_other")}
  | .k6.total = (.k6 | add)')
verdict=$(jq -f "$(dirname "$0")/verdict.jq" <<<"$obj")
jq --argjson v "$verdict" '. + $v' <<<"$obj"
