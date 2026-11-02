# 1단계 1차 측정 기록 (보관)

1차 측정은 2026-11-02 21:40~22:03 에 돌렸다. naive 외 18회 중 10회가 INCONCLUSIVE(`order-total`, `k6-unclassified`)이며, 판매(paid)는 모두 100이라 초과 판매는 없었다.
원인은 직전 실행이 남긴 클라이언트 소켓 약 5000개가 호스트에 TIME_WAIT 로 남은 채 다음 k6 가 시작된 것이다. 연결 단계에서 실패해 요청이 시스템에 닿지 않았다.
진단 수치: 유휴 후 단독 실행은 오류 0건, TIME_WAIT 5036개에서 시작한 실행은 `i/o timeout` 982건, TIME_WAIT 49개에서 시작한 실행은 0건.
이후 `run-test.sh` 는 k6 시작 전에 TIME_WAIT 가 500개 미만이 될 때까지 기다린다. 이 기록의 실행 결과와 환경 메모는 `runs/`, `stage1-environment.md` 에 있다.

| run | kind | strategy | users | stock | paid | oversold | undersold | finalStock | conflicts | timeouts | retries | verdict | p95(ms) | p99(ms) | rps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 20261102-214045-naive-warmup | warmup | naive | 5000 | 100 | 429 | 329 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 12905.48 | 13190.45 | 372.41 |
| 20261102-214106-naive | measure | naive | 5000 | 100 | 574 | 474 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 7363.68 | 7689.3 | 609.71 |
| 20261102-214219-naive | measure | naive | 5000 | 100 | 602 | 502 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 6108.58 | 6247.46 | 767.12 |
| 20261102-214230-naive | measure | naive | 5000 | 100 | 589 | 489 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 7165.47 | 14878.91 | 234.4 |
| 20261102-214401-pessimistic-warmup | warmup | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | INCONCLUSIVE | 3778.98 | 3881.34 | 638.28 |
| 20261102-214513-pessimistic | measure | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | INCONCLUSIVE | 14958.67 | 21373.19 | 231.61 |
| 20261102-214638-pessimistic | measure | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 4848.66 | 4947.95 | 987.32 |
| 20261102-214647-pessimistic | measure | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | INCONCLUSIVE | 5030.45 | 9221.36 | 520.46 |
| 20261102-214808-conditional-warmup | warmup | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | INCONCLUSIVE | 3638.53 | 3853.63 | 635.77 |
| 20261102-214920-conditional | measure | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | INCONCLUSIVE | 21319.61 | 21451.1 | 231.38 |
| 20261102-215046-conditional | measure | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | INCONCLUSIVE | 3482.5 | 3761.61 | 635.16 |
| 20261102-215159-conditional | measure | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 5788.45 | 5912.47 | 815.42 |
| 20261102-215215-optimistic-r0-warmup | warmup | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 678 | 0 | 0 | OK | 7621.36 | 7766.44 | 631.99 |
| 20261102-215227-optimistic-r0 | measure | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 516 | 0 | 0 | OK | 5508.29 | 9223.03 | 509.11 |
| 20261102-215241-optimistic-r0 | measure | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 534 | 0 | 0 | INCONCLUSIVE | 3845.58 | 4035.8 | 634.28 |
| 20261102-215353-optimistic-r0 | measure | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 480 | 0 | 0 | OK | 5685.97 | 5788.29 | 824.15 |
| 20261102-215409-optimistic-r1-warmup | warmup | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 358 | 0 | 432 | OK | 4823.9 | 7012.4 | 533.82 |
| 20261102-215423-optimistic-r1 | measure | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 385 | 0 | 448 | OK | 5549.44 | 5661.99 | 847.56 |
| 20261102-215433-optimistic-r1 | measure | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 383 | 0 | 441 | OK | 7324.9 | 9373.94 | 523.79 |
| 20261102-215447-optimistic-r1 | measure | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 371 | 0 | 435 | INCONCLUSIVE | 216.5 | 1830.21 | 637.6 |
| 20261102-215605-optimistic-r2-warmup | warmup | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 223 | 0 | 574 | INCONCLUSIVE | 7282.6 | 21280.01 | 232.61 |
| 20261102-215731-optimistic-r2 | measure | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 252 | 0 | 627 | OK | 4833.4 | 4936.07 | 980.34 |
| 20261102-215740-optimistic-r2 | measure | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 251 | 0 | 621 | INCONCLUSIVE | 7231.34 | 9312.01 | 232.41 |
| 20261102-215907-optimistic-r2 | measure | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 245 | 0 | 592 | INCONCLUSIVE | 3848.13 | 7059.03 | 534.51 |
| 20261102-220027-optimistic-r3-warmup | warmup | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 151 | 0 | 633 | OK | 9440.86 | 9601.23 | 509.99 |
| 20261102-220041-optimistic-r3 | measure | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 174 | 0 | 689 | INCONCLUSIVE | 3139.21 | 3282.01 | 637.6 |
| 20261102-220153-optimistic-r3 | measure | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 173 | 0 | 673 | OK | 7109.84 | 9345.66 | 523.6 |
| 20261102-220206-optimistic-r3 | measure | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 189 | 0 | 702 | INCONCLUSIVE | 2962.75 | 3497.44 | 634.95 |
