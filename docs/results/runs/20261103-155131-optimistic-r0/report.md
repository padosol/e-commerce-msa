# 실행 결과 20261103-155131-optimistic-r0

- RUN_ID: 20261103-155131-optimistic-r0
- 실행 시각: 2026-11-03T06:51:32Z
- git 커밋: e7cc995
- 전략: optimistic-r0
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
- conflicts: 520
- timeouts: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- k6 응답: paid 100, soldOut 4380, conflict 520, orderTimeout 0, gatewayTimeout 0, clientTimeout 0, other 0, total 5000
- verdict: OK
- inconclusive: 없음

## 원인 지표
- MySQL 행 락 대기 횟수: 548
- MySQL 행 락 누적 대기 시간(ms): 1128
- MySQL 행 락 평균 대기(ms): 2.06
- MySQL 현재 락 대기 수 최댓값: 0 (1s 표본)
- Hikari pending 최댓값: inventory 0, order 181, payment 0, product 0 (1s 표본)
- Tomcat busy threads 최댓값: gateway 200, inventory 1, order 200, payment 26, product 4 (1s 표본)
- 낙관적 락: 충돌 520, 재시도 0, 최종 CONFLICT 520
- 계층별 타임아웃: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- inventory 차감 서버 측 p95(ms): 3.08 (1s 표본)
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
- k6 시작 전 호스트 TIME_WAIT 대기: 0초 (시작 시 117개, 기준 < 500)
- k6 실행 위치: Docker 네트워크 안 컨테이너 (grafana/k6:2.2.0, network e-commerce_default → gateway:8080 직접, cpus 4 / memory 2g)
- kind: measure

## 실패 사유별 건수
- SOLD_OUT: 4380
- STOCK_CONFLICT: 520

## k6
- 총 요청 수: 5000
- RPS: 1183.07
- http_req_duration avg(ms): 1403.69
- http_req_duration p95(ms): 2271.55
- http_req_duration p99(ms): 2340.67
- http_req_duration max(ms): 2479.92
- http_req_failed 비율: 0
- orders_paid: 100
- orders_sold_out: 4380
- orders_other: 0
- 테스트 소요 시간(초): 5
