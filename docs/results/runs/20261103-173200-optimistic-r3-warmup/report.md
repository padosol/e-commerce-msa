# 실행 결과 20261103-173200-optimistic-r3-warmup

- RUN_ID: 20261103-173200-optimistic-r3-warmup
- 실행 시각: 2026-11-03T08:32:01Z
- git 커밋: 8e46ac0
- 전략: optimistic-r3
- kind: warmup
- USERS: 5000
- SALE_QUANTITY: 100
- payment 설정: `{"delayMs":200,"jitterMs":50,"failureRate":0.0}`

## 검증
- saleQuantity: 100
- paid: 100
- undersold: 0
- failed: 4900
- pending: 0
- preOrderFailed: 0
- finalStock: 0
- oversold: 0
- stockMismatch: 0
- conflicts: 153
- timeouts: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- k6 응답: paid 100, soldOut 4747, conflict 153, orderTimeout 0, gatewayTimeout 0, clientTimeout 0, other 0, total 5000
- verdict: OK
- inconclusive: 없음

## 원인 지표
- MySQL 행 락 대기 횟수: 813
- MySQL 행 락 누적 대기 시간(ms): 1535
- MySQL 행 락 평균 대기(ms): 1.89
- MySQL 현재 락 대기 수 최댓값: 0 (1s 표본)
- Hikari pending 최댓값: inventory 128, order 184, payment 0, product 0 (1s 표본)
- Tomcat busy threads 최댓값: gateway 189, inventory 139, order 200, payment 1, product 3 (1s 표본)
- 낙관적 락: 충돌 800, 재시도 647, 최종 CONFLICT 153
- 계층별 타임아웃: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- inventory 차감 서버 측 p95(ms): 57.29 (1s 표본)
- 차감 호출 수: 5000
- 결제 호출 수: 100
- Hikari 커넥션 획득 평균 대기(ms): inventory 8.88 (5011회), user 0.03 (8회), product 0.2 (5007회), payment 0.02 (109회), order 58.5 (10014회) (카운터 차이)
- GC 정지(구간 안, 횟수 / 합 / 1s 표본 최대): gateway Copy 9회 / 675ms / 669ms, MarkSweepCompact 1회 / 1203ms / 1203ms; user Copy 13회 / 13ms / 9ms; product Copy 17회 / 27ms / 9ms; inventory Copy 17회 / 78ms / 18ms; order G1 Young Generation 10회 / 40ms / 13ms; payment Copy 1회 / 1ms / 1ms

## 고정 설정
- order-service -> inventory/payment 클라이언트 connect/read timeout: 2s / 10s (order-service application.yml)
- gateway 하위 호출 read timeout: 30s (gateway application.yml)
- k6 요청 timeout: 60s, executor: per-vu-iterations, VU당 iterations: 1
- 앱 컨테이너(실행 중 컨테이너·Prometheus 에서 확인): gateway cpus 2 / 640MiB / Serial GC / JAVA_TOOL_OPTIONS `-XX:MaxRAMPercentage=70`, user-service cpus 2 / 640MiB / Serial GC / JAVA_TOOL_OPTIONS `-XX:MaxRAMPercentage=70`, product-service cpus 2 / 640MiB / Serial GC / JAVA_TOOL_OPTIONS `-XX:MaxRAMPercentage=70`, inventory-service cpus 2 / 640MiB / Serial GC / JAVA_TOOL_OPTIONS `-XX:MaxRAMPercentage=70`, order-service cpus 2 / 1024MiB / G1 GC / JAVA_TOOL_OPTIONS `-XX:MaxRAMPercentage=70 -XX:+UseG1GC`, payment-service cpus 2 / 640MiB / Serial GC / JAVA_TOOL_OPTIONS `-XX:MaxRAMPercentage=70`
- MySQL 컨테이너 제한: cpus 4 / 1536m (compose.yaml)
- Tomcat/Hikari 기본값, virtual threads off
- MySQL 격리 수준: REPEATABLE-READ, innodb_lock_wait_timeout: 50s
- Hikari connectionTimeout: 30s (기본값)
- Prometheus 스크레이프 간격: 1s (infra/prometheus/prometheus.yml)
- k6 시작 전 호스트 TIME_WAIT 대기: 0초 (시작 시 78개, 기준 < 500)
- k6 실행 위치: Docker 네트워크 안 컨테이너 (grafana/k6:2.2.0, network e-commerce_default → gateway:8080 직접, cpus 4 / memory 2g)
- kind: warmup

## 실패 사유별 건수
- SOLD_OUT: 4747
- STOCK_CONFLICT: 153

## k6
- 총 요청 수: 5000
- RPS: 844.22
- http_req_duration avg(ms): 3766.32
- http_req_duration p95(ms): 5270.98
- http_req_duration p99(ms): 5437.3
- http_req_duration max(ms): 5674.72
- http_req_failed 비율: 0
- orders_paid: 100
- orders_sold_out: 4747
- orders_other: 0
- 테스트 소요 시간(초): 7
