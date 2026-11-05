# 실행 결과 20261103-173221-optimistic-r3

- RUN_ID: 20261103-173221-optimistic-r3
- 실행 시각: 2026-11-03T08:32:22Z
- git 커밋: 8e46ac0
- 전략: optimistic-r3
- kind: measure
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
- conflicts: 178
- timeouts: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- k6 응답: paid 100, soldOut 4722, conflict 178, orderTimeout 0, gatewayTimeout 0, clientTimeout 0, other 0, total 5000
- verdict: OK
- inconclusive: 없음

## 원인 지표
- MySQL 행 락 대기 횟수: 950
- MySQL 행 락 누적 대기 시간(ms): 1980
- MySQL 행 락 평균 대기(ms): 2.08
- MySQL 현재 락 대기 수 최댓값: 9 (1s 표본)
- Hikari pending 최댓값: inventory 0, order 187, payment 0, product 0 (1s 표본)
- Tomcat busy threads 최댓값: gateway 198, inventory 1, order 200, payment 31, product 1 (1s 표본)
- 낙관적 락: 충돌 859, 재시도 681, 최종 CONFLICT 178
- 계층별 타임아웃: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- inventory 차감 서버 측 p95(ms): 56.48 (1s 표본)
- 차감 호출 수: 5000
- 결제 호출 수: 100
- Hikari 커넥션 획득 평균 대기(ms): inventory 5.28 (5005회), user 0.09 (4회), product 0 (5003회), payment 0.02 (103회), order 57.19 (10009회) (카운터 차이)
- GC 정지(구간 안, 횟수 / 합 / 1s 표본 최대): gateway Copy 9회 / 834ms / 824ms, MarkSweepCompact 1회 / 962ms / 962ms; user Copy 13회 / 10ms / 10ms; product Copy 16회 / 25ms / 8ms; inventory Copy 12회 / 27ms / 7ms; order G1 Young Generation 10회 / 57ms / 23ms; payment Copy 2회 / 4ms / 2ms

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
- k6 시작 전 호스트 TIME_WAIT 대기: 0초 (시작 시 133개, 기준 < 500)
- k6 실행 위치: Docker 네트워크 안 컨테이너 (grafana/k6:2.2.0, network e-commerce_default → gateway:8080 직접, cpus 4 / memory 2g)
- kind: measure

## 실패 사유별 건수
- SOLD_OUT: 4722
- STOCK_CONFLICT: 178

## k6
- 총 요청 수: 5000
- RPS: 637.29
- http_req_duration avg(ms): 3397.79
- http_req_duration p95(ms): 4683.71
- http_req_duration p99(ms): 6705.82
- http_req_duration max(ms): 6716.74
- http_req_failed 비율: 0
- orders_paid: 100
- orders_sold_out: 4722
- orders_other: 0
- 테스트 소요 시간(초): 9
