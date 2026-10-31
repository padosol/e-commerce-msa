# 실행 결과 20261031-105048-naive

- RUN_ID: 20261031-105048-naive
- 실행 시각: 2026-10-31T01:50:49Z
- git 커밋: 6ade07d
- 전략: naive
- USERS: 5000
- SALE_QUANTITY: 100
- payment 설정: `{"delayMs":200,"jitterMs":50,"failureRate":0.0}`

## 검증
- saleQuantity: 100
- paid: 720
- failed: 4280
- pending: 0
- finalStock: 0
- oversold: 620
- stockMismatch: -620
- verdict: OVERSOLD

## 실패 사유별 건수
- SOLD_OUT: 4280

## k6
- 총 요청 수: 5000
- RPS: 525.39
- http_req_duration avg(ms): 4568.44
- http_req_duration p95(ms): 7604.1
- http_req_duration p99(ms): 7848.12
- http_req_duration max(ms): 9252.97
- http_req_failed 비율: 0
- orders_paid: 720
- orders_sold_out: 4280
- orders_other: 0
- 테스트 소요 시간(초): 10
