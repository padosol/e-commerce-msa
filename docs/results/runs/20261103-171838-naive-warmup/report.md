# 실행 결과 20261103-171838-naive-warmup

- RUN_ID: 20261103-171838-naive-warmup
- 실행 시각: 2026-11-03T08:18:39Z
- git 커밋: 8e46ac0
- 전략: naive
- kind: warmup
- USERS: 5000
- SALE_QUANTITY: 100
- payment 설정: `{"delayMs":200,"jitterMs":50,"failureRate":0.0}`

## 검증
- saleQuantity: 100
- paid: 485
- undersold: 0
- failed: 4515
- pending: 0
- preOrderFailed: 0
- finalStock: 0
- oversold: 385
- stockMismatch: -385
- conflicts: 0
- timeouts: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- k6 응답: paid 485, soldOut 4515, conflict 0, orderTimeout 0, gatewayTimeout 0, clientTimeout 0, other 0, total 5000
- verdict: OVERSOLD
- inconclusive: 없음

## 원인 지표
- MySQL 행 락 대기 횟수: 416
- MySQL 행 락 누적 대기 시간(ms): 1757
- MySQL 행 락 평균 대기(ms): 4.22
- MySQL 현재 락 대기 수 최댓값: 0 (1s 표본)
- Hikari pending 최댓값: inventory 0, order 182, payment 0, product 0 (1s 표본)
- Tomcat busy threads 최댓값: gateway 188, inventory 6, order 200, payment 59, product 1 (1s 표본)
- 낙관적 락: 충돌 0, 재시도 0, 최종 CONFLICT 0
- 계층별 타임아웃: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- inventory 차감 서버 측 p95(ms): 10.23 (1s 표본)
- 차감 호출 수: 5000
- 결제 호출 수: 485
- Hikari 커넥션 획득 평균 대기(ms): inventory 0.05 (5011회), user 0.04 (10회), product 0.17 (5009회), payment 0.02 (494회), order 177.99 (10014회) (카운터 차이)
- GC 정지(구간 안, 횟수 / 합 / 1s 표본 최대): gateway Copy 86회 / 661ms / 659ms; user Copy 14회 / 15ms / 11ms; product Copy 18회 / 56ms / 7ms; inventory Copy 18회 / 50ms / 7ms; order G1 Young Generation 30회 / 240ms / 59ms; payment Copy 3회 / 14ms / 11ms

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
- k6 시작 전 호스트 TIME_WAIT 대기: 0초 (시작 시 34개, 기준 < 500)
- k6 실행 위치: Docker 네트워크 안 컨테이너 (grafana/k6:2.2.0, network e-commerce_default → gateway:8080 직접, cpus 4 / memory 2g)
- kind: warmup

## 실패 사유별 건수
- SOLD_OUT: 4515

## k6
- 총 요청 수: 5000
- RPS: 379.77
- http_req_duration avg(ms): 6404.96
- http_req_duration p95(ms): 10706.66
- http_req_duration p99(ms): 11859.4
- http_req_duration max(ms): 11933.71
- http_req_failed 비율: 0
- orders_paid: 485
- orders_sold_out: 4515
- orders_other: 0
- 테스트 소요 시간(초): 14
