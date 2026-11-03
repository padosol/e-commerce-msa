#!/usr/bin/env bash
# 1단계 측정 결과를 구성별 3회 중앙값 마크다운 표로 stdout 에 출력한다. 결과 파일을 읽기만 한다.
# 사용: scripts/summarize.sh   env: RUNS_DIR(기본 docs/results/runs) FROM(기본 없음: RUN_ID 가 이 값 이상인 실행만, 예 FROM=20261104)
#
# 대상: runs/<RUN_ID>/ 중 meta.json 이 있고 kind == "measure", users == 5000, saleQuantity == 100 인 실행.
#   meta.json 이 없는 0단계 기준선과 워밍업(kind == "warmup")은 빠진다.
#   대상 실행에는 verify.json, causes.json, k6-summary.json 이 모두 있어야 한다(없으면 실패).
# 선택: 구성마다 RUN_ID 순(= 시간 순)으로 verdict 가 INCONCLUSIVE 가 아닌 실행을 앞에서부터 3개 쓴다.
#   쓴 실행의 RUN_ID(verify.json 의 inconclusive 가 비어 있지 않으면 `†`)와, 쓰지 않은 뒤 실행의 RUN_ID·verdict, 빼놓은 INCONCLUSIVE 측정 실행(RUN_ID, strategy, k6.other)을 표 아래에 적는다.
# 중앙값: 실행별 값의 가운데 값. Prometheus 에서 온 값(1s 표본 최댓값, p95)이 "n/a" 인 실행이 섞이면
#   숫자인 실행만으로 중앙값을 내고(짝수 개면 가운데 두 값의 평균) 값 뒤에 `*` 를 붙인다.
#   숫자가 하나도 없으면 `n/a` 를 쓴다. 소수는 둘째 자리까지 반올림한다.
# 구성 순서: naive, pessimistic, conditional, optimistic-r0, optimistic-r1, optimistic-r2, optimistic-r3
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUNS_DIR="${RUNS_DIR:-$ROOT/docs/results/runs}"
FROM="${FROM:-}"
CONFIGS='["naive","pessimistic","conditional","optimistic-r0","optimistic-r1","optimistic-r2","optimistic-r3"]'
PER_CONFIG=3

# 대상 실행마다 {meta, verify, causes, k6} 를 한 줄 JSON 으로 낸다.
# $(...) 안에서는 bash 가 set -e 를 끄므로 실패는 exit 로 직접 알린다.
collect() {
  local meta dir f target
  find "$RUNS_DIR" -mindepth 2 -maxdepth 2 -name meta.json | LC_ALL=C sort | while IFS= read -r meta; do
    target="$(jq '.kind == "measure" and .users == 5000 and .saleQuantity == 100' "$meta")" \
      || { echo "읽을 수 없음: $meta" >&2; exit 1; }
    [ "$target" = true ] || continue
    dir="$(dirname "$meta")"
    if [ -n "$FROM" ] && [[ "$(basename "$dir")" < "$FROM" ]]; then continue; fi
    for f in verify.json causes.json k6-summary.json; do
      [ -f "$dir/$f" ] || { echo "없음: $dir/$f" >&2; exit 1; }
    done
    jq -c -n --slurpfile m "$meta" --slurpfile v "$dir/verify.json" \
      --slurpfile c "$dir/causes.json" --slurpfile k "$dir/k6-summary.json" \
      '{meta: $m[0], verify: $v[0], causes: $c[0], k6: $k[0].metrics}' \
      || { echo "읽을 수 없음: $dir" >&2; exit 1; }
  done
}

