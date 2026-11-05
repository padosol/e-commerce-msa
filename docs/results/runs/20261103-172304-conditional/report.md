# 실행 결과 20261103-172304-conditional

- RUN_ID: 20261103-172304-conditional
- 실행 시각: 2026-11-03T08:23:05Z
- git 커밋: 8e46ac0
- 전략: conditional
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
- conflicts: 0
- timeouts: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- k6 응답: paid 100, soldOut 4900, conflict 0, orderTimeout 0, gatewayTimeout 0, clientTimeout 0, other 0, total 5000
- verdict: OK
- inconclusive: 없음

## 원인 지표
- MySQL 행 락 대기 횟수: 211
- MySQL 행 락 누적 대기 시간(ms): 1291
- MySQL 행 락 평균 대기(ms): 6.12
- MySQL 현재 락 대기 수 최댓값: 0 (1s 표본)
- Hikari pending 최댓값: inventory 15, order 186, payment 0, product 0 (1s 표본)
- Tomcat busy threads 최댓값: gateway 190, inventory 21, order 200, payment 1, product 5 (1s 표본)
- 낙관적 락: 충돌 0, 재시도 0, 최종 CONFLICT 0
- 계층별 타임아웃: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- inventory 차감 서버 측 p95(ms): 11.65 (1s 표본)
- 차감 호출 수: 5000
- 결제 호출 수: 100
- Hikari 커넥션 획득 평균 대기(ms): inventory 0.89 (9905회), user 0.05 (4회), product 0 (5003회), payment 0.02 (103회), order 64.56 (10009회) (카운터 차이)
- GC 정지(구간 안, 횟수 / 합 / 1s 표본 최대): gateway Copy 8회 / 904ms / 904ms; user Copy 13회 / 14ms / 12ms; product Copy 17회 / 26ms / 8ms; inventory Copy 14회 / 43ms / 12ms; order G1 Young Generation 10회 / 39ms / 13ms; payment Copy 1회 / 3ms / 3ms

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
- k6 시작 전 호스트 TIME_WAIT 대기: 0초 (시작 시 96개, 기준 < 500)
- k6 실행 위치: Docker 네트워크 안 컨테이너 (grafana/k6:2.2.0, network e-commerce_default → gateway:8080 직접, cpus 4 / memory 2g)
- kind: measure

## 실패 사유별 건수
- SOLD_OUT: 4900

## k6
- 총 요청 수: 5000
- RPS: 1087.42
- http_req_duration avg(ms): 2301.54
- http_req_duration p95(ms): 3583.83
- http_req_duration p99(ms): 3776.93
- http_req_duration max(ms): 3961.08
- http_req_failed 비율: 0
- orders_paid: 100
- orders_sold_out: 4900
- orders_other: 0
- 테스트 소요 시간(초): 6
