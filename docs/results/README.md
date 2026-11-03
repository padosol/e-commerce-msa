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
- 컨테이너 제한: 앱 cpus 2 / 640m, MySQL cpus 4 / 1536m. 2단계(GC)부터 order-service 만 cpus 2 / 1g + `-XX:+UseG1GC` (나머지 앱은 Serial GC). 2단계부터는 실행마다 `meta.json` 의 `jvm` 과 report.md 에 실제 값이 기록된다
- Tomcat/Hikari 기본값, virtual threads off
- MySQL 격리 수준 REPEATABLE-READ, `innodb_lock_wait_timeout` 50s (실행마다 report.md 에 실측값 기록)
- Hikari connectionTimeout 30s (기본값)
- Prometheus 스크레이프 간격: 0단계 5s / 1단계부터 1s
- k6 실행 위치: 1단계 4차부터 Docker 네트워크 안 컨테이너(grafana/k6:2.2.0, cpus 4 / memory 2g)가 `gateway:8080` 에 직접 연결. 0단계~1단계 3차는 호스트 k6 → `localhost:8080`(Docker Desktop 포트 포워딩 경유)

## 실행 목록

0단계 기준선(커밋 b3cf43c) 행은 1단계 이전 환경이다.
1단계 1~3차 측정 기록은 `stage1-attempt1/`, `stage1-attempt2/`, `stage1-attempt3/` 에 보관했다(다시 잰 이유는 각 폴더의 README).

| run | kind | strategy | users | stock | paid | oversold | undersold | finalStock | conflicts | timeouts | retries | verdict | p95(ms) | p99(ms) | rps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 20261031-105010-naive | measure | naive | 5000 | 100 | 423 | 323 | 0 | 0 | 0 | 0 | - | OVERSOLD | 12808.41 | 13027.97 | 377.54 |
| 20261031-105032-naive | measure | naive | 5000 | 100 | 566 | 466 | 0 | 0 | 0 | 0 | - | OVERSOLD | 8793.76 | 9027.14 | 545.66 |
| 20261031-105048-naive | measure | naive | 5000 | 100 | 720 | 620 | 0 | 0 | 0 | 0 | - | OVERSOLD | 7604.1 | 7848.12 | 525.39 |
| 20261103-154814-naive-warmup | warmup | naive | 5000 | 100 | 528 | 428 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 8765.48 | 8923.59 | 533.67 |
| 20261103-154828-naive | measure | naive | 5000 | 100 | 534 | 434 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 3542.47 | 3653 | 1030.3 |
| 20261103-154838-naive | measure | naive | 5000 | 100 | 540 | 440 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 5200.36 | 5360.97 | 863.42 |
| 20261103-154848-naive | measure | naive | 5000 | 100 | 508 | 408 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 3640.36 | 3736.57 | 1008.86 |
| 20261103-154903-pessimistic-warmup | warmup | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 4999.54 | 5184.06 | 855.9 |
| 20261103-154914-pessimistic | measure | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 3357.79 | 3539.21 | 1037.94 |
| 20261103-154923-pessimistic | measure | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 4973.6 | 5105.73 | 926.29 |
| 20261103-154933-pessimistic | measure | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 3159.51 | 3263.97 | 1107.88 |
| 20261103-154948-conditional-warmup | warmup | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 4967.1 | 5375.33 | 881.84 |
| 20261103-154959-conditional | measure | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 3287.14 | 3385.29 | 1105.91 |
| 20261103-155008-conditional | measure | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 13161.83 | 26461.04 | 92.95 |
| 20261103-155106-conditional | measure | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 3328.79 | 3526.34 | 1132.38 |
| 20261103-155121-optimistic-r0-warmup | warmup | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 656 | 0 | 0 | OK | 4971.32 | 5105.04 | 914.61 |
| 20261103-155131-optimistic-r0 | measure | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 520 | 0 | 0 | OK | 2271.55 | 2340.67 | 1183.07 |
| 20261103-155140-optimistic-r0 | measure | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 493 | 0 | 0 | OK | 4721.02 | 4859.27 | 976.52 |
| 20261103-155150-optimistic-r0 | measure | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 500 | 0 | 0 | OK | 2671.92 | 6638.7 | 633.12 |
| 20261103-155208-optimistic-r1-warmup | warmup | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 317 | 0 | 382 | OK | 4180.82 | 4332.4 | 984.08 |
| 20261103-155217-optimistic-r1 | measure | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 369 | 0 | 435 | OK | 3354.12 | 6642.92 | 635.57 |
| 20261103-155230-optimistic-r1 | measure | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 384 | 0 | 450 | OK | 2466.39 | 2594.18 | 1178.23 |
| 20261103-155239-optimistic-r1 | measure | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 362 | 0 | 422 | OK | 3856.82 | 3993.13 | 1173.28 |
| 20261103-155253-optimistic-r2-warmup | warmup | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 211 | 0 | 551 | OK | 3226.76 | 3337.76 | 1096.39 |
| 20261103-155302-optimistic-r2 | measure | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 246 | 0 | 610 | OK | 4232 | 4389.8 | 1072.84 |
| 20261103-155312-optimistic-r2 | measure | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 262 | 0 | 634 | OK | 2872.98 | 3037.47 | 1263.77 |
| 20261103-155320-optimistic-r2 | measure | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 255 | 0 | 622 | OK | 3694.37 | 3882.6 | 1177.31 |
| 20261103-155335-optimistic-r3-warmup | warmup | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 146 | 0 | 627 | OK | 2976.84 | 3095.36 | 1151.12 |
| 20261103-155343-optimistic-r3 | measure | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 168 | 0 | 673 | OK | 4889.62 | 5097.82 | 886.73 |
| 20261103-155354-optimistic-r3 | measure | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 187 | 0 | 702 | OK | 3394.72 | 3577.37 | 1079.88 |
| 20261103-155403-optimistic-r3 | measure | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 191 | 0 | 709 | OK | 4544.07 | 4721.12 | 938.28 |

