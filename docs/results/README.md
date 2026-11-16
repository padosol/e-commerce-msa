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
- 부하 조건: 0단계~1단계 4차와 2단계 GC 실험은 USERS 5000 이 같은 순간에 요청(`ARRIVAL_SECONDS=0`). 1단계 5차(2026-11-03)부터 USERS 500 이 1초 동안 같은 간격으로 요청(`ARRIVAL_SECONDS=1`, 사용자 i 가 i × 2ms 뒤 출발). 재고 100 은 같다. 실행마다 `meta.json` 의 `users`·`arrivalSeconds` 와 report.md 의 "요청 도착" 에 기록된다
- 컨테이너 제한: 앱 cpus 2 / 640m, MySQL cpus 4 / 1536m.
  - 2단계 GC 실험(5000명 조건)에서만 order-service 를 1g + `-XX:+UseG1GC` 로 바꿨다. 지금 `compose.yaml` 에는 없다.
  - 2단계(게이트웨이 메모리)부터 gateway 만 1280m·swap 없음이다. heap `-Xmx448m`·Serial GC 는 그대로다(`stage2-gateway-memory.md`).
  - 2단계 GC 실험부터는 실행마다 `meta.json` 의 `jvm` 과 report.md 에 실제 값이 기록된다
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

### 2단계 GC 실험: order-service 1g + G1 GC (5000명 동시 요청, 설정은 반영하지 않음)

1단계 4차와 같은 7개 구성 × (워밍업 1 + 측정 3). 위 표의 1단계 4차 행(20261103-1548~1554)과 비교한다. 해석은 `stage2-summary.md`.

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

### 1단계 5차: 500명이 1초 동안 도착 (재고 100)

`USERS=500 ARRIVAL_SECONDS=1`. 위의 0단계·1단계 4차·2단계 GC 실험 행(5000명 동시 요청)과는 부하 조건이 달라 직접 비교하지 않는다.

