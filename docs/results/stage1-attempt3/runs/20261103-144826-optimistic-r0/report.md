# 실행 결과 20261103-144826-optimistic-r0

- RUN_ID: 20261103-144826-optimistic-r0
- 실행 시각: 2026-11-03T05:48:54Z
- git 커밋: 5596cb0
- 전략: optimistic-r0
- kind: measure
- USERS: 5000
- SALE_QUANTITY: 100
- payment 설정: `{"delayMs":200,"jitterMs":50,"failureRate":0.0}`

## 검증
- saleQuantity: 100
- paid: 100
- undersold: 0
- failed: 4386
- pending: 0
- preOrderFailed: 0
- finalStock: 0
- oversold: 0
- stockMismatch: 0
- conflicts: 442
- timeouts: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- k6 응답: paid 100, soldOut 3944, conflict 442, orderTimeout 0, gatewayTimeout 0, clientTimeout 0, other 514, total 5000
- verdict: INCONCLUSIVE
- inconclusive: order-total, k6-unclassified

## 원인 지표
- MySQL 행 락 대기 횟수: 460
- MySQL 행 락 누적 대기 시간(ms): 914
- MySQL 행 락 평균 대기(ms): 1.99
- MySQL 현재 락 대기 수 최댓값: 4 (1s 표본)
- Hikari pending 최댓값: inventory 0, order 183, payment 0, product 0 (1s 표본)
- Tomcat busy threads 최댓값: gateway 200, inventory 2, order 200, payment 43, product 1 (1s 표본)
- 낙관적 락: 충돌 442, 재시도 0, 최종 CONFLICT 442
- 계층별 타임아웃: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- inventory 차감 서버 측 p95(ms): 2.73 (1s 표본)
- 차감 호출 수: 4486
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
- k6 시작 전 호스트 TIME_WAIT 대기: 27초 (시작 시 30개, 기준 < 500)
- kind: measure

## 실패 사유별 건수
- SOLD_OUT: 3944
- STOCK_CONFLICT: 442

## k6
- 총 요청 수: 5000
- RPS: 533.58
- http_req_duration avg(ms): 2459.21
- http_req_duration p95(ms): 7113.96
- http_req_duration p99(ms): 9250.44
- http_req_duration max(ms): 9288.09
- http_req_failed 비율: 0.1028
- orders_paid: 100
- orders_sold_out: 3944
- orders_other: 514
- 테스트 소요 시간(초): 10
