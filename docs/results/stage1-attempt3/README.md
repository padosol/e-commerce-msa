# 1단계 3차 측정 기록 (보관)

3차 측정은 2026-11-03 14:36~14:58 에 돌렸다. 다른 프로젝트 컨테이너를 멈추고 Docker Desktop 을 재시작한 깨끗한 환경이었다(`stage1-environment.md`). k6 는 2차까지와 같이 호스트(macOS)에서 돌렸다.
naive 외 6개 구성의 측정 18회 중 4회(비관적 락, 조건부 UPDATE, 낙관적 락 r0, r3)와 워밍업 2회가 INCONCLUSIVE(`order-total`, `k6-unclassified`)였다. 판매(paid)는 모두 100이라 초과 판매는 없었다.
오류는 k6 가 연결 5000개를 동시에 여는 첫 몇 초에 몰렸다(`dial: i/o timeout` 또는 `connection reset by peer`). TIME_WAIT 수(약 30)나 실행 간격(4~97초)과는 무관했다.
원인은 측정 경로다. 호스트의 `localhost:8080` 은 Docker Desktop 백엔드 프로세스가 받는다(macOS 대기열 `kern.ipc.somaxconn` 128). 백엔드는 VM 안 게이트웨이로 연결을 하나 더 맺어 데이터를 옮긴다. 이 중계 계층이 동시 연결 폭주를 감당하지 못했다. 깨끗한 환경에서도 같아서, 환경 탓이 아니라 측정 방식의 문제로 판단했다.
2026-11-03 사용자 결정: k6 를 Docker 네트워크 안의 컨테이너로 옮겨(`gateway:8080` 에 직접 연결) 7개 구성 전체를 다시 잰다(4차). 이 기록은 비교용으로 보관한다.

| run | kind | strategy | users | stock | paid | oversold | undersold | finalStock | conflicts | timeouts | retries | verdict | p95(ms) | p99(ms) | rps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 20261103-143647-naive-warmup | warmup | naive | 5000 | 100 | 465 | 365 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 12883.62 | 13170.66 | 372.66 |
| 20261103-143708-naive | measure | naive | 5000 | 100 | 548 | 448 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 9691.67 | 10049.51 | 492.46 |
| 20261103-143750-naive | measure | naive | 5000 | 100 | 617 | 517 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 8990.14 | 9135.03 | 535.46 |
| 20261103-143832-naive | measure | naive | 5000 | 100 | 604 | 504 | 0 | 0 | 0 | 0 | 0 | OVERSOLD | 5104.4 | 5193.19 | 877.21 |
| 20261103-143915-pessimistic-warmup | warmup | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 7342.35 | 9399.26 | 521.64 |
| 20261103-143951-pessimistic | measure | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 6251.86 | 6414.39 | 762.69 |
| 20261103-144029-pessimistic | measure | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 4592.17 | 4743.44 | 687.54 |
| 20261103-144108-pessimistic | measure | pessimistic | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | INCONCLUSIVE | 5364.78 | 5622.02 | 481.66 |
| 20261103-144256-conditional-warmup | warmup | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | INCONCLUSIVE | 3332.98 | 3900.54 | 629.89 |
| 20261103-144408-conditional | measure | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 6939.95 | 7088.57 | 537.58 |
| 20261103-144421-conditional | measure | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | OK | 4744.12 | 4815.23 | 1004.89 |
| 20261103-144459-conditional | measure | conditional | 5000 | 100 | 100 | 0 | 0 | 0 | 0 | 0 | 0 | INCONCLUSIVE | 7211.4 | 14990.7 | 233.44 |
| 20261103-144657-optimistic-r0-warmup | warmup | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 747 | 0 | 0 | OK | 4310.61 | 4417.26 | 1043.97 |
| 20261103-144706-optimistic-r0 | measure | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 518 | 0 | 0 | OK | 5088 | 5323.78 | 860.92 |
| 20261103-144744-optimistic-r0 | measure | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 473 | 0 | 0 | OK | 4415.12 | 6602.81 | 530.45 |
| 20261103-144826-optimistic-r0 | measure | optimistic-r0 | 5000 | 100 | 100 | 0 | 0 | 0 | 442 | 0 | 0 | INCONCLUSIVE | 7113.96 | 9250.44 | 533.58 |
| 20261103-145012-optimistic-r1-warmup | warmup | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 362 | 0 | 439 | INCONCLUSIVE | 3169.11 | 3297.09 | 634 |
| 20261103-145123-optimistic-r1 | measure | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 409 | 0 | 471 | OK | 5100.11 | 5449.43 | 871.7 |
| 20261103-145134-optimistic-r1 | measure | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 369 | 0 | 435 | OK | 4396.69 | 4582.35 | 1056.18 |
| 20261103-145210-optimistic-r1 | measure | optimistic-r1 | 5000 | 100 | 100 | 0 | 0 | 0 | 338 | 0 | 400 | OK | 4427.95 | 4550.27 | 1064.46 |
| 20261103-145252-optimistic-r2-warmup | warmup | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 225 | 0 | 583 | OK | 4445.67 | 4577.82 | 1054.31 |
| 20261103-145323-optimistic-r2 | measure | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 245 | 0 | 604 | OK | 4229.26 | 4339.87 | 1100.76 |
| 20261103-145359-optimistic-r2 | measure | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 257 | 0 | 626 | OK | 4668.76 | 4878.04 | 996.6 |
| 20261103-145436-optimistic-r2 | measure | optimistic-r2 | 5000 | 100 | 100 | 0 | 0 | 0 | 262 | 0 | 627 | OK | 4768.77 | 4879.96 | 989.76 |
| 20261103-145518-optimistic-r3-warmup | warmup | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 150 | 0 | 619 | OK | 4448.28 | 9227.14 | 530.75 |
| 20261103-145553-optimistic-r3 | measure | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 170 | 0 | 676 | INCONCLUSIVE | 7132.97 | 9295.17 | 530.09 |
| 20261103-145734-optimistic-r3 | measure | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 186 | 0 | 714 | OK | 3995.95 | 4120.82 | 1145.99 |
| 20261103-145743-optimistic-r3 | measure | optimistic-r3 | 5000 | 100 | 100 | 0 | 0 | 0 | 173 | 0 | 685 | OK | 5617.02 | 5831.01 | 830.73 |