| run | kind | strategy | users | stock | paid | oversold | undersold | finalStock | conflicts | timeouts | retries | verdict | p95(ms) | p99(ms) | rps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 20261103-221028-naive-warmup | warmup | naive | 500 | 100 | 439 | 339 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 2152.03 | 2262.27 | 168.95 |
| 20261103-221035-naive | measure | naive | 500 | 100 | 439 | 339 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 1047.79 | 1155.8 | 263.83 |
| 20261103-221041-naive | measure | naive | 500 | 100 | 426 | 326 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 751.31 | 883.51 | 342.83 |
| 20261103-221046-naive | measure | naive | 500 | 100 | 408 | 308 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 531.22 | 581.62 | 394.08 |
| 20261103-221057-pessimistic-warmup | warmup | pessimistic | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 549.63 | 731.06 | 393.12 |
| 20261103-221102-pessimistic | measure | pessimistic | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 351.51 | 418.53 | 448.63 |
| 20261103-221107-pessimistic | measure | pessimistic | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 284.83 | 345.44 | 485.91 |
| 20261103-221113-pessimistic | measure | pessimistic | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 275.42 | 306.98 | 477.24 |
| 20261103-221123-conditional-warmup | warmup | conditional | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 284.9 | 324.51 | 479.92 |
| 20261103-221128-conditional | measure | conditional | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 264.09 | 310.57 | 497.09 |
| 20261103-221133-conditional | measure | conditional | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 253.45 | 287.72 | 494.73 |
| 20261103-221138-conditional | measure | conditional | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 307 | 346.07 | 496.41 |
| 20261103-221149-optimistic-r0-warmup | warmup | optimistic-r0 | 500 | 100 | 100 | 0 | 0 | 0 | 172 | 0 | 0 | OK | 259.05 | 278.27 | 496.67 |
| 20261103-221154-optimistic-r0 | measure | optimistic-r0 | 500 | 100 | 100 | 0 | 0 | 0 | 187 | 0 | 0 | OK | 646.15 | 680.77 | 322.56 |
| 20261103-221159-optimistic-r0 | measure | optimistic-r0 | 500 | 100 | 100 | 0 | 0 | 0 | 92 | 0 | 0 | OK | 245.76 | 265.4 | 497.25 |
| 20261103-221204-optimistic-r0 | measure | optimistic-r0 | 500 | 100 | 100 | 0 | 0 | 0 | 77 | 0 | 0 | OK | 238.85 | 257.39 | 496.47 |
| 20261103-221215-optimistic-r1-warmup | warmup | optimistic-r1 | 500 | 100 | 100 | 0 | 0 | 0 | 107 | 0 | 173 | OK | 240.63 | 264.02 | 493.91 |
| 20261103-221220-optimistic-r1 | measure | optimistic-r1 | 500 | 100 | 100 | 0 | 0 | 0 | 51 | 0 | 104 | OK | 245.57 | 262.72 | 496.41 |
| 20261103-221225-optimistic-r1 | measure | optimistic-r1 | 500 | 100 | 100 | 0 | 0 | 0 | 13 | 0 | 54 | OK | 237.71 | 258.53 | 498.06 |
| 20261103-221230-optimistic-r1 | measure | optimistic-r1 | 500 | 100 | 100 | 0 | 0 | 0 | 34 | 0 | 70 | OK | 227.26 | 256.6 | 495.21 |
| 20261103-221241-optimistic-r2-warmup | warmup | optimistic-r2 | 500 | 100 | 100 | 0 | 0 | 0 | 34 | 0 | 158 | OK | 245.39 | 264.35 | 496.85 |
| 20261103-221246-optimistic-r2 | measure | optimistic-r2 | 500 | 100 | 100 | 0 | 0 | 0 | 20 | 0 | 118 | OK | 240.1 | 261.53 | 495.27 |
| 20261103-221251-optimistic-r2 | measure | optimistic-r2 | 500 | 100 | 100 | 0 | 0 | 0 | 15 | 0 | 96 | OK | 241.72 | 258.94 | 497.08 |
| 20261103-221256-optimistic-r2 | measure | optimistic-r2 | 500 | 100 | 100 | 0 | 0 | 0 | 13 | 0 | 79 | OK | 236.65 | 259.06 | 494.88 |
| 20261103-221307-optimistic-r3-warmup | warmup | optimistic-r3 | 500 | 100 | 100 | 0 | 0 | 0 | 147 | 0 | 620 | OK | 401.19 | 457.36 | 494.55 |
| 20261103-221312-optimistic-r3 | measure | optimistic-r3 | 500 | 100 | 100 | 0 | 0 | 0 | 12 | 0 | 120 | OK | 240.01 | 260.8 | 496.31 |
| 20261103-221317-optimistic-r3 | measure | optimistic-r3 | 500 | 100 | 100 | 0 | 0 | 0 | 13 | 0 | 133 | OK | 232.98 | 253.4 | 496.69 |
| 20261103-221322-optimistic-r3 | measure | optimistic-r3 | 500 | 100 | 100 | 0 | 0 | 0 | 9 | 0 | 92 | OK | 238.51 | 257.87 | 497.87 |

### 2단계: 게이트웨이 메모리 1280m·swap 없음 (500명이 1초 동안 도착, 재고 100)

`USERS=500 ARRIVAL_SECONDS=1`. 위의 1단계 5차 행과 같은 부하 조건이고, 게이트웨이 메모리만 다르다.

