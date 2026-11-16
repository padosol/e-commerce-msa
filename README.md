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
| `USERS` | 500 | 주문하는 사용자 수(사용자마다 1번 요청) |
| `ARRIVAL_SECONDS` | 1 | 요청 `USERS` 건이 이 시간(초) 동안 같은 간격으로 도착한다(기본: 500명이 1초 동안, 2ms 마다 1명). `0` 이면 `USERS` 명이 같은 순간에 요청한다 |
| `SALE_QUANTITY` | 100 | 판매 재고 |
| `WARMUP` | 0 | `1` 이면 워밍업 실행으로 기록한다(RUN_ID 끝에 `-warmup`, `meta.json` 의 kind 가 `warmup`, 요약 표에서 제외) |
| `K6_RUNNER` | docker | `docker`: Docker 네트워크 안 k6 컨테이너가 `gateway:8080` 에 직접 연결 / `host`: 호스트 k6 가 `localhost:8080` 으로 연결(1단계 3차까지의 방식) |
| `K6_CPUS` / `K6_MEMORY` | 4 / 2g | k6 컨테이너 자원 한도(빈 값이면 제한 없음) |
| `K6_NETWORK` | e-commerce_default | k6 컨테이너를 붙일 Compose 네트워크. Compose 프로젝트 이름(기본은 저장소 디렉터리 이름) 뒤에 `_default` 가 붙는다 |

예: `USERS=200 SALE_QUANTITY=20 scripts/run-test.sh`. 마지막 줄에 verdict(`OK` / `OVERSOLD` 등)가 출력된다.

부하 조건은 2026-11-03 에 바꿨다. 0단계, 1단계 1~4차, 2단계 GC 실험의 기록은 모두 `USERS=5000 ARRIVAL_SECONDS=0`(5000명이 같은 순간에 요청)이다.
그 조건에서는 연결 5000개가 0.25초 안에 몰려 게이트웨이의 커널 연결 대기열(100칸)이 넘쳤다(`docs/results/stage2-gateway-diagnosis/`).
이 실험의 목표는 동시 주문의 정합성이다. 그래서 현실에 가까운 "1초 동안 500명" 으로 낮췄다. 재고는 그대로 100개다.
예전 조건을 다시 재려면 `USERS=5000 ARRIVAL_SECONDS=0 scripts/run-test.sh` 로 돌린다.

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

인자가 없으면 `naive pessimistic conditional optimistic-r0 optimistic-r1 optimistic-r2 optimistic-r3` 를 순서대로 돈다. 구성마다 inventory-service 를 다시 만들고, 전략이 맞는지 확인하고(다르면 중단), `scripts/smoke.sh` 로 order-service 의 예전 연결을 정리한 뒤 워밍업 1회와 측정 `RUNS`(기본 3)회를 돌린다. 끝나면 구성별 판정 목록을 출력하고 inventory-service 를 `naive` 로 되돌린다. 실행 하나라도 실패하면 즉시 중단하고 `naive` 로 되돌린다. `USERS`, `ARRIVAL_SECONDS`, `SALE_QUANTITY` 는 `run-test.sh` 로 그대로 전달된다.

예상 소요 시간은 구성당 약 5분(워밍업 포함), 전체 7개 구성이면 약 35분이다.

### 판정과 결과 파일

판정은 `OVERSOLD` / `INCONCLUSIVE` / `UNDERSOLD` / `OK` 중 하나이고 정의는 `docs/results/README.md` 에 있다. 실행마다 `runs/<RUN_ID>/` 에 `meta.json`(전략·kind·커밋·부하 조건, 2단계 GC 실험부터 앱별 컨테이너 한도·`JAVA_TOOL_OPTIONS`·GC 종류)과 `causes.json`(락 대기, 풀·스레드 포화, 낙관적 충돌, 타임아웃, 2단계 GC 실험부터 커넥션 획득 대기와 GC 정지 같은 원인 지표)도 남는다.

결과를 구성별 3회 중앙값 표로 모으려면 `scripts/summarize.sh` 를 쓴다. 기본은 지금 부하 조건(`USERS=500 ARRIVAL_SECONDS=1`)으로 잰 실행만 모은다.
예전 조건 기록은 `USERS=5000 ARRIVAL_SECONDS=0` 을 준다. 특정 측정만 모으려면 `FROM` 과 `TO`(RUN_ID 앞부분, `TO` 는 그 값 미만)로 범위를 고른다.

| 측정 | 명령 |
|---|---|
| 1단계 4차 | `USERS=5000 ARRIVAL_SECONDS=0 FROM=20261103-1548 TO=20261103-1600 scripts/summarize.sh` |
| 2단계 GC 실험 | `USERS=5000 ARRIVAL_SECONDS=0 FROM=20261103-1718 TO=20261103-1800 scripts/summarize.sh` |
| 1단계 5차 | `FROM=20261103-2210 TO=20261103-2235 scripts/summarize.sh` |
| 2단계 게이트웨이 메모리 | `FROM=20261103-2235 TO=20261103-2300 scripts/summarize.sh` |
| 2단계 회귀 매트릭스 1차 | `FROM=20261116-2230 TO=20261116-2243 scripts/summarize.sh` |
| 2단계 회귀 매트릭스 진단 재측정 | `FROM=20261116-2243 TO=20261116-2300 scripts/summarize.sh` |
 1단계 결과 해석은 `docs/results/stage1-summary.md`(4차, 5000명 동시 요청)와 `docs/results/stage1-attempt5-summary.md`(5차, 1초 동안 500명)에 있다.

