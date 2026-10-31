# 실행 결과 20261031-105010-naive

- RUN_ID: 20261031-105010-naive
- 실행 시각: 2026-10-31T01:50:13Z
- git 커밋: 6ade07d
- 전략: naive
- USERS: 5000
- SALE_QUANTITY: 100
- payment 설정: `{"delayMs":200,"jitterMs":50,"failureRate":0.0}`

## 검증
- saleQuantity: 100
- paid: 423
- failed: 4577
- pending: 0
- finalStock: 0
- oversold: 323
- stockMismatch: -323
- verdict: OVERSOLD

## 실패 사유별 건수
- SOLD_OUT: 4577

## k6
- 총 요청 수: 5000
- RPS: 377.54
- http_req_duration avg(ms): 8068.33
- http_req_duration p95(ms): 12808.41
- http_req_duration p99(ms): 13027.97
- http_req_duration max(ms): 13113.09
- http_req_failed 비율: 0
- orders_paid: 423
- orders_sold_out: 4577
- orders_other: 0
- 테스트 소요 시간(초): 14
