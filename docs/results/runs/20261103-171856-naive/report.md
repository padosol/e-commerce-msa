# 실행 결과 20261103-171856-naive

- RUN_ID: 20261103-171856-naive
- 실행 시각: 2026-11-03T08:18:57Z
- git 커밋: 8e46ac0
- 전략: naive
- kind: measure
- USERS: 5000
- SALE_QUANTITY: 100
- payment 설정: `{"delayMs":200,"jitterMs":50,"failureRate":0.0}`

## 검증
- saleQuantity: 100
- paid: 576
- undersold: 0
- failed: 4424
- pending: 0
- preOrderFailed: 0
- finalStock: 0
- oversold: 476
- stockMismatch: -476
- conflicts: 0
- timeouts: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- k6 응답: paid 576, soldOut 4424, conflict 0, orderTimeout 0, gatewayTimeout 0, clientTimeout 0, other 0, total 5000
- verdict: OVERSOLD
- inconclusive: 없음

## 원인 지표
- MySQL 행 락 대기 횟수: 518
- MySQL 행 락 누적 대기 시간(ms): 1591
- MySQL 행 락 평균 대기(ms): 3.07
- MySQL 현재 락 대기 수 최댓값: 6 (1s 표본)
- Hikari pending 최댓값: inventory 0, order 184, payment 0, product 0 (1s 표본)
- Tomcat busy threads 최댓값: gateway 200, inventory 2, order 200, payment 116, product 3 (1s 표본)
- 낙관적 락: 충돌 0, 재시도 0, 최종 CONFLICT 0
- 계층별 타임아웃: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- inventory 차감 서버 측 p95(ms): 5.56 (1s 표본)
- 차감 호출 수: 5000
- 결제 호출 수: 576
- Hikari 커넥션 획득 평균 대기(ms): inventory 0.04 (5005회), user 0.06 (4회), product 0.01 (5003회), payment 0.05 (579회), order 117.66 (10009회) (카운터 차이)
- GC 정지(구간 안, 횟수 / 합 / 1s 표본 최대): gateway Copy 8회 / 776ms / 776ms; user Copy 13회 / 14ms / 10ms; product Copy 17회 / 35ms / 7ms; inventory Copy 15회 / 27ms / 6ms; order G1 Young Generation 27회 / 167ms / 60ms; payment Copy 4회 / 13ms / 9ms

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
- k6 시작 전 호스트 TIME_WAIT 대기: 0초 (시작 시 73개, 기준 < 500)
- k6 실행 위치: Docker 네트워크 안 컨테이너 (grafana/k6:2.2.0, network e-commerce_default → gateway:8080 직접, cpus 4 / memory 2g)
- kind: measure

## 실패 사유별 건수
- SOLD_OUT: 4424

## k6
- 총 요청 수: 5000
- RPS: 610.61
- http_req_duration avg(ms): 3731.65
- http_req_duration p95(ms): 5797.42
- http_req_duration p99(ms): 6452.62
- http_req_duration max(ms): 6485.52
- http_req_failed 비율: 0
- orders_paid: 576
- orders_sold_out: 4424
- orders_other: 0
- 테스트 소요 시간(초): 9