### 2단계: 게이트웨이 메모리

게이트웨이만 메모리 1280m·swap 없음으로 바꿨다(`compose.yaml`). heap(`-Xmx448m`)과 GC(Serial)는 1단계와 같다.
1단계 5차에서 게이트웨이는 640m 한도에 닿아 swap 을 286 MiB 썼고, 메모리를 얼마나 쓰는지 재서 정한 값이다(`docs/results/stage2-gateway-memory.md`).

### 2단계 GC 실험 (5000명 조건, 설정은 반영하지 않음)

5000명 동시 조건에서 order-service 만 메모리 1g·`-XX:+UseG1GC` 로 바꿔 쟀다(`docs/results/stage2-summary.md`, `docs/results/stage2-environment.md`).
1단계 4차에서 order-service 의 커넥션 대기 최댓값 3.74s 가 Serial GC 의 Full GC 정지(3.27s)와 겹쳤기 때문이다.
지금 `compose.yaml` 에는 이 설정을 넣지 않았다. order-service 는 1단계 그대로 640m·Serial GC 다.
이 실험 중 나온 게이트웨이 60초 타임아웃의 원인 조사는 `docs/results/stage2-gateway-diagnosis/` 에 있다. 조사에 쓴 게이트웨이 설정 변경은 진단용 override 파일로만 적용했다.

`causes.json` 에 커넥션 획득 대기·GC 정지가 없는 예전 실행은 `scripts/jvm-metrics.sh docs/results/runs/<RUN_ID>...` 로 같은 값을 Prometheus 에서 다시 계산할 수 있다(그 시각의 Prometheus 데이터가 남아 있을 때만). 1단계 4차 28회의 값은 `docs/results/stage1-attempt4-jvm-metrics.json` 에 있다.

### 검증·분류 도구

- Grafana 새 패널: MySQL 행 락 대기 (회/s), MySQL 행 락 대기 시간 (ms/s), MySQL 현재 락 대기 수, 낙관적 락 충돌·재시도 (회/s), 타임아웃 응답 (회/s)
- 판정 규칙 테스트: `scripts/test-verdict.sh`
- k6 응답 분류 테스트: `k6 run --quiet load-test/k6/classify-test.js`

## 2단계: 이커머스 기본 기능

선착순 실험을 다 마친 뒤 로드맵을 바꿔 일반 쇼핑 흐름(상품 조회, 장바구니, 장바구니 주문, 주문 내역)을 붙였다. 위의 "2단계 GC 실험"·"2단계: 게이트웨이 메모리" 는 로드맵을 바꾸기 전 1단계 브랜치에서 한 추가 실험이다. 이름이 같아도 이 절과는 다르며, 그 절의 제목은 그대로 둔다. Redis 단계는 근거가 생길 때까지 보류한다.

사용자 API (게이트웨이 `localhost:8080`, `/api/users/*` 와 `GET /api/products*` 를 뺀 나머지는 `Authorization: Bearer <accessToken>` 필요):

| 메서드·경로 | 설명 |
|---|---|
| `POST /api/users/signup` | `{email,password,name}` -> 201 `{userId}` |
| `POST /api/users/login` | `{email,password}` -> 200 `{accessToken}` |
| `GET /api/products?page=0&size=20` | 상품 목록 `{products:[{productId,name,price}],page,size,total}` |
| `GET /api/products/{id}` | 상품 상세. 이벤트 상품과 없는 상품은 404 `PRODUCT_NOT_FOUND` |
| `GET /api/cart` | 장바구니 `{items:[{productId,name,unitPrice,quantity,subtotal,available}],total}` |
| `PUT /api/cart/items/{productId}` | `{quantity}`(1~99)로 담기·수량 바꾸기. 살 수 없는 상품은 400 `PRODUCT_UNAVAILABLE`. 서로 다른 상품은 100개까지 |
| `DELETE /api/cart/items/{productId}` | 장바구니에서 빼기 -> 204 |
| `POST /api/cart/checkout` | 장바구니 주문 `{orderId,status,reason}`. 201 `PAID`, 결제 거절 402 `PAYMENT_DECLINED`, 빈 장바구니 400 `CART_EMPTY` |
| `GET /api/orders?page=0&size=20` | 내 주문 목록(최신순) `{orders:[{orderId,status,reason,amount,createdAt,itemCount,firstProductName}],page,size,total}` |
| `GET /api/orders/{id}` | 주문 상세(`items` 포함). 없으면 404 `ORDER_NOT_FOUND` |
| `POST /api/orders` | 1단계 선착순 주문 `{eventId}`. 측정 스크립트가 쓰므로 그대로다 |