### 2단계: order-service 1g + G1 GC

1단계 4차와 같은 7개 구성 × (워밍업 1 + 측정 3). 위 표의 1단계 4차 행(20261103-1548~1554)과 비교한다.

| run | kind | strategy | users | stock | paid | oversold | undersold | finalStock | conflicts | timeouts | retries | verdict | p95(ms) | p99(ms) | rps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 20261103-171838-naive-warmup | warmup | naive | 5000 | 100 | 485 | 385 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 10706.66 | 11859.4 | 379.77 |
| 20261103-171856-naive | measure | naive | 5000 | 100 | 576 | 476 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 5797.42 | 6452.62 | 610.61 |
| 20261103-171909-naive | measure | naive | 5000 | 100 | 602 | 502 | 0 | 0 | 0 | 418 | 0 | OVERSOLD | 58787.97 | 58819.51 | 83.23 |
| 20261103-172116-naive | measure | naive | 5000 | 100 | 601 | 501 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 4207.54 | 4357.85 | 1020.77 |
| 20261103-172131-pessimistic-warmup | warmup | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 3533.93 | 3622.19 | 565.91 |
| 20261103-172145-pessimistic | measure | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 3774.87 | 3919.22 | 1148.7 |
| 20261103-172155-pessimistic | measure | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 6701.22 | 26461.42 | 181.05 |
| 20261103-172228-pessimistic | measure | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 2769.83 | 3400.71 | 1067.85 |
| 20261103-172243-conditional-warmup | warmup | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 4240.79 | 6744.12 | 320.88 |
| 20261103-172304-conditional | measure | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 3583.83 | 3776.93 | 1087.42 |
| 20261103-172314-conditional | measure | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 17 | 0 | INCONCLUSIVE | 26558.4 | 52623.75 | 83.3 |
| 20261103-172519-conditional | measure | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 3389.6 | 3549.41 | 639.17 |
| 20261103-172538-optimistic-r0-warmup | warmup | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 656 | 0 | 0 | OK | 6749.86 | 13228.87 | 346.83 |
| 20261103-172558-optimistic-r0 | measure | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 517 | 0 | 0 | OK | 3489.68 | 3644.16 | 1153 |
| 20261103-172607-optimistic-r0 | measure | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 504 | 0 | 0 | OK | 3516.43 | 3631.84 | 639.61 |
| 20261103-172620-optimistic-r0 | measure | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 518 | 0 | 0 | OK | 3778.93 | 3967.89 | 1122.3 |
| 20261103-172635-optimistic-r1-warmup | warmup | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 332 | 99 | 403 | INCONCLUSIVE | 26939.95 | 58872.07 | 83.21 |
| 20261103-172840-optimistic-r1 | measure | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 368 | 0 | 438 | OK | 3201.92 | 3400.15 | 1176.61 |
| 20261103-172849-optimistic-r1 | measure | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 389 | 0 | 459 | OK | 4627.03 | 4773.03 | 647.42 |
| 20261103-172902-optimistic-r1 | measure | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 381 | 0 | 436 | OK | 2884.69 | 2984.42 | 1156.55 |
| 20261103-172917-optimistic-r2-warmup | warmup | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 172 | 0 | 473 | OK | 4965.73 | 5151.99 | 874.11 |
| 20261103-172928-optimistic-r2 | measure | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 253 | 0 | 631 | OK | 1835.44 | 2851.29 | 1111.33 |
| 20261103-172938-optimistic-r2 | measure | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 254 | 0 | 605 | OK | 4653.53 | 4808.39 | 925.38 |
| 20261103-172949-optimistic-r2 | measure | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 255 | 26 | 621 | INCONCLUSIVE | 26791.43 | 52906.14 | 83.28 |
| 20261103-173200-optimistic-r3-warmup | warmup | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 153 | 0 | 647 | OK | 5270.98 | 5437.3 | 844.22 |
| 20261103-173211-optimistic-r3 | measure | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 183 | 0 | 701 | OK | 3269.15 | 3413.08 | 1153.21 |
| 20261103-173221-optimistic-r3 | measure | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 178 | 0 | 681 | OK | 4683.71 | 6705.82 | 637.29 |
| 20261103-173234-optimistic-r3 | measure | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 183 | 0 | 709 | OK | 3170.91 | 3352.65 | 1090.64 |
