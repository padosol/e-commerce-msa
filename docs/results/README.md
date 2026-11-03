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
- k6 실행 위치: 1단계 4차부터 Docker 네트워크 안 컨테이너(grafana/k6:2.2.0, cpus 4 / memory 2g)가 `gateway:8080` 에 직접 연결. 0단계~1단계 3차는 호스트 k6 → `localhost:8080`(Docker Desktop 포트 포워딩 경유)

## 실행 목록

0단계 기준선(커밋 b3cf43c) 행은 1단계 이전 환경이다.
1단계 1~3차 측정 기록은 `stage1-attempt1/`, `stage1-attempt2/`, `stage1-attempt3/` 에 보관했다(다시 잰 이유는 각 폴더의 README).

| run | kind | strategy | users | stock | paid | oversold | undersold | finalStock | conflicts | timeouts | retries | verdict | p95(ms) | p99(ms) | rps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 20261031-105010-naive | measure | naive | 5000 | 100 | 423 | 323 | 0 | 0 | 0 | 0 | - | OVERSOLD | 12808.41 | 13027.97 | 377.54 |
| 20261031-105032-naive | measure | naive | 5000 | 100 | 566 | 466 | 0 | 0 | 0 | 0 | - | OVERSOLD | 8793.76 | 9027.14 | 545.66 |
| 20261031-105048-naive | measure | naive | 5000 | 100 | 720 | 620 | 0 | 0 | 0 | 0 | - | OVERSOLD | 7604.1 | 7848.12 | 525.39 |
