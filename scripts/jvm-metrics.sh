#!/usr/bin/env bash
# 이미 끝난 실행의 Hikari 커넥션 획득 대기와 GC 정지를 Prometheus 에서 다시 계산해 JSON 배열로 stdout 에 낸다.
# 1단계 4차까지의 실행처럼 causes.json 에 hikariAcquire/gcPause 가 없는 실행의 비교 기준값을 만들 때 쓴다.
# 사용: scripts/jvm-metrics.sh docs/results/runs/<RUN_ID>...   (Prometheus 에 그 시각의 데이터가 남아 있어야 한다)
# 구간: run-test.sh 와 같다(k6 시작 2초 전 ~ k6 끝 2초 뒤). k6 시작·끝 시각 대신 report.md 의 "실행 시각"(k6 시작 직전,
#   초 단위)과 "테스트 소요 시간(초)" 을 쓰므로, 초 단위 반올림을 덮으려고 끝을 1초 늘린다(W = 소요 시간 + 5s).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/scripts/lib.sh"

[ "$#" -gt 0 ] || { echo "사용: $0 <run_dir>..." >&2; exit 1; }
for dir in "$@"; do
  report="$dir/report.md"
  [ -f "$report" ] || { echo "없음: $report" >&2; exit 1; }
  started=$(sed -n 's/^- 실행 시각: //p' "$report")
  duration=$(sed -n 's/^- 테스트 소요 시간(초): //p' "$report")
  [ -n "$started" ] && [ -n "$duration" ] || { echo "실행 시각/소요 시간 없음: $report" >&2; exit 1; }
  t_start=$(date -j -u -f '%Y-%m-%dT%H:%M:%SZ' "$started" +%s 2>/dev/null || date -u -d "$started" +%s)
  W="$(( duration + 5 ))s"
  t_eval=$(( t_start + duration + 3 ))
  jq -c -n --arg id "$(basename "$dir")" --arg started "$started" --argjson d "$duration" \
    --argjson ha "$(hikari_acquire_json "$W" "$t_eval")" --argjson gp "$(gc_pause_json "$W" "$t_eval")" \
    '{runId: $id, startedAt: $started, durationSec: $d, hikariAcquire: $ha, gcPause: $gp}'
done | jq -s .