| run | kind | strategy | users | stock | paid | oversold | undersold | finalStock | conflicts | timeouts | retries | verdict | p95(ms) | p99(ms) | rps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 20261103-223546-naive-warmup | warmup | naive | 500 | 100 | 377 | 277 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 2017.3 | 2161.58 | 178.19 |
| 20261103-223553-naive | measure | naive | 500 | 100 | 500 | 400 | 0 | 7 | 0 | 0 | 0 | OVERSOLD | 1122.42 | 1216.41 | 242.04 |
| 20261103-223559-naive | measure | naive | 500 | 100 | 461 | 361 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 757.55 | 820.1 | 319.36 |
| 20261103-223605-naive | measure | naive | 500 | 100 | 418 | 318 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 625.25 | 685.02 | 363.24 |
| 20261103-223616-pessimistic-warmup | warmup | pessimistic | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 497.82 | 632.17 | 405.35 |
| 20261103-223621-pessimistic | measure | pessimistic | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 392.59 | 446.61 | 445.86 |
| 20261103-223626-pessimistic | measure | pessimistic | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 333.08 | 410.51 | 467.56 |
| 20261103-223631-pessimistic | measure | pessimistic | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 297.73 | 340.35 | 472.2 |
| 20261103-223642-conditional-warmup | warmup | conditional | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 280.6 | 335.74 | 488.17 |
| 20261103-223647-conditional | measure | conditional | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 306.5 | 343.99 | 486.44 |
| 20261103-223652-conditional | measure | conditional | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 257.88 | 311.09 | 494.37 |
| 20261103-223657-conditional | measure | conditional | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 267.8 | 295.12 | 496.57 |
| 20261103-223708-optimistic-r0-warmup | warmup | optimistic-r0 | 500 | 100 | 100 | 0 | 0 | 0 | 127 | 0 | 0 | OK | 248.41 | 267.49 | 495.39 |
| 20261103-223713-optimistic-r0 | measure | optimistic-r0 | 500 | 100 | 100 | 0 | 0 | 0 | 96 | 0 | 0 | OK | 246.51 | 264.25 | 494.96 |
| 20261103-223718-optimistic-r0 | measure | optimistic-r0 | 500 | 100 | 100 | 0 | 0 | 0 | 133 | 0 | 0 | OK | 250.08 | 261.42 | 496.09 |
| 20261103-223723-optimistic-r0 | measure | optimistic-r0 | 500 | 100 | 100 | 0 | 0 | 0 | 105 | 0 | 0 | OK | 237.73 | 260.51 | 496.06 |
| 20261103-223734-optimistic-r1-warmup | warmup | optimistic-r1 | 500 | 100 | 100 | 0 | 0 | 0 | 67 | 0 | 124 | OK | 248.41 | 263.01 | 497.73 |
| 20261103-223739-optimistic-r1 | measure | optimistic-r1 | 500 | 100 | 100 | 0 | 0 | 0 | 54 | 0 | 108 | OK | 244.52 | 260.28 | 497.49 |
| 20261103-223744-optimistic-r1 | measure | optimistic-r1 | 500 | 100 | 100 | 0 | 0 | 0 | 34 | 0 | 87 | OK | 241.47 | 264.26 | 495.43 |
| 20261103-223749-optimistic-r1 | measure | optimistic-r1 | 500 | 100 | 100 | 0 | 0 | 0 | 41 | 0 | 90 | OK | 239.94 | 260.12 | 497.57 |
| 20261103-223800-optimistic-r2-warmup | warmup | optimistic-r2 | 500 | 100 | 100 | 0 | 0 | 0 | 112 | 0 | 353 | OK | 249.63 | 277.97 | 492.87 |
| 20261103-223805-optimistic-r2 | measure | optimistic-r2 | 500 | 100 | 100 | 0 | 0 | 0 | 17 | 0 | 109 | OK | 241.72 | 261.18 | 492.95 |
| 20261103-223810-optimistic-r2 | measure | optimistic-r2 | 500 | 100 | 100 | 0 | 0 | 0 | 8 | 0 | 74 | OK | 235.58 | 254.83 | 496.98 |
| 20261103-223815-optimistic-r2 | measure | optimistic-r2 | 500 | 100 | 100 | 0 | 0 | 0 | 9 | 0 | 61 | OK | 234.48 | 253.41 | 498.53 |
| 20261103-223826-optimistic-r3-warmup | warmup | optimistic-r3 | 500 | 100 | 100 | 0 | 0 | 0 | 57 | 0 | 300 | OK | 244.93 | 273.59 | 496.96 |
| 20261103-223831-optimistic-r3 | measure | optimistic-r3 | 500 | 100 | 100 | 0 | 0 | 0 | 20 | 0 | 115 | OK | 232.44 | 252.14 | 494.59 |
| 20261103-223836-optimistic-r3 | measure | optimistic-r3 | 500 | 100 | 100 | 0 | 0 | 0 | 7 | 0 | 57 | OK | 238.14 | 259.59 | 495.84 |
| 20261103-223841-optimistic-r3 | measure | optimistic-r3 | 500 | 100 | 100 | 0 | 0 | 0 | 7 | 0 | 54 | OK | 241.87 | 257.82 | 498.15 |

