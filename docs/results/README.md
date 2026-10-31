# 선착순 주문 부하 테스트 결과

`scripts/run-test.sh` 가 실행마다 `runs/<RUN_ID>/` 를 만들고 아래 표에 한 줄을 추가한다.

## verdict
- `OVERSOLD`: 판매 수량(paid)이 재고(saleQuantity)보다 많다.
- `INCONCLUSIVE`: 주문이 아직 처리 중(pending > 0)이거나, 재고가 남았는데 판매가 모자라(finalStock > 0 이고 paid < saleQuantity) 실행이 제대로 일어나지 않았다. 결과를 신뢰하지 않는다.
- `OK`: 위에 해당하지 않음.

## 고정 설정
아래 기준선 3회(커밋 6ade07d)와 이후 모든 실행에 공통이며, 이후 실행은 각 report.md 에 같은 블록이 기록된다.
기준선 report.md 의 `git 커밋: 6ade07d` 는 작성자 정보 재작성 전 해시이며, 현재 히스토리의 같은 커밋은 `b3cf43c` 다.
- order-service 클라이언트 connect/read timeout: 2s / 10s
- gateway 하위 호출 read timeout: 30s
- k6 요청 timeout: 60s, executor: per-vu-iterations, VU당 1 iteration
- 컨테이너 제한: 앱 cpus 2 / 640m, MySQL cpus 4 / 1536m
- Tomcat/Hikari 기본값, virtual threads off

## 실행 목록

| run | strategy | users | stock | paid | oversold | verdict | p95(ms) | p99(ms) | rps |
|---|---|---|---|---|---|---|---|---|---|
| 20261031-105010-naive | naive | 5000 | 100 | 423 | 323 | OVERSOLD | 12808.41 | 13027.97 | 377.54 |
| 20261031-105032-naive | naive | 5000 | 100 | 566 | 466 | OVERSOLD | 8793.76 | 9027.14 | 545.66 |
| 20261031-105048-naive | naive | 5000 | 100 | 720 | 620 | OVERSOLD | 7604.1 | 7848.12 | 525.39 |