`page` 는 0 이상, `size` 는 1~100 이고 범위 밖이면 400 `INVALID_REQUEST` 다.
일반 상품은 `POST /internal/products`(product-service, 게이트웨이 밖)로 만든다. `scripts/lib.sh` 의 `create_product <name> <price>` 가 이 호출을 감싼다.

서비스 안 패키지: 각 서비스는 `com.flashsale.<service>` 아래 `domain`(순수 Java 도메인 클래스), `application`(서비스와 `port.out` 포트), `adapter.in`(웹 컨트롤러)·`adapter.out`(JPA·원격 호출 구현), `config` 로 나눈다. 의존 방향은 ArchUnit 테스트(`ArchitectureTest`)가 지킨다: `domain` 은 Spring·JPA 에 의존하지 않고, `application` 은 `adapter` 를 모른다.

결제 실패율: fake PG 의 실패율 기본값은 10%(`0.1`)다. `PAYMENT_FAILURE_RATE=0.3 docker compose up -d payment-service` 처럼 바꾸거나, 실행 중에는 `PUT /internal/payments/config` 로 바꾼다. 선착순 측정과 `scripts/smoke*.sh` 는 실행 전에 실패율만 0 으로 고정한다(`smoke-shop.sh` 만 끝날 때 원래 설정으로 되돌린다). 그래서 `scripts/run-test.sh`, `run-matrix.sh`, `smoke.sh` 를 돌린 뒤에는 실행 중인 payment-service 의 실패율이 0 으로 남는다. 10% 로 되돌리려면 `source scripts/lib.sh && set_payment_failure_rate 0.1` 을 실행하거나 payment-service 를 다시 만든다. 그 밖의 경로(직접 호출, 화면 확인)에서는 10% 가 그대로 적용되어 결제가 가끔 거절된다.

주의: order-service 의 V2 마이그레이션은 `orders.product_id`·`quantity` 컬럼을 지운다. 그래서 같은 MySQL 컨테이너에 1단계 코드(`feat/flash-sale-stage1`)를 붙이면 Hibernate 검증에서 실패한다. MySQL 은 익명 볼륨을 쓰므로 `docker compose up -d -V mysql` 로 볼륨을 새로 만든다(데이터는 사라지고, 부하 테스트 스크립트가 필요한 것을 다시 만든다).

장바구니 주문의 일괄 차감(`inventory-service`)은 `INVENTORY_STRATEGY` 와 관계없이 항상 조건부 UPDATE 로 한다. 선착순 단건 주문만 위의 재고 전략을 따른다. 결제가 거절되면 차감한 재고를 전부 되돌리고, 성공하면 주문한 줄만 장바구니에서 지운다.

### 사용자 흐름 스모크

```bash
./gradlew bootJar
docker compose up -d --build      # service.Dockerfile 이 미리 빌드한 jar 를 복사하므로 bootJar 가 먼저다
scripts/smoke.sh                  # SMOKE OK (선착순 주문 1건)
scripts/smoke-shop.sh             # SHOP SMOKE OK
```

`scripts/smoke-shop.sh` 는 게이트웨이로 회원가입·로그인, 상품 조회, 장바구니 담기, 결제 거절(재고·장바구니 그대로), 결제 성공(재고 차감·장바구니 비움), 주문 내역 조회를 차례로 확인한다. 실패하면 `SHOP SMOKE FAIL: <단계> <http 코드> <본문 앞 300자>` 를 찍고 exit 1 이다. 매번 새 이메일과 새 상품을 쓰므로 연달아 돌려도 된다. 끝나면 결제 설정을 시작할 때 값으로 되돌린다. 이전 스택이 떠 있어도 `up -d --build` 하면 Flyway 마이그레이션이 기존 볼륨에 적용된다.

호스트의 8080(또는 9090/3000/3306) 포트가 다른 프로그램에 쓰이고 있으면 compose override 파일로 게이트웨이 등의 호스트 포트를 옮기고, 스크립트에는 `GATEWAY_URL`(Prometheus 는 `PROMETHEUS_URL`)을 그 주소로 지정한다. 예: `GATEWAY_URL=http://localhost:18080 scripts/smoke-shop.sh`. override 파일은 저장소에 넣지 않았다.

## 모니터링

- Grafana: http://localhost:3000/d/flash-sale (익명 접근)
- Prometheus: http://localhost:9090

9090 / 3000 포트가 사용 중이면 `PROMETHEUS_PORT` / `GRAFANA_PORT` 로 호스트 포트를 옮길 수 있다(`PROMETHEUS_PORT=19090 GRAFANA_PORT=13000 docker compose up -d`). 이때 스크립트에도 같은 `PROMETHEUS_PORT` 를 export 하거나 `PROMETHEUS_URL` 을 지정해야 한다.

## 결과

`docs/results/` 에 실행마다 `runs/<RUN_ID>/`(k6 요약, 검증, 주문 통계, meta.json, causes.json, report.md)가 쌓이고, `docs/results/README.md` 표에 한 줄씩 추가된다.

0단계 naive 기준선(USERS 5000, 재고 100, 3회): 모두 `OVERSOLD`, 판매 수량 423 / 566 / 720.