### 2단계 회귀 매트릭스: 1차 (커밋 4161ac4, 500명이 1초 동안 도착, 재고 100)

스택을 새로 띄운 직후 잰 본 기록이다. optimistic-r0 가 UNDERSOLD 2회. 해석은 `stage2-ecommerce-regression.md`.

| run | kind | strategy | users | stock | paid | oversold | undersold | finalStock | conflicts | timeouts | retries | verdict | p95(ms) | p99(ms) | rps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 20261116-223031-naive-warmup | warmup | naive | 500 | 100 | 377 | 277 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 2445.79 | 2588.28 | 151.05 |
| 20261116-223039-naive | measure | naive | 500 | 100 | 444 | 344 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 1358.51 | 1438.49 | 229 |
| 20261116-223046-naive | measure | naive | 500 | 100 | 448 | 348 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 939.77 | 1037.19 | 287.77 |
| 20261116-223052-naive | measure | naive | 500 | 100 | 418 | 318 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 887.84 | 969.79 | 295.07 |
| 20261116-223103-pessimistic-warmup | warmup | pessimistic | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 821.91 | 932.2 | 341.57 |
| 20261116-223109-pessimistic | measure | pessimistic | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 706.01 | 834.66 | 353.17 |
| 20261116-223114-pessimistic | measure | pessimistic | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 589.9 | 619.15 | 377.36 |
| 20261116-223120-pessimistic | measure | pessimistic | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 757.32 | 883.08 | 333.75 |
| 20261116-223132-conditional-warmup | warmup | conditional | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 768.86 | 864.34 | 335.15 |
| 20261116-223138-conditional | measure | conditional | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 864.83 | 983.65 | 313.64 |
| 20261116-223144-conditional | measure | conditional | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 791.49 | 890.91 | 330.41 |
| 20261116-223149-conditional | measure | conditional | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 709.56 | 767.03 | 346.97 |
| 20261116-223201-optimistic-r0-warmup | warmup | optimistic-r0 | 500 | 100 | 100 | 0 | 0 | 0 | 285 | 0 | 0 | OK | 274.67 | 299.91 | 492.23 |
| 20261116-223206-optimistic-r0 | measure | optimistic-r0 | 500 | 100 | 96 | -4 | 4 | 4 | 404 | 0 | 0 | UNDERSOLD | 710.39 | 842.95 | 288.5 |
| 20261116-223213-optimistic-r0 | measure | optimistic-r0 | 500 | 100 | 99 | -1 | 1 | 1 | 401 | 0 | 0 | UNDERSOLD | 528.64 | 639.12 | 341.03 |
| 20261116-223219-optimistic-r0 | measure | optimistic-r0 | 500 | 100 | 100 | 0 | 0 | 0 | 181 | 0 | 0 | OK | 262.48 | 286.12 | 495.52 |
| 20261116-223230-optimistic-r1-warmup | warmup | optimistic-r1 | 500 | 100 | 100 | 0 | 0 | 0 | 101 | 0 | 166 | OK | 266.94 | 300.2 | 494.6 |
| 20261116-223235-optimistic-r1 | measure | optimistic-r1 | 500 | 100 | 100 | 0 | 0 | 0 | 67 | 0 | 130 | OK | 242.13 | 263.11 | 496.15 |
| 20261116-223240-optimistic-r1 | measure | optimistic-r1 | 500 | 100 | 100 | 0 | 0 | 0 | 73 | 0 | 135 | OK | 254.35 | 274.14 | 495.04 |
| 20261116-223246-optimistic-r1 | measure | optimistic-r1 | 500 | 100 | 100 | 0 | 0 | 0 | 44 | 0 | 90 | OK | 236.85 | 262.97 | 497.35 |
| 20261116-223257-optimistic-r2-warmup | warmup | optimistic-r2 | 500 | 100 | 100 | 0 | 0 | 0 | 79 | 0 | 260 | OK | 250.8 | 278.65 | 495.87 |
| 20261116-223302-optimistic-r2 | measure | optimistic-r2 | 500 | 100 | 100 | 0 | 0 | 0 | 12 | 0 | 106 | OK | 237.11 | 255.64 | 496.45 |
| 20261116-223307-optimistic-r2 | measure | optimistic-r2 | 500 | 100 | 100 | 0 | 0 | 0 | 20 | 0 | 113 | OK | 247.89 | 262.75 | 495.91 |
| 20261116-223312-optimistic-r2 | measure | optimistic-r2 | 500 | 100 | 100 | 0 | 0 | 0 | 51 | 0 | 181 | OK | 250.29 | 271.81 | 496.39 |
| 20261116-223323-optimistic-r3-warmup | warmup | optimistic-r3 | 500 | 100 | 100 | 0 | 0 | 0 | 116 | 0 | 527 | OK | 444.42 | 521.21 | 494.63 |
| 20261116-223328-optimistic-r3 | measure | optimistic-r3 | 500 | 100 | 100 | 0 | 0 | 0 | 16 | 0 | 159 | OK | 233.42 | 257.16 | 497.44 |
| 20261116-223333-optimistic-r3 | measure | optimistic-r3 | 500 | 100 | 100 | 0 | 0 | 0 | 45 | 0 | 245 | OK | 249.34 | 268.1 | 495.38 |
| 20261116-223338-optimistic-r3 | measure | optimistic-r3 | 500 | 100 | 100 | 0 | 0 | 0 | 13 | 0 | 138 | OK | 242.78 | 262.15 | 497.16 |

