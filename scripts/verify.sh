#!/usr/bin/env bash
# usage: verify.sh <event_id> <product_id> <sale_quantity>  -> JSON on stdout. 항상 exit 0.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

event_id="$1"; product_id="$2"; sale_quantity="$3"

stats=$(order_stats "$event_id")
inv=$(curl -fsS --max-time 10 "$INVENTORY_URL/internal/inventory/$product_id")

obj=$(jq -n --argjson s "$stats" --argjson i "$inv" --argjson q "$sale_quantity" '
  ($s.paid) as $paid
  | ($i.quantity) as $stock
  | {saleQuantity: $q, paid: $paid, failed: $s.failed, pending: $s.pending,
     finalStock: $stock,
     oversold: ($paid - $q),
     stockMismatch: (($q - $stock) - $paid)}')
verdict=$(jq -f "$(dirname "$0")/verdict.jq" <<<"$obj")
jq --argjson v "$verdict" '. + {verdict: $v}' <<<"$obj"
