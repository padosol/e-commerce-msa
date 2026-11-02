# 선착순 주문 부하 테스트 결과

`scripts/run-test.sh` 가 실행마다 `runs/<RUN_ID>/` 를 만들고 아래 표에 한 줄을 추가한다.

## verdict
판매 수량의 기준은 DB 의 PAID 주문 수(paid)다. 위에서부터 먼저 해당하는 것이 판정이다.
- `OVERSOLD`: `paid > saleQuantity`. 유일한 실패.
- `INCONCLUSIVE`: 실행 자체를 믿을 수 없음. 아래 중 하나라도 해당(해당한 검사 이름이 `verify.json` 의 `inconclusive` 에 남는다).
  - `pending`: 대기 후에도 pending > 0.
  - `order-total`: `paid + failed + 주문 생성 전 실패` 가 USERS 와 다름.
  - `non-characteristic-reason`: "방식의 특성이 아닌" 실패 사유(`INVENTORY_ERROR`·`PAYMENT_ERROR`·`PRODUCT_ERROR`·`PAYMENT_DECLINED` 등)가 1건 이상.
  - `k6-unclassified`: k6 응답 중 분류되지 않은 것(401, 400 미오픈, 연결 오류, 그 밖의 5xx 등)이 1건 이상.
  - `k6-total`: k6 응답 합계가 USERS 와 다름.
  - `k6-db-paid`: `k6 paid ≤ DB paid ≤ k6 paid + (게이트웨이 타임아웃 + k6 타임아웃)` 을 벗어남.
- `UNDERSOLD`: 위 둘이 아니고 `paid < saleQuantity`.
- `OK`: 위 셋이 아니고 `paid == saleQuantity`.

## 고정 설정
아래 기준선 3회(커밋 6ade07d)와 이후 모든 실행에 공통이며, 이후 실행은 각 report.md 에 같은 블록이 기록된다.
기준선 report.md 의 `git 커밋: 6ade07d` 는 작성자 정보 재작성 전 해시이며, 현재 히스토리의 같은 커밋은 `b3cf43c` 다.
- order-service 클라이언트 connect/read timeout: 2s / 10s
- gateway 하위 호출 read timeout: 30s
- k6 요청 timeout: 60s, executor: per-vu-iterations, VU당 1 iteration
- 컨테이너 제한: 앱 cpus 2 / 640m, MySQL cpus 4 / 1536m
- Tomcat/Hikari 기본값, virtual threads off
- MySQL 격리 수준 REPEATABLE-READ, `innodb_lock_wait_timeout` 50s (실행마다 report.md 에 실측값 기록)
- Hikari connectionTimeout 30s (기본값)
- Prometheus 스크레이프 간격: 0단계 5s / 1단계부터 1s
- 1단계 측정 중 같은 Docker VM 에 다른 프로젝트 컨테이너가 유휴 상태로 함께 실행됨 (자세한 내용: stage1-environment.md)

## 실행 목록

0단계 기준선(커밋 b3cf43c) 행은 1단계 이전 환경이다.

- 1단계: INCONCLUSIVE 로 판정된 측정 행은 표에 그대로 두되 비교에서 빼고, 같은 구성으로 다시 잰 행으로 대신한다. 원인은 k6 → Docker Desktop 포트 포워딩 → 게이트웨이 연결 단계의 오류(connection reset by peer, i/o timeout)로 요청이 시스템에 닿지 않았고, 재고 전략과 무관하다(naive 워밍업에서도 발생).

| run | kind | strategy | users | stock | paid | oversold | undersold | finalStock | conflicts | timeouts | retries | verdict | p95(ms) | p99(ms) | rps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 20261031-105010-naive | measure | naive | 5000 | 100 | 423 | 323 | 0 | 0 | 0 | 0 | - | OVERSOLD | 12808.41 | 13027.97 | 377.54 |
| 20261031-105032-naive | measure | naive | 5000 | 100 | 566 | 466 | 0 | 0 | 0 | 0 | - | OVERSOLD | 8793.76 | 9027.14 | 545.66 |
| 20261031-105048-naive | measure | naive | 5000 | 100 | 720 | 620 | 0 | 0 | 0 | 0 | - | OVERSOLD | 7604.1 | 7848.12 | 525.39 |
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
