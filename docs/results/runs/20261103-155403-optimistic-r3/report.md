# 실행 결과 20261103-155403-optimistic-r3

- RUN_ID: 20261103-155403-optimistic-r3
- 실행 시각: 2026-11-03T06:54:04Z
- git 커밋: e7cc995
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
- conflicts: 191
- timeouts: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- k6 응답: paid 100, soldOut 4709, conflict 191, orderTimeout 0, gatewayTimeout 0, clientTimeout 0, other 0, total 5000
- verdict: OK
- inconclusive: 없음

## 원인 지표
- MySQL 행 락 대기 횟수: 997
- MySQL 행 락 누적 대기 시간(ms): 1621
- MySQL 행 락 평균 대기(ms): 1.63
- MySQL 현재 락 대기 수 최댓값: 9 (1s 표본)
- Hikari pending 최댓값: inventory 0, order 186, payment 0, product 0 (1s 표본)
- Tomcat busy threads 최댓값: gateway 197, inventory 1, order 200, payment 44, product 1 (1s 표본)
- 낙관적 락: 충돌 900, 재시도 709, 최종 CONFLICT 191
- 계층별 타임아웃: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- inventory 차감 서버 측 p95(ms): 37.66 (1s 표본)
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
- k6 시작 전 호스트 TIME_WAIT 대기: 0초 (시작 시 127개, 기준 < 500)
- k6 실행 위치: Docker 네트워크 안 컨테이너 (grafana/k6:2.2.0, network e-commerce_default → gateway:8080 직접, cpus 4 / memory 2g)
- kind: measure

## 실패 사유별 건수
- SOLD_OUT: 4709
- STOCK_CONFLICT: 191

## k6
- 총 요청 수: 5000
- RPS: 938.28
- http_req_duration avg(ms): 3026
- http_req_duration p95(ms): 4544.07
- http_req_duration p99(ms): 4721.12
- http_req_duration max(ms): 5076.36
- http_req_failed 비율: 0
- orders_paid: 100
- orders_sold_out: 4709
- orders_other: 0
- 테스트 소요 시간(초): 6