runs="$(collect)"
jq -r -s --argjson configs "$CONFIGS" --argjson want "$PER_CONFIG" '
  def median: sort | length as $n
    | if $n % 2 == 1 then .[($n - 1) / 2 | floor] else (.[$n / 2 - 1] + .[$n / 2]) / 2 end;
  def fmt: if . == floor then floor | tostring else (. * 100 | round) / 100 | tostring end;
  # 입력: 실행별 값 배열(숫자 또는 "n/a")
  def nums: map(select(type == "number"));
  def mark($all): if (. | length) < ($all | length) then "*" else "" end;
  def cell: . as $all | nums
    | if length == 0 then "n/a" else (median | fmt) + mark($all) end;
  def span: . as $all | nums
    | if length == 0 then "n/a" else "\(min | fmt)~\(max | fmt)" + mark($all) end;
  def row: "| " + join(" | ") + " |";
  # gcPause.<svc> 의 GC 별 값 합/최댓값. 기록이 없는 실행(2단계 이전)은 "n/a"
  def gcsum($svc): (.causes.gcPause[$svc] // null) | if . == null then "n/a" else ([.[].totalMs] | add // 0) end;
  def gcmax($svc): (.causes.gcPause[$svc] // null) | if . == null then "n/a" else ([.[].max1sMs] | max // 0) end;

  sort_by(.meta.runId) as $all
  | [$configs[] as $c
      | [$all[] | select(.meta.strategy == $c and .verify.verdict != "INCONCLUSIVE")] as $ok
      | {config: $c, used: $ok[:$want], spare: $ok[$want:]}] as $groups
  | [$all[] | select(.verify.verdict == "INCONCLUSIVE")] as $excluded
  | def col(f): [.used[] | f];

  ( ["구성", "판정(3회)", "paid", "undersold", "conflicts", "timeouts", "retries",
     "p95(ms)", "p99(ms)", "rps", "inventory 차감 서버 p95(ms)", "MySQL 락 대기 횟수", "평균 락 대기(ms)",
     "inventory Hikari pending 최대", "inventory Tomcat busy 최대", "결제 호출 수"] | row ),
  ( [range(16) | "---"] | row ),
  ( $groups[]
    | [ .config,
        (if (.used | length) == 0 then "n/a" else col(.verify.verdict) | join("/") end),
        (col(.verify.paid) | cell),
        (col(.verify.undersold) | cell),
        (col(.verify.conflicts) | cell),
        (col(.verify.timeouts.total) | cell),
        (col(.causes.optimistic.retries) | cell),
        (col(.k6.http_req_duration["p(95)"]) | cell),
        (col(.k6.http_req_duration["p(99)"]) | cell),
        (col(.k6.http_reqs.rate) | cell),
        (col(.causes.inventoryDecreaseP95Ms) | cell),
        (col(.causes.mysql.lockWaits) | cell),
        (col(.causes.mysql.avgLockWaitMs) | cell),
        (col(.causes.hikariPendingMax.inventory) | cell),
        (col(.causes.tomcatBusyMax.inventory) | cell),
        (col(.causes.paymentCalls) | cell) ]
    | row ),
  "",
  "`*`: 일부 실행의 값이 `n/a`(Prometheus 1s 표본 없음, 또는 2단계 이전이라 기록 없음)라 숫자인 실행만으로 계산한 값. Hikari·Tomcat 최댓값과 서버 p95 는 1s 표본 값이다.",
  "",
  "보조 표 (같은 실행): 끝단 값의 3회 범위(최소~최대)와 앞단 서비스 포화 지표의 중앙값",
  "",
  ( ["구성", "p95 범위(ms)", "rps 범위", "order Hikari pending 최대", "order Tomcat busy 최대", "gateway Tomcat busy 최대",
     "order 커넥션 획득 평균 대기(ms)", "order GC 정지 합(ms)", "order GC 1s 최대(ms)", "gateway GC 정지 합(ms)"] | row ),
  ( [range(10) | "---"] | row ),
  ( $groups[]
    | [ .config,
        (col(.k6.http_req_duration["p(95)"]) | span),
        (col(.k6.http_reqs.rate) | span),
        (col(.causes.hikariPendingMax.order) | cell),
        (col(.causes.tomcatBusyMax.order) | cell),
        (col(.causes.tomcatBusyMax.gateway) | cell),
        (col(.causes.hikariAcquire.order.avgMs // "n/a") | cell),
        (col(gcsum("order")) | cell),
        (col(gcmax("order")) | cell),
        (col(gcsum("gateway")) | cell) ]
    | row ),
  "",
  "중앙값에 쓴 실행 (RUN_ID 순, INCONCLUSIVE 제외 앞 \($want)회):",
  "",
  ( $groups[]
    | "- \(.config): "
      + (if (.used | length) == 0 then "없음" else [.used[] | .meta.runId + (if (.verify.inconclusive // [] | length) > 0 then "†" else "" end)] | join(", ") end)
      + (if (.used | length) < $want then " (판정 가능한 실행이 \(.used | length)회뿐)" else "" end)
      + (if (.spare | length) > 0 then " (쓰지 않은 뒤 실행: \([.spare[] | "\(.meta.runId) \(.verify.verdict)"] | join(", ")))" else "" end) ),
  ( if ([$groups[].used[] | select((.verify.inconclusive // [] | length) > 0)] | length) > 0
    then "", "`†`: 판정은 위와 같지만 `verify.json` 의 `inconclusive` 에 실패한 검사가 남아 있는 실행(예: OVERSOLD 와 함께 기록된 검사). 중앙값에 섞이기 전에 확인할 것."
    else empty end ),
  ( [$all[] | select(.meta.strategy != "naive" and .verify.verdict == "OVERSOLD")] as $bad
    | if ($bad | length) > 0 then "", "!!! 경고: naive 가 아닌 측정 실행에서 OVERSOLD 발생: \([$bad[].meta.runId] | join(", "))" else empty end ),
  "",
  "제외한 INCONCLUSIVE 측정 실행:",
  "",
  ( if ($excluded | length) == 0 then "- 없음"
    else
      ( ["run", "strategy", "k6.other", "inconclusive"] | row ),
      ( [range(4) | "---"] | row ),
      ( $excluded[] | [.meta.runId, .meta.strategy, (.verify.k6.other | tostring), (.verify.inconclusive | join(", "))] | row )
    end )
' <<<"$runs"
