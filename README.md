# 선착순 한정 판매 MSA 부하 테스트

실제 배포 없이 로컬에서 MSA 이커머스를 만들고, k6 로 선착순 한정 판매 시나리오를 돌리면서 문제를 직접 재현하고 단계적으로 고쳐 나가는 실험·학습 프로젝트다. 실패 기준은 하나, **재고보다 많이 팔리면 실패**다. 0단계는 일부러 순진하게(naive) 구현해 초과 판매가 실제로 터지는 것을 확인하고, 이후 단계마다 해결책을 하나씩 적용해 같은 시나리오로 다시 측정·비교한다. 한 번에 한 문제만 다룬다.

## 준비물

- JDK 25
- Docker Desktop (VM 메모리 8GB 이상)
- jq, curl
- k6 (호스트용, 선택): 측정은 기본으로 k6 컨테이너(`grafana/k6:2.2.0`)로 돌린다. 호스트 k6 는 `K6_RUNNER=host` 와 응답 분류 테스트에만 쓴다

## 실행

```bash
./gradlew bootJar                 # 6개 서비스 jar 빌드
docker compose up -d --build      # 서비스 + MySQL + Prometheus + Grafana 기동
scripts/smoke.sh                  # 기동 확인, 마지막 줄이 SMOKE OK 여야 한다
scripts/run-test.sh               # 초기화 -> 준비 -> k6 -> 검증 -> 결과 기록
```

`scripts/run-test.sh` 환경 변수:

| env | 기본값 | 의미 |
|---|---|---|
| `USERS` | 5000 | 동시에 주문하는 사용자 수 |
| `SALE_QUANTITY` | 100 | 판매 재고 |
| `WARMUP` | 0 | `1` 이면 워밍업 실행으로 기록한다(RUN_ID 끝에 `-warmup`, `meta.json` 의 kind 가 `warmup`, 요약 표에서 제외) |
| `K6_RUNNER` | docker | `docker`: Docker 네트워크 안 k6 컨테이너가 `gateway:8080` 에 직접 연결 / `host`: 호스트 k6 가 `localhost:8080` 으로 연결(1단계 3차까지의 방식) |
| `K6_CPUS` / `K6_MEMORY` | 4 / 2g | k6 컨테이너 자원 한도(빈 값이면 제한 없음) |
| `K6_NETWORK` | e-commerce_default | k6 컨테이너를 붙일 Compose 네트워크. Compose 프로젝트 이름(기본은 저장소 디렉터리 이름) 뒤에 `_default` 가 붙는다 |

예: `USERS=200 SALE_QUANTITY=20 scripts/run-test.sh`. 마지막 줄에 verdict(`OK` / `OVERSOLD` 등)가 출력된다.

k6 를 컨테이너로 돌리는 이유: macOS 의 `localhost:8080` 은 Docker Desktop 백엔드 프로세스가 받아 VM 안 게이트웨이로 연결을 한 번 더 맺어 중계한다. 연결 5000개가 한꺼번에 몰리면 이 중계 계층에서 일부가 실패해(`dial: i/o timeout`, `connection reset by peer`) 요청이 서비스에 닿지 않는다(1단계 3차 기록 참고). 같은 Docker 네트워크 안에서는 이 중계를 거치지 않는다.

`run-test.sh` 는 k6 를 시작하기 전에 호스트의 TIME_WAIT 소켓이 500개 미만으로 줄 때까지(최대 120초) 기다린다. 호스트 k6 로 5000 연결짜리 실행을 연달아 돌리면 앞 실행의 소켓이 남아 연결 단계에서 실패하기 때문이다(컨테이너 실행에서는 영향이 거의 없다).

0단계 스택이 이미 떠 있는 상태에서 1단계로 올라왔다면 먼저 `docker compose down` 으로 내린 뒤 다시 `up` 해야 한다. MySQL exporter 용 사용자는 MySQL 컨테이너를 새로 만들 때만 생성되므로, 그렇지 않으면 exporter 가 실패해 Grafana 패널 11~13 이 비고 `maxCurrentWaits` 가 `n/a` 가 된다.

종료는 `docker compose down`.

## 재고 전략 교체

재빌드는 필요 없다. 환경변수를 바꾼 뒤 inventory-service 만 다시 만들면 된다.

```bash
INVENTORY_STRATEGY=<name> INVENTORY_OPTIMISTIC_MAX_RETRIES=<0~3> docker compose up -d inventory-service
```