### 2단계 회귀 매트릭스: 진단 재측정 (1차 직후 같은 스택, 앞 4개 구성만)

같은 커밋·같은 설정. 데워진 스택에서 naive·pessimistic·conditional·optimistic-r0 만 다시 잰 것이다. 해석은 `stage2-ecommerce-regression.md`.

| run | kind | strategy | users | stock | paid | oversold | undersold | finalStock | conflicts | timeouts | retries | verdict | p95(ms) | p99(ms) | rps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 20261116-224312-naive-warmup | warmup | naive | 500 | 100 | 449 | 349 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 291.91 | 317.53 | 436.11 |
| 20261116-224317-naive | measure | naive | 500 | 100 | 349 | 249 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 290.84 | 314.48 | 495.53 |
| 20261116-224322-naive | measure | naive | 500 | 100 | 215 | 115 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 253.61 | 265.21 | 495.37 |
| 20261116-224327-naive | measure | naive | 500 | 100 | 185 | 85 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 249.67 | 261.78 | 497.87 |
| 20261116-224338-pessimistic-warmup | warmup | pessimistic | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 295.27 | 331.43 | 496.49 |
| 20261116-224343-pessimistic | measure | pessimistic | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 258.51 | 277.97 | 495.65 |
| 20261116-224348-pessimistic | measure | pessimistic | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 245.85 | 273.76 | 496.15 |
| 20261116-224353-pessimistic | measure | pessimistic | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 241.41 | 258.16 | 497.42 |
| 20261116-224404-conditional-warmup | warmup | conditional | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 246.05 | 264.74 | 495.89 |
| 20261116-224409-conditional | measure | conditional | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 240.74 | 260.09 | 495.1 |
| 20261116-224414-conditional | measure | conditional | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 242.49 | 260.31 | 498.16 |
| 20261116-224419-conditional | measure | conditional | 500 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 247.81 | 267.41 | 496.42 |
| 20261116-224430-optimistic-r0-warmup | warmup | optimistic-r0 | 500 | 100 | 100 | 0 | 0 | 0 | 108 | 0 | 0 | OK | 249.9 | 265.58 | 496.11 |
| 20261116-224435-optimistic-r0 | measure | optimistic-r0 | 500 | 100 | 100 | 0 | 0 | 0 | 93 | 0 | 0 | OK | 237.19 | 258.97 | 497.7 |
| 20261116-224441-optimistic-r0 | measure | optimistic-r0 | 500 | 100 | 100 | 0 | 0 | 0 | 115 | 0 | 0 | OK | 238.46 | 260.94 | 496.56 |
| 20261116-224446-optimistic-r0 | measure | optimistic-r0 | 500 | 100 | 100 | 0 | 0 | 0 | 80 | 0 | 0 | OK | 239.59 | 256.17 | 496.16 |
