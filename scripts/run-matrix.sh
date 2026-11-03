#!/usr/bin/env bash
# 구성별로 inventory-service 를 교체하며 (워밍업 1 + 측정 RUNS) 회 실행한다.
# 사용: scripts/run-matrix.sh [config...]   기본: naive pessimistic conditional optimistic-r0..r3
# env: RUNS(3) USERS SALE_QUANTITY ARRIVAL_SECONDS (run-test.sh 로 전달). 실행 중 실패하면 즉시 중단한다.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/scripts/lib.sh"
cd "$ROOT"

RUNS="${RUNS:-3}"
if [ "$#" -eq 0 ]; then
  set -- naive pessimistic conditional optimistic-r0 optimistic-r1 optimistic-r2 optimistic-r3
fi

# inventory-service 만 다시 만든다(Prometheus/Grafana 는 건드리지 않는다).
switch_to() {
  local config="$1" strategy="$1" retries=0
  case "$config" in
    optimistic-r[0-3]) strategy=optimistic; retries="${config#optimistic-r}" ;;
    naive|pessimistic|conditional) ;;
    *) echo "알 수 없는 구성: $config" >&2; return 1 ;;
  esac
  INVENTORY_STRATEGY="$strategy" INVENTORY_OPTIMISTIC_MAX_RETRIES="$retries" \
    docker compose up -d inventory-service
}

# 전환 직후의 일시적 실패를 견디기 위해 스모크를 최대 3회 시도한다(측정 데이터가 아닌 준비 확인용).
# env: SMOKE_CMD(기본 scripts/smoke.sh) SMOKE_INTERVAL(기본 5초, 시도 사이 대기)
smoke_with_retry() {
  local smoke_cmd="${SMOKE_CMD:-$ROOT/scripts/smoke.sh}" n err
  for n in 1 2 3; do
    if err=$("$smoke_cmd" 2>&1 >/dev/null); then
      return 0
    fi
    echo "smoke attempt $n/3 failed: $err" >&2
    [ "$n" -lt 3 ] && sleep "${SMOKE_INTERVAL:-5}"
  done
  return 1
}

restore_naive() {
  echo "== inventory-service 를 naive 로 되돌린다"
  switch_to naive && wait_healthy
}
trap 'rc=$?; if [ $rc -ne 0 ]; then echo "중단됨(exit $rc)" >&2; restore_naive || true; fi' EXIT

summary=()
for config in "$@"; do
  echo "== [$config] 전환"
  switch_to "$config"
  wait_healthy
  actual=$(current_strategy)
  if [ "$actual" != "$config" ]; then
    echo "전략 불일치: 기대 $config, 실제 $actual" >&2
    exit 1
  fi
  smoke_with_retry   # order-service 의 예전 연결 정리

  echo "== [$config] 워밍업"
  WARMUP=1 "$ROOT/scripts/run-test.sh" | tail -1 >/dev/null
  for i in $(seq 1 "$RUNS"); do
    echo "== [$config] 측정 $i/$RUNS"
    verdict=$("$ROOT/scripts/run-test.sh" | tee /dev/stderr | tail -1)
    summary+=("$config #$i: $verdict")
  done
done

echo
echo "== 판정 목록"
printf '%s\n' "${summary[@]}"
trap - EXIT
restore_naive
