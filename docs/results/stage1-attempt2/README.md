# 1단계 2차 측정 기록 (보관)

2차 측정은 2026-11-02 22:15~23:07 에 돌렸다. 1차 이후 `run-test.sh` 가 k6 시작 전 호스트 TIME_WAIT 정리를 기다리도록 고친 뒤다. 같은 Docker VM 에 다른 프로젝트 컨테이너가 유휴 상태로 함께 실행됐다(`stage1-environment.md`).
naive 외 6개 구성의 측정 21회 중 4회가 INCONCLUSIVE(`order-total`, `k6-unclassified`)였고, 판매(paid)는 모두 100이라 초과 판매는 없었다. 이 4회를 비교에서 빼고 같은 구성으로 다시 잰 실행으로 대신해 구성마다 3회를 채웠으며, 그 요약이 `stage1-summary.md` 다(요약 안의 `docs/results/runs/<RUN_ID>` 는 이 폴더의 `runs/<RUN_ID>` 를 가리킨다).
2026-11-03 사용자 결정: 이 처리 대신 다른 프로젝트 컨테이너를 멈추고 Docker Desktop 을 재시작한 깨끗한 환경에서 7개 구성 전체를 다시 잰다(3차). 이 기록은 비교용으로 보관한다.

- 1단계: INCONCLUSIVE 로 판정된 측정 행은 표에 그대로 두되 비교에서 빼고, 같은 구성으로 다시 잰 행으로 대신한다. 원인은 k6 → Docker Desktop 포트 포워딩 → 게이트웨이 연결 단계의 오류(connection reset by peer, i/o timeout)로 요청이 order-service 에 닿지 않았다(주문 행도 주문 전 실패 기록도 없음). 재고 전략과 무관하다(naive 워밍업에서도 발생). `20261102-224227-pessimistic` 은 연결이 맺어진 지 약 15초 뒤에 reset 됐다(오류 22:43:11, 실행 시작 22:42:55, p95 ≈ 15000 ms). Docker Desktop 포트 포워더가 gateway 컨테이너를 포기한 것과 맞지만 gateway 의 accept 경로가 원인일 가능성은 배제하지 못했다. 표의 INCONCLUSIVE 워밍업 4행도 같은 원인이다(워밍업은 비교하지 않는다).

| run | kind | strategy | users | stock | paid | oversold | undersold | finalStock | conflicts | timeouts | retries | verdict | p95(ms) | p99(ms) | rps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 20261102-221512-naive-warmup | warmup | naive | 5000 | 100 | 529 | 429 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 7805.63 | 7927.49 | 232.58 |
| 20261102-221638-naive | measure | naive | 5000 | 100 | 653 | 553 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 6747.98 | 6927.99 | 702.18 |
| 20261102-221650-naive | measure | naive | 5000 | 100 | 582 | 482 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 7354.19 | 9331.89 | 525.97 |
| 20261102-221732-naive | measure | naive | 5000 | 100 | 609 | 509 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 8270.37 | 8549.17 | 571.72 |
| 20261102-224048-pessimistic-warmup | warmup | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | INCONCLUSIVE | 8053.22 | 21354.13 | 232.95 |
| 20261102-224213-pessimistic | measure | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 5023.84 | 7107.32 | 524.35 |
| 20261102-224227-pessimistic | measure | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | INCONCLUSIVE | 15000.55 | 15002.89 | 231.7 |
| 20261102-224420-pessimistic | measure | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 6045.63 | 6181.18 | 780.94 |
| 20261102-224436-conditional-warmup | warmup | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 4843.49 | 4958.67 | 954.58 |
| 20261102-224507-conditional | measure | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 6117.84 | 6230.15 | 781.67 |
| 20261102-224545-conditional | measure | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 4709.54 | 4993 | 972.28 |
| 20261102-224622-conditional | measure | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | INCONCLUSIVE | 7249.62 | 14931.89 | 234.02 |
| 20261102-224821-optimistic-r0-warmup | warmup | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 763 | 0 | 0 | OK | 4313.5 | 4448.22 | 526.79 |
| 20261102-224834-optimistic-r0 | measure | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 505 | 0 | 0 | OK | 5316.13 | 5403.06 | 853.45 |
| 20261102-224912-optimistic-r0 | measure | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 507 | 0 | 0 | OK | 5160.25 | 5285.39 | 903.76 |
| 20261102-224949-optimistic-r0 | measure | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 504 | 0 | 0 | OK | 4968.55 | 5201.33 | 898 |
| 20261102-225032-optimistic-r1-warmup | warmup | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 346 | 0 | 417 | OK | 5286.34 | 5376.5 | 907.9 |
| 20261102-225102-optimistic-r1 | measure | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 362 | 0 | 422 | OK | 4516.64 | 4604.04 | 529.08 |
| 20261102-225143-optimistic-r1 | measure | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 354 | 0 | 409 | OK | 4778.39 | 6313.59 | 774.19 |
| 20261102-225221-optimistic-r1 | measure | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 391 | 0 | 453 | OK | 4924.89 | 5043.49 | 954.28 |
| 20261102-225304-optimistic-r2-warmup | warmup | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 219 | 0 | 577 | INCONCLUSIVE | 4040.85 | 4218.09 | 633.54 |
| 20261102-225437-optimistic-r2 | measure | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 256 | 0 | 634 | OK | 4552.72 | 4611.91 | 961.92 |
| 20261102-225446-optimistic-r2 | measure | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 262 | 0 | 625 | OK | 4869.94 | 4984.29 | 961.94 |
| 20261102-225523-optimistic-r2 | measure | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 259 | 0 | 621 | INCONCLUSIVE | 3269.2 | 3418.12 | 633.47 |
| 20261102-225709-optimistic-r3-warmup | warmup | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 136 | 0 | 609 | OK | 4716.76 | 4826.12 | 983.94 |
| 20261102-225718-optimistic-r3 | measure | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 186 | 0 | 698 | OK | 4925.37 | 4994.25 | 943.51 |
| 20261102-225755-optimistic-r3 | measure | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 179 | 0 | 687 | OK | 4449.32 | 4572.63 | 1036.77 |
| 20261102-225831-optimistic-r3 | measure | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 189 | 0 | 699 | OK | 5540 | 5665.41 | 850.9 |
| 20261102-230051-pessimistic-warmup | warmup | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 8022.95 | 8145.53 | 598.51 |
| 20261102-230103-pessimistic | measure | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 7182.33 | 9481.19 | 515.6 |
| 20261102-230150-conditional-warmup | warmup | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | INCONCLUSIVE | 3597.16 | 3752.57 | 631.22 |
| 20261102-230324-conditional | measure | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | INCONCLUSIVE | 4562.48 | 4668.12 | 634.58 |
| 20261102-230443-optimistic-r2-warmup | warmup | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 215 | 0 | 558 | INCONCLUSIVE | 4120.73 | 4270.89 | 636.76 |
| 20261102-230554-optimistic-r2 | measure | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 251 | 0 | 620 | OK | 7656.5 | 9355.37 | 523.44 |
| 20261102-230626-conditional-warmup | warmup | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 4611.66 | 4895.8 | 993.93 |
| 20261102-230645-conditional | measure | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 6148.91 | 6256.77 | 764.33 |
