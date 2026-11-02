# 실행 결과 20261102-215907-optimistic-r2

- RUN_ID: 20261102-215907-optimistic-r2
- 실행 시각: 2026-11-02T12:59:08Z
- git 커밋: 2b23ce0
- 전략: optimistic-r2
- kind: measure
- USERS: 5000
- SALE_QUANTITY: 100
- payment 설정: `{"delayMs":200,"jitterMs":50,"failureRate":0.0}`

## 검증
- saleQuantity: 100
- paid: 100
- undersold: 0
- failed: 4598
- pending: 0
- preOrderFailed: 0
- finalStock: 0
- oversold: 0
- stockMismatch: 0
- conflicts: 245
- timeouts: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- k6 응답: paid 100, soldOut 4353, conflict 245, orderTimeout 0, gatewayTimeout 0, clientTimeout 0, other 302, total 5000
- verdict: INCONCLUSIVE
- inconclusive: order-total, k6-unclassified

## 원인 지표
- MySQL 행 락 대기 횟수: 926
- MySQL 행 락 누적 대기 시간(ms): 2097
- MySQL 행 락 평균 대기(ms): 2.26
- MySQL 현재 락 대기 수 최댓값: 0 (1s 표본)
- Hikari pending 최댓값: inventory 0, order 185, payment 0, product 0 (1s 표본)
- Tomcat busy threads 최댓값: gateway 200, inventory 3, order 200, payment 3, product 4 (1s 표본)
- 낙관적 락: 충돌 837, 재시도 592, 최종 CONFLICT 245
- 계층별 타임아웃: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- inventory 차감 서버 측 p95(ms): 20.48 (1s 표본)
- 차감 호출 수: 4698
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
- kind: measure

## 실패 사유별 건수
- SOLD_OUT: 4353
- STOCK_CONFLICT: 245

## k6
- 총 요청 수: 5000
- RPS: 534.51
- http_req_duration avg(ms): 1823.53
- http_req_duration p95(ms): 3848.13
- http_req_duration p99(ms): 7059.03
- http_req_duration max(ms): 9249.29
- http_req_failed 비율: 0.0604
- orders_paid: 100
- orders_sold_out: 4353
- orders_other: 302
- 테스트 소요 시간(초): 10
