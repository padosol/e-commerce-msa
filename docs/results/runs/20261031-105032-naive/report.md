# 실행 결과 20261031-105032-naive

- RUN_ID: 20261031-105032-naive
- 실행 시각: 2026-10-31T01:50:33Z
- git 커밋: 6ade07d
- 전략: naive
- USERS: 5000
- SALE_QUANTITY: 100
- payment 설정: `{"delayMs":200,"jitterMs":50,"failureRate":0.0}`

## 검증
- saleQuantity: 100
- paid: 566
- failed: 4434
- pending: 0
- finalStock: 0
- oversold: 466
- stockMismatch: -466
- verdict: OVERSOLD

## 실패 사유별 건수
- SOLD_OUT: 4434

## k6
- 총 요청 수: 5000
- RPS: 545.66
- http_req_duration avg(ms): 5313.82
- http_req_duration p95(ms): 8793.76
- http_req_duration p99(ms): 9027.14
- http_req_duration max(ms): 9090.09
- http_req_failed 비율: 0
- orders_paid: 566
- orders_sold_out: 4434
- orders_other: 0
- 테스트 소요 시간(초): 10
