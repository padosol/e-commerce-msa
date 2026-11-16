# 실행 결과 20261116-223132-conditional-warmup

- RUN_ID: 20261116-223132-conditional-warmup
- 실행 시각: 2026-11-16T13:31:32Z
- git 커밋: 4161ac4
- 전략: conditional
- kind: warmup
- USERS: 500
- 요청 도착: 500명이 1초 동안 같은 간격으로 1번씩
- SALE_QUANTITY: 100
- payment 설정: `{"delayMs":200,"jitterMs":50,"failureRate":0.0}`

## 검증
- saleQuantity: 100
- paid: 100
- undersold: 0
- failed: 400
- pending: 0
- preOrderFailed: 0
- finalStock: 0
- oversold: 0
- stockMismatch: 0
- conflicts: 0
- timeouts: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- k6 응답: paid 100, soldOut 400, conflict 0, orderTimeout 0, gatewayTimeout 0, clientTimeout 0, other 0, total 500
- verdict: OK
- inconclusive: 없음

## 원인 지표
- MySQL 행 락 대기 횟수: 140
- MySQL 행 락 누적 대기 시간(ms): 2049
- MySQL 행 락 평균 대기(ms): 14.64
- MySQL 현재 락 대기 수 최댓값: 0 (1s 표본)
- Hikari pending 최댓값: inventory 0, order 177, payment 0, product 0 (1s 표본)
- Tomcat busy threads 최댓값: gateway 200, inventory 6, order 197, payment 57, product 2 (1s 표본)
- 낙관적 락: 충돌 0, 재시도 0, 최종 CONFLICT 0
- 계층별 타임아웃: inventory 0, payment 0, product 0, orderDb 0, gateway 0, client 0, total 0
- inventory 차감 서버 측 p95(ms): 50.32 (1s 표본)
- 차감 호출 수: 500
- 결제 호출 수: 100
- Hikari 커넥션 획득 평균 대기(ms): inventory 0 (-1113회), order 168.86 (1014회), payment 0.05 (109회), product 0.02 (507회), user 0.05 (8회) (카운터 차이)
- GC 정지(구간 안, 횟수 / 합 / 1s 표본 최대): gateway Copy 9회 / 66ms / 51ms; user Copy 1회 / 2ms / 2ms; product Copy 2회 / 6ms / 3ms; order Copy 8회 / 47ms / 33ms; payment Copy 1회 / 2ms / 2ms

## 고정 설정
- order-service -> inventory/payment 클라이언트 connect/read timeout: 2s / 10s (order-service application.yml)
- gateway 하위 호출 read timeout: 30s (gateway application.yml)
- k6 요청 timeout: 60s, executor: per-vu-iterations, 사용자당 요청 1번
- 앱 컨테이너(실행 중 컨테이너·Prometheus 에서 확인): gateway cpus 2 / 1280MiB / Serial GC / JAVA_TOOL_OPTIONS `-Xmx448m -XX:+UseSerialGC`, user-service cpus 2 / 640MiB / Serial GC / JAVA_TOOL_OPTIONS `-XX:MaxRAMPercentage=70`, product-service cpus 2 / 640MiB / Serial GC / JAVA_TOOL_OPTIONS `-XX:MaxRAMPercentage=70`, inventory-service cpus 2 / 640MiB / Serial GC / JAVA_TOOL_OPTIONS `-XX:MaxRAMPercentage=70`, order-service cpus 2 / 640MiB / Serial GC / JAVA_TOOL_OPTIONS `-XX:MaxRAMPercentage=70`, payment-service cpus 2 / 640MiB / Serial GC / JAVA_TOOL_OPTIONS `-XX:MaxRAMPercentage=70`
- MySQL 컨테이너 제한: cpus 4 / 1536m (compose.yaml)
- Tomcat/Hikari 기본값, virtual threads off
- MySQL 격리 수준: REPEATABLE-READ, innodb_lock_wait_timeout: 50s
- Hikari connectionTimeout: 30s (기본값)
- Prometheus 스크레이프 간격: 1s (infra/prometheus/prometheus.yml)
- k6 시작 전 호스트 TIME_WAIT 대기: 0초 (시작 시 223개, 기준 < 500)
- k6 실행 위치: Docker 네트워크 안 컨테이너 (grafana/k6:2.2.0, network e-commerce_default → gateway:8080 직접, cpus 4 / memory 2g)
- kind: warmup

## 실패 사유별 건수
- SOLD_OUT: 400

## k6
- 총 요청 수: 500
- RPS: 335.15
- http_req_duration avg(ms): 498.77
- http_req_duration p95(ms): 768.86
- http_req_duration p99(ms): 864.34
- http_req_duration max(ms): 1011.43
- http_req_failed 비율: 0
- orders_paid: 100
- orders_sold_out: 400
- orders_other: 0
- 테스트 소요 시간(초): 2