- `INVENTORY_STRATEGY` 유효한 이름: `naive`(기본), `pessimistic`, `conditional`, `optimistic`
- 주의: `INVENTORY_STRATEGY` 없이 `docker compose up -d` 만 실행하면 inventory-service 가 기본값 `naive` 로 다시 만들어진다.
- `INVENTORY_OPTIMISTIC_MAX_RETRIES` (0~3, 기본 0): `optimistic` 일 때만 쓰는 재시도 횟수
- 현재 전략: `curl -s localhost:8083/internal/strategy`. 결과의 RUN_ID 와 표의 strategy 열에는 `naive`, `pessimistic`, `conditional`, `optimistic-r0`~`optimistic-r3` 로 기록된다.
- `up` 직후 첫 실행은 콜드 스타트라 느리다(기준선 1회차 p95 12.8s, 이후 8.8s / 7.6s). 그래서 매트릭스는 구성마다 워밍업 1회를 먼저 돌린다(README 표의 kind 열이 `warmup`).

### 매트릭스 실행

```bash
scripts/run-matrix.sh                                  # 7개 구성 x (워밍업 1 + 측정 3)
RUNS=1 USERS=200 SALE_QUANTITY=20 scripts/run-matrix.sh conditional optimistic-r1   # 작은 규모 확인
```

인자가 없으면 `naive pessimistic conditional optimistic-r0 optimistic-r1 optimistic-r2 optimistic-r3` 를 순서대로 돈다. 구성마다 inventory-service 를 다시 만들고, 전략이 맞는지 확인하고(다르면 중단), `scripts/smoke.sh` 로 order-service 의 예전 연결을 정리한 뒤 워밍업 1회와 측정 `RUNS`(기본 3)회를 돌린다. 끝나면 구성별 판정 목록을 출력하고 inventory-service 를 `naive` 로 되돌린다. 실행 하나라도 실패하면 즉시 중단하고 `naive` 로 되돌린다. `USERS`(5000), `SALE_QUANTITY`(100) 는 `run-test.sh` 로 그대로 전달된다.

예상 소요 시간은 구성당 약 5분(워밍업 포함), 전체 7개 구성이면 약 35분이다.

### 판정과 결과 파일

판정은 `OVERSOLD` / `INCONCLUSIVE` / `UNDERSOLD` / `OK` 중 하나이고 정의는 `docs/results/README.md` 에 있다. 실행마다 `runs/<RUN_ID>/` 에 `meta.json`(전략·kind·커밋, 2단계부터 앱별 컨테이너 한도·`JAVA_TOOL_OPTIONS`·GC 종류 등)과 `causes.json`(락 대기, 풀·스레드 포화, 낙관적 충돌, 타임아웃, 2단계부터 커넥션 획득 대기와 GC 정지 같은 원인 지표)도 남는다.

결과를 구성별 3회 중앙값 표로 모으려면 `scripts/summarize.sh` 를 쓴다. 특정 측정만 모으려면 `FROM=<RUN_ID 앞부분>` 으로 그 이후 실행만 고른다(예: `FROM=20261104 scripts/summarize.sh`). 1단계 결과 해석은 `docs/results/stage1-summary.md` 에 있다.

`causes.json` 에 커넥션 획득 대기·GC 정지가 없는 예전 실행은 `scripts/jvm-metrics.sh docs/results/runs/<RUN_ID>...` 로 같은 값을 Prometheus 에서 다시 계산할 수 있다(그 시각의 Prometheus 데이터가 남아 있을 때만).

### 2단계: order-service GC

order-service 만 컨테이너 메모리 1g, `-XX:+UseG1GC` 로 바꿨다(`compose.yaml`). 나머지 앱은 640m 그대로라 JVM 이 Serial GC 를 고른다(메모리가 약 1.8GB 미만이면 Serial). 1단계 4차 측정에서 order-service 의 커넥션 대기 최댓값 3.74s 가 Serial GC 의 Full GC 정지(3.27s)와 겹쳤기 때문이다.

### 검증·분류 도구

- Grafana 새 패널: MySQL 행 락 대기 (회/s), MySQL 행 락 대기 시간 (ms/s), MySQL 현재 락 대기 수, 낙관적 락 충돌·재시도 (회/s), 타임아웃 응답 (회/s)
- 판정 규칙 테스트: `scripts/test-verdict.sh`
- k6 응답 분류 테스트: `k6 run --quiet load-test/k6/classify-test.js`

## 모니터링

- Grafana: http://localhost:3000/d/flash-sale (익명 접근)
- Prometheus: http://localhost:9090

9090 / 3000 포트가 사용 중이면 `PROMETHEUS_PORT` / `GRAFANA_PORT` 로 호스트 포트를 옮길 수 있다(`PROMETHEUS_PORT=19090 GRAFANA_PORT=13000 docker compose up -d`). 이때 스크립트에도 같은 `PROMETHEUS_PORT` 를 export 하거나 `PROMETHEUS_URL` 을 지정해야 한다.

## 결과

`docs/results/` 에 실행마다 `runs/<RUN_ID>/`(k6 요약, 검증, 주문 통계, meta.json, causes.json, report.md)가 쌓이고, `docs/results/README.md` 표에 한 줄씩 추가된다.

0단계 naive 기준선(USERS 5000, 재고 100, 3회): 모두 `OVERSOLD`, 판매 수량 423 / 566 / 720.
