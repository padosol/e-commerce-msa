# 실행 결과 20261102-225831-optimistic-r3

- RUN_ID: 20261102-225831-optimistic-r3
- 실행 시각: 2026-11-02T13:59:00Z
- git 커밋: 9da812b
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
- conflicts: 189
- timeouts: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- k6 응답: paid 100, soldOut 4711, conflict 189, orderTimeout 0, gatewayTimeout 0, clientTimeout 0, other 0, total 5000
- verdict: OK
- inconclusive: 없음

## 원인 지표
- MySQL 행 락 대기 횟수: 982
- MySQL 행 락 누적 대기 시간(ms): 3545
- MySQL 행 락 평균 대기(ms): 3.61
- MySQL 현재 락 대기 수 최댓값: 4 (1s 표본)
- Hikari pending 최댓값: inventory 0, order 186, payment 0, product 0 (1s 표본)
- Tomcat busy threads 최댓값: gateway 197, inventory 1, order 200, payment 54, product 5 (1s 표본)
- 낙관적 락: 충돌 888, 재시도 699, 최종 CONFLICT 189
- 계층별 타임아웃: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- inventory 차감 서버 측 p95(ms): 26.84 (1s 표본)
- 차감 호출 수: 5000
- 결제 호출 수: 100

## 고정 설정
- order-service -> inventory/payment 클라이언트 connect/read timeout: 2s / 10s (order-service application.yml)
- gateway 하위 호출 read timeout: 30s (gateway application.yml)
- k6 요청 timeout: 60s, executor: per-vu-iterations, VU당 iterations: 1
- 컨테이너 제한: 앱 cpus 2 / 640m, MySQL cpus 4 / 1536m (compose.yaml)
- Tomcat/Hikari 기본값, virtual threads off
- MySQL 격리 수준: REPEATABLE-READ, innodb_lock_wait_timeout: 50s
- Hikari connectionTimeout: 30s (기본값)
- Prometheus 스크레이프 간격: 1s (infra/prometheus/prometheus.yml)
- k6 시작 전 호스트 TIME_WAIT 대기: 28초 (시작 시 31개, 기준 < 500)
- kind: measure

## 실패 사유별 건수
- SOLD_OUT: 4711
- STOCK_CONFLICT: 189

## k6
- 총 요청 수: 5000
- RPS: 850.9
- http_req_duration avg(ms): 3710.46
- http_req_duration p95(ms): 5540
- http_req_duration p99(ms): 5665.41
- http_req_duration max(ms): 5706.63
- http_req_failed 비율: 0
- orders_paid: 100
- orders_sold_out: 4711
- orders_other: 0
- 테스트 소요 시간(초): 6
