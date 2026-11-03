# 실행 결과 20261103-144657-optimistic-r0-warmup

- RUN_ID: 20261103-144657-optimistic-r0-warmup
- 실행 시각: 2026-11-03T05:46:58Z
- git 커밋: 5596cb0
- 전략: optimistic-r0
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
- conflicts: 747
- timeouts: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- k6 응답: paid 100, soldOut 4153, conflict 747, orderTimeout 0, gatewayTimeout 0, clientTimeout 0, other 0, total 5000
- verdict: OK
- inconclusive: 없음

## 원인 지표
- MySQL 행 락 대기 횟수: 779
- MySQL 행 락 누적 대기 시간(ms): 2957
- MySQL 행 락 평균 대기(ms): 3.8
- MySQL 현재 락 대기 수 최댓값: 9 (1s 표본)
- Hikari pending 최댓값: inventory 40, order 189, payment 0, product 0 (1s 표본)
- Tomcat busy threads 최댓값: gateway 198, inventory 17, order 200, payment 23, product 5 (1s 표본)
- 낙관적 락: 충돌 747, 재시도 0, 최종 CONFLICT 747
- 계층별 타임아웃: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- inventory 차감 서버 측 p95(ms): 24.43 (1s 표본)
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
- k6 시작 전 호스트 TIME_WAIT 대기: 0초 (시작 시 73개, 기준 < 500)
- kind: warmup

## 실패 사유별 건수
- SOLD_OUT: 4153
- STOCK_CONFLICT: 747

## k6
- 총 요청 수: 5000
- RPS: 1043.97
- http_req_duration avg(ms): 2548.06
- http_req_duration p95(ms): 4310.61
- http_req_duration p99(ms): 4417.26
- http_req_duration max(ms): 4435.49
- http_req_failed 비율: 0
- orders_paid: 100
- orders_sold_out: 4153
- orders_other: 0
- 테스트 소요 시간(초): 6
