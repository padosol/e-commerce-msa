# 실행 결과 20261103-145553-optimistic-r3

- RUN_ID: 20261103-145553-optimistic-r3
- 실행 시각: 2026-11-03T05:56:22Z
- git 커밋: 5596cb0
- 전략: optimistic-r3
- kind: measure
- USERS: 5000
- SALE_QUANTITY: 100
- payment 설정: `{"delayMs":200,"jitterMs":50,"failureRate":0.0}`

## 검증
- saleQuantity: 100
- paid: 100
- undersold: 0
- failed: 4547
- pending: 0
- preOrderFailed: 0
- finalStock: 0
- oversold: 0
- stockMismatch: 0
- conflicts: 170
- timeouts: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- k6 응답: paid 100, soldOut 4377, conflict 170, orderTimeout 0, gatewayTimeout 0, clientTimeout 0, other 353, total 5000
- verdict: INCONCLUSIVE
- inconclusive: order-total, k6-unclassified

## 원인 지표
- MySQL 행 락 대기 횟수: 918
- MySQL 행 락 누적 대기 시간(ms): 2716
- MySQL 행 락 평균 대기(ms): 2.96
- MySQL 현재 락 대기 수 최댓값: 9 (1s 표본)
- Hikari pending 최댓값: inventory 0, order 181, payment 0, product 0 (1s 표본)
- Tomcat busy threads 최댓값: gateway 200, inventory 3, order 200, payment 39, product 3 (1s 표본)
- 낙관적 락: 충돌 846, 재시도 676, 최종 CONFLICT 170
- 계층별 타임아웃: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- inventory 차감 서버 측 p95(ms): 68.36 (1s 표본)
- 차감 호출 수: 4647
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
- k6 시작 전 호스트 TIME_WAIT 대기: 28초 (시작 시 34개, 기준 < 500)
- kind: measure

## 실패 사유별 건수
- SOLD_OUT: 4377
- STOCK_CONFLICT: 170

## k6
- 총 요청 수: 5000
- RPS: 530.09
- http_req_duration avg(ms): 2277.73
- http_req_duration p95(ms): 7132.97
- http_req_duration p99(ms): 9295.17
- http_req_duration max(ms): 9313.06
- http_req_failed 비율: 0.0706
- orders_paid: 100
- orders_sold_out: 4377
- orders_other: 353
- 테스트 소요 시간(초): 10
