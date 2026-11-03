#!/usr/bin/env bash
# tools/arm-summary.sh <run_dir>... (저장소 루트에서, Prometheus 에 그 시각 데이터가 있어야 GC 열이 나온다): 실행별 k6 최대·waiting p99, 게이트웨이 넘침·major fault·swap, GC 정지(Prometheus)
set -euo pipefail
source scripts/lib.sh   # 저장소 루트에서 실행
for r in "$@"; do
  s=$(awk '/k6_start/{print $2}' "$r/times.txt"); e=$(awk '/k6_end/{print $2}' "$r/times.txt")
  W="$(( e - s + 4 ))s"; t=$(( e + 2 ))
  gc=$(gc_pause_json "$W" "$t" | jq -r '.gateway | to_entries | map("\(.key|.[0:4]) \(.value.count)회 max \(.value.max1sMs|round)ms") | join(", ")')
  k=$(jq -r '.metrics | "max \(.http_req_duration.max/1000*10|round/10)s p99wait \(.http_req_waiting["p(99)"]/1000*10|round/10)s connMax \(.http_req_connecting.max/1000*10|round/10)s"' "$r/k6-summary.json")
  g=$(awk 'NF==9{if(!f){o=$2;m=$5;f=1}; lo=$2; lm=$5; if($7>sw)sw=$7} END{printf "ovf %d majf %d swapMax %dMiB", lo-o, lm-m, sw/1048576}' "$r/gw.txt")
  ord=$(jq -r '"paid+failed \(.paid + .failed)"' "$r/order-stats.json")
  echo "$(basename "$r") | $k | $g | gw GC: $gc | $ord"
done
