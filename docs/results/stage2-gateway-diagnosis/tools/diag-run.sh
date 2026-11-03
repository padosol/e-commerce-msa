#!/usr/bin/env bash
# 진단용 실행 1회(docs/results 에 기록하지 않음): tools/diag-run.sh <out_dir>. 저장소 루트에서 실행.
# 게이트웨이 카운터는 cgroup 밖의 특권 컨테이너(sampler.sh)가 0.25초마다 읽는다. k6 는 요청별 값을 csv 로 남긴다.
set -euo pipefail
OUT="$1"; mkdir -p "$OUT"
source scripts/lib.sh
reset_data
read -r EVENT_ID PRODUCT_ID <<<"$(create_event 100)"
set_stock "$PRODUCT_ID" 100
issue_tokens 5000 load-test/k6/.data/tokens.json
P=$(docker inspect -f '{{.State.Pid}}' e-commerce-gateway-1)
docker run --rm --privileged --pid=host --cgroupns=host -e P="$P" -v "$(dirname "$0")/sampler.sh:/s.sh:ro" --name diag-sampler alpine:3 sh /s.sh > "$OUT/gw.txt" 2>&1 &
sleep 1
echo "k6_start_epoch $(date +%s)" > "$OUT/times.txt"
docker run --rm --network e-commerce_default --ulimit nofile=65536:65536 --cpus 4 --memory 2g \
  -v "$PWD/load-test/k6:/k6:ro" -v "$OUT:/out" grafana/k6:2.2.0 run --quiet --summary-export /out/k6-summary.json \
  --out csv=/out/req.csv.gz -e BASE_URL=http://gateway:8080 -e EVENT_ID="$EVENT_ID" -e USERS=5000 -e ARRIVAL_SECONDS=0 \
  -e TOKENS_FILE=/k6/.data/tokens.json /k6/flash-sale.js > "$OUT/k6.txt" 2>&1 || true
echo "k6_end_epoch $(date +%s)" >> "$OUT/times.txt"
sleep 2
docker rm -f diag-sampler >/dev/null 2>&1 || true
wait_orders_settled "$EVENT_ID" 5000 60
order_stats "$EVENT_ID" > "$OUT/order-stats.json"
jq -r '.metrics | "dur \(.http_req_duration.max|round)ms waitP99 \(.http_req_waiting["p(99)"]|round) connMax \(.http_req_connecting.max|round)"' "$OUT/k6-summary.json"
