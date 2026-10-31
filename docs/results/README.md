# 선착순 주문 부하 테스트 결과

`scripts/run-test.sh` 가 실행마다 `runs/<RUN_ID>/` 를 만들고 아래 표에 한 줄을 추가한다.

| run | strategy | users | stock | paid | oversold | verdict | p95(ms) | p99(ms) | rps |
|---|---|---|---|---|---|---|---|---|---|
| 20261031-105010-naive | naive | 5000 | 100 | 423 | 323 | OVERSOLD | 12808.41 | 13027.97 | 377.54 |
| 20261031-105032-naive | naive | 5000 | 100 | 566 | 466 | OVERSOLD | 8793.76 | 9027.14 | 545.66 |
| 20261031-105048-naive | naive | 5000 | 100 | 720 | 620 | OVERSOLD | 7604.1 | 7848.12 | 525.39 |
