# 선착순 한정 판매 MSA 0단계 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 초과 판매가 실제로 재현되는 naive 기준 버전의 MSA(서비스 5개 + 게이트웨이)와, 명령 하나로 "초기화 → 시드 → k6 → 검증 → 기록"이 끝나는 측정 환경을 만든다.

**Architecture:** Gradle 멀티 모듈 Spring Boot 서비스 6개를 Docker Compose 로 띄운다. 주문은 Order 서비스가 Product → Inventory → Payment 를 동기 REST 로 차례로 호출해 처리하고, Inventory 의 차감은 설정으로 고르는 `StockStrategy` 뒤에 둔다(0단계는 락 없는 `naive` 하나). Prometheus/Grafana 가 서비스 지표와 k6 remote-write 지표를 함께 보여주고, 셸 스크립트가 실행·검증·결과 기록을 자동화한다.

**Tech Stack:** Java 25, Spring Boot 4.1.2, Spring Cloud 2025.1.4 (Gateway Server WebMVC), Gradle 9.8.2, MySQL 8.4, Flyway, jjwt 0.13.0, Testcontainers 2.x, k6 2.2, Prometheus v3.5.0, Grafana 12.1.0, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-10-29-flash-sale-msa-design.md`

## Global Constraints

- 버전: Java toolchain 25, `org.springframework.boot` 4.1.2, `io.spring.dependency-management` 1.1.7, `spring-cloud-dependencies` 2025.1.4, Gradle wrapper 9.8.2 (Initializr zip 에서 복사).
- 로컬 JDK: Corretto 25 가 `~/Library/Java/JavaVirtualMachines/corretto-25.0.4.1` 에 있다(셸 기본 java 는 17 — Gradle toolchain 이 25 를 찾는다).
- group `com.flashsale`, 기본 패키지 `com.flashsale.<svc>` (`gateway`, `user`, `product`, `inventory`, `order`, `payment`). 모듈명 `gateway`, `user-service`, `product-service`, `inventory-service`, `order-service`, `payment-service`.
- 포트: gateway 8080, user 8081, product 8082, inventory 8083, order 8084, payment 8085, MySQL 3306, Prometheus 9090, Grafana 3000.
- MySQL 8.4 컨테이너 1개, 스키마 `user_db`, `product_db`, `inventory_db`, `order_db`, `payment_db`. 계정 `<name>_svc` / 비밀번호 `<name>_pw` (예: `inventory_svc`/`inventory_pw`), 자기 스키마에만 권한.
- 모든 DB 서비스의 `application.yml` 공통 블록:
  ```yaml
  spring:
    threads.virtual.enabled: false
    datasource:
      url: ${DB_URL:jdbc:mysql://localhost:3306/<schema>}
      username: ${DB_USERNAME:<name>_svc}
      password: ${DB_PASSWORD:<name>_pw}
    jpa:
      open-in-view: false
      hibernate.ddl-auto: validate
      properties.hibernate.jdbc.time_zone: UTC
  server.tomcat.mbeanregistry.enabled: true
  management:
    endpoints.web.exposure.include: health,prometheus
    metrics.distribution.percentiles-histogram.http.server.requests: true
  ```
- 스키마는 Flyway `src/main/resources/db/migration/V1__*.sql`. 시간은 `Instant` ↔ `DATETIME(6)` (UTC).
- DTO 는 Java record. Lombok 사용 안 함. 에러 응답 본문은 `{"code": "...", "message": "..."}`.
- **0단계 금지 목록** (스펙 안티 목표): spring-retry, resilience4j, OpenFeign, JPA `@Version`, `@Lock`/`SELECT ... FOR UPDATE`, 조건부 `UPDATE ... WHERE quantity > 0`, 원격 호출을 감싸는 `@Transactional`, 가상 스레드, 톰캣/Hikari 풀 기본값 변경.
- 서비스 간 HTTP: `RestClient` + `JdkClientHttpRequestFactory` (HTTP/1.1, connect timeout 2s, read timeout 10s). `SimpleClientHttpRequestFactory`(HttpURLConnection 은 POST 를 몰래 재전송함)와 Apache HttpClient 는 쓰지 않는다.
- Payment 기본값: `delay-ms` 200, `jitter-ms` 50, `failure-rate` 0.0.
- JWT: HS256, 비밀키 env `JWT_SECRET` (기본값 `local-dev-secret-change-me-0123456789abcdef`), subject = userId 문자열, TTL `PT2H`.
- k6 기본값: `USERS=5000`, `SALE_QUANTITY=100`.
- 테스트: JUnit 5 + AssertJ + Mockito. DB 테스트는 Initializr 패턴의 `TestcontainersConfiguration` (`org.testcontainers.mysql.MySQLContainer`, 이미지 `mysql:8.4`, `@ServiceConnection`). Spring Boot 4 는 테스트 어노테이션 패키지가 바뀌었다(예: `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`, `org.springframework.test.context.bean.override.mockito.MockitoBean`).
- Docker Desktop VM 메모리 7.7GB 기준 한도: 앱 컨테이너 `cpus: "2"`, `memory: 640m`, JVM `-XX:MaxRAMPercentage=70`; MySQL `cpus: "4"`, `memory: 1536m`; Prometheus 512m; Grafana 256m.
- 커밋 메시지: 한국어, prefix 없음, **트레일러(Co-Authored-By 등) 금지** (사용자 커밋 규칙). 작업은 main 이 아닌 작업 브랜치/워크트리에서.

## Review Focus

1. **숨은 재시도** — 연결이 끊겼을 때 클라이언트가 POST 를 다시 보내면 이중 차감이 초과 판매 원인에 섞인다. 기대: 요청은 한 번만 도달하고 `DownstreamException`. → Task 6 `droppedConnectionIsNotRetried`.
2. **0단계가 우연히 안전해짐** — 누군가 락/버전/조건부 UPDATE 를 넣으면 경쟁 조건이 사라진다. 기대: 동시 차감에서 차감 유실과 초과 판매가 발생. → Task 2 `concurrentDecreasesLoseUpdates`.
3. **원격 호출 중 열린 트랜잭션** — 커넥션 풀 고갈이 결과에 섞인다. 기대: 세 클라이언트 호출 시점에 활성 트랜잭션 없음. → Task 7 `remoteCallsRunOutsideTransactions`.
4. **X-User-Id 위조** — 클라이언트가 보낸 헤더가 그대로 전달되면 사용자 식별이 무의미해진다. 기대: 게이트웨이가 항상 제거하고 JWT subject 로 덮어씀. → Task 8 `clientSuppliedUserIdIsReplaced`, `publicRoutesStripClientUserId`.
5. **이전 실행 데이터가 검증에 섞임** — 기대: 통계는 요청한 행사만 센다. → Task 7 `statsCountOnlyRequestedEvent` (+ run-test 는 매번 새 행사 생성 + 초기화).

---

### Task 1: 빌드 골격과 MySQL

**Files:**
- Create: `settings.gradle`, `build.gradle`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `.gitignore`, `compose.yaml`, `infra/mysql/init/01-schemas.sql`

**Interfaces:**
- Produces: 이후 모듈은 `settings.gradle` 에 `include '<module>'` 한 줄과 자기 `build.gradle`(의존성만)만 추가하면 된다. `compose.yaml` 의 `mysql` 서비스(healthcheck 포함).

- [ ] **Step 1: Gradle wrapper 복사**

```bash
curl -s -o "$TMPDIR/starter.zip" "https://start.spring.io/starter.zip?type=gradle-project&language=java&bootVersion=4.1.2&javaVersion=25&groupId=com.flashsale&artifactId=probe&dependencies=web"
unzip -o -q "$TMPDIR/starter.zip" -d "$TMPDIR/starter"
cp -R "$TMPDIR/starter/gradlew" "$TMPDIR/starter/gradlew.bat" "$TMPDIR/starter/gradle" .
grep distributionUrl gradle/wrapper/gradle-wrapper.properties
```
Expected: `gradle-9.8.2-bin.zip`

- [ ] **Step 2: 루트 빌드 작성**

`settings.gradle`: `rootProject.name = 'e-commerce'` (include 없음).
`build.gradle`:
```groovy
plugins {
    id 'org.springframework.boot' version '4.1.2' apply false
    id 'io.spring.dependency-management' version '1.1.7' apply false
}
subprojects {
    apply plugin: 'java'
    apply plugin: 'org.springframework.boot'
    apply plugin: 'io.spring.dependency-management'
    group = 'com.flashsale'
    version = '0.0.1-SNAPSHOT'
    java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }
    repositories { mavenCentral() }
    dependencyManagement {
        imports { mavenBom 'org.springframework.cloud:spring-cloud-dependencies:2025.1.4' }
    }
    dependencies { testRuntimeOnly 'org.junit.platform:junit-platform-launcher' }
    tasks.named('test') { useJUnitPlatform() }
    tasks.named('bootJar') { archiveFileName = "${project.name}.jar" }
    tasks.named('jar') { enabled = false }
}
```
`.gitignore`: `.gradle/`, `build/`, `.idea/`, `*.iml`, `out/`, `.DS_Store`, `load-test/k6/.data/`.

- [ ] **Step 3: MySQL 초기화 SQL과 compose 작성**

`infra/mysql/init/01-schemas.sql`: 5개 스키마 `CREATE DATABASE`, 각 `CREATE USER '<name>_svc'@'%' IDENTIFIED BY '<name>_pw'`, `GRANT ALL PRIVILEGES ON <schema>.* TO '<name>_svc'@'%'`.
`compose.yaml` 의 `mysql`: 이미지 `mysql:8.4`, `MYSQL_ROOT_PASSWORD: root`, 포트 `3306:3306`, `./infra/mysql/init:/docker-entrypoint-initdb.d:ro`, healthcheck `mysqladmin ping -h 127.0.0.1 -proot` (interval 5s, retries 30), `deploy.resources.limits` cpus "4" / memory 1536m. 이름 있는 볼륨은 두지 않는다(`docker compose down` 이면 깨끗한 DB).

- [ ] **Step 4: 검증**

Run: `./gradlew help` → Expected: `BUILD SUCCESSFUL`
Run: `docker compose up -d mysql && until [ "$(docker inspect -f '{{.State.Health.Status}}' $(docker compose ps -q mysql))" = healthy ]; do sleep 2; done && docker compose exec mysql mysql -uinventory_svc -pinventory_pw -e 'SHOW DATABASES'`
Expected: 목록에 `inventory_db` 는 있고 `order_db` 는 없다.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle build.gradle gradlew gradlew.bat gradle .gitignore compose.yaml infra/mysql
git commit -m "Gradle 멀티 모듈 골격과 MySQL 스키마 초기화 추가"
```

---

### Task 2: inventory-service — naive 차감과 전략 교체 구조

**Files:**
- Modify: `settings.gradle` (`include 'inventory-service'`)
- Create: `inventory-service/build.gradle`
- Create: `inventory-service/src/main/java/com/flashsale/inventory/InventoryApplication.java`
- Create: `.../inventory/domain/Inventory.java`, `.../domain/InventoryRepository.java`
- Create: `.../inventory/stock/StockStrategy.java`, `.../stock/DecreaseResult.java`, `.../stock/NaiveStockStrategy.java`, `.../stock/InventoryNotFoundException.java`
- Create: `.../inventory/api/InventoryController.java`, `.../api/QuantityRequest.java`, `.../api/InventoryResponse.java`, `.../api/DecreaseResponse.java`, `.../api/StrategyResponse.java`, `.../api/ApiExceptionHandler.java`
- Create: `inventory-service/src/main/resources/application.yml`, `.../db/migration/V1__create_inventory.sql`
- Test: `inventory-service/src/test/java/com/flashsale/inventory/TestcontainersConfiguration.java`, `.../stock/NaiveStockStrategyTest.java`, `.../api/InventoryControllerTest.java`

**Interfaces:**
- Consumes: Task 1 루트 빌드, `inventory_db`.
- Produces (HTTP, 포트 8083, 모두 내부용):
  - `PUT /internal/inventory/{productId}` `{"quantity": int>=0}` → 200 `{"productId": long, "quantity": int}` (없으면 생성)
  - `GET /internal/inventory/{productId}` → 200 위와 동일 / 404 `{"code":"INVENTORY_NOT_FOUND"}`
  - `POST /internal/inventory/{productId}/decrease` `{"quantity": int>=1}` → 200 `{"result":"SUCCESS"|"OUT_OF_STOCK"}` / 404
  - `POST /internal/inventory/{productId}/increase` `{"quantity": int>=1}` → 204 / 404
  - `GET /internal/strategy` → 200 `{"strategy":"naive"}`
- Produces (Java): `interface StockStrategy { String name(); DecreaseResult decrease(long productId, int quantity); void increase(long productId, int quantity); }`, `enum DecreaseResult { SUCCESS, OUT_OF_STOCK }`. 설정 `inventory.strategy: ${INVENTORY_STRATEGY:naive}`.

- [ ] **Step 1: 모듈 골격**

`build.gradle` 의존성(Initializr 출력과 동일): `spring-boot-starter-actuator`, `-data-jpa`, `-flyway`, `-validation`, `-webmvc`, `org.flywaydb:flyway-mysql`, runtimeOnly `com.mysql:mysql-connector-j`, `io.micrometer:micrometer-registry-prometheus`; test: `spring-boot-starter-webmvc-test`, `-data-jpa-test`, `spring-boot-testcontainers`, `org.testcontainers:testcontainers-junit-jupiter`, `org.testcontainers:testcontainers-mysql`.
`application.yml`: 포트 8083, `spring.application.name: inventory-service`, 공통 블록(스키마 `inventory_db`, 계정 `inventory_svc`), `inventory.strategy`.
`V1__create_inventory.sql`: `CREATE TABLE inventory (product_id BIGINT PRIMARY KEY, quantity INT NOT NULL)`.
`Inventory` 엔티티(`productId` @Id, `quantity`) — `@Version` 없음. `InventoryRepository extends JpaRepository<Inventory, Long>`.

- [ ] **Step 2: 전략 테스트 작성 (`NaiveStockStrategyTest`, @SpringBootTest + Testcontainers, 클래스에 @Transactional 붙이지 않음, @BeforeEach 에서 `deleteAll`)**

```java
decreaseSubtractsWhenEnough:      재고 5 → decrease(1L,1) == SUCCESS, 남은 수량 4
decreaseReturnsOutOfStock:        재고 0 → decrease(1L,1) == OUT_OF_STOCK, 남은 수량 0
increaseAdds:                     재고 3 → increase(1L,2) → 남은 수량 5
unknownProductThrows:             decrease(999L,1) → InventoryNotFoundException
```
그리고 특성(characterization) 테스트 — 0단계가 "깨져 있음"을 고정한다:
```java
@Test
void concurrentDecreasesLoseUpdates() throws Exception {
    repository.save(new Inventory(1L, 50));
    ExecutorService pool = Executors.newFixedThreadPool(32);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<DecreaseResult>> futures = new ArrayList<>();
    for (int i = 0; i < 200; i++) {
        futures.add(pool.submit(() -> { start.await(); return strategy.decrease(1L, 1); }));
    }
    start.countDown();
    long successes = 0;
    for (Future<DecreaseResult> f : futures) if (f.get() == DecreaseResult.SUCCESS) successes++;
    pool.shutdown();
    int remaining = repository.findById(1L).orElseThrow().getQuantity();
    assertThat(successes + remaining).isGreaterThan(50); // 차감 유실
    assertThat(successes).isGreaterThan(50);             // 초과 판매
}
```

- [ ] **Step 3: 실패 확인**

Run: `./gradlew :inventory-service:test --tests '*NaiveStockStrategyTest'`
Expected: FAIL (컴파일 에러 — `StockStrategy` 없음)

- [ ] **Step 4: `NaiveStockStrategy` 구현**

`@Component @ConditionalOnProperty(name = "inventory.strategy", havingValue = "naive", matchIfMissing = true)`, `name()` = `"naive"`. `decrease`/`increase` 는 각각 `@Transactional`: `findById` → (없으면 `InventoryNotFoundException`) → 애플리케이션에서 수량 비교 → `setQuantity(getQuantity() ∓ quantity)` 후 dirty checking 으로 저장. 락·버전·조건부 쿼리를 쓰지 않는다는 점과 그 이유(0단계 의도된 경쟁 조건)를 클래스 주석에 남긴다.

- [ ] **Step 5: 통과 확인**

Run: `./gradlew :inventory-service:test --tests '*NaiveStockStrategyTest'`
Expected: PASS. `concurrentDecreasesLoseUpdates` 만 실패하면(유실이 안 생김) **sleep 등을 넣어 억지로 만들지 말고 멈춰서 보고**한다.

- [ ] **Step 6: API 테스트 작성 (`InventoryControllerTest`, @SpringBootTest + @AutoConfigureMockMvc + Testcontainers)**

```java
putThenGetReturnsQuantity:         PUT /internal/inventory/1 {quantity:7} → 200; GET → {"productId":1,"quantity":7}
decreaseReturnsResultBody:         재고 1 → decrease 두 번: 둘 다 200, 결과 SUCCESS 다음 OUT_OF_STOCK
increaseReturns204:                재고 1 → increase {quantity:1} → 204, GET quantity 2
unknownProductReturns404WithCode:  GET /internal/inventory/999 → 404, $.code == "INVENTORY_NOT_FOUND"
invalidQuantityReturns400:         decrease {quantity:0} → 400
strategyEndpointReportsNaive:      GET /internal/strategy → {"strategy":"naive"}
```

- [ ] **Step 7: 실패 확인** — Run: `./gradlew :inventory-service:test --tests '*InventoryControllerTest'` → Expected: FAIL (404/컴파일 에러)

- [ ] **Step 8: 컨트롤러·DTO·예외 핸들러 구현** — `QuantityRequest(@Min(1) int quantity)` 는 decrease/increase 용, PUT 은 `@Min(0)` 별도 검증. `StrategyResponse(String strategy)` 는 주입된 `StockStrategy.name()`.

- [ ] **Step 9: 통과 확인** — Run: `./gradlew :inventory-service:test` → Expected: PASS (전체)

- [ ] **Step 10: Commit**

```bash
git add settings.gradle inventory-service
git commit -m "재고 서비스 naive 차감 전략과 내부 API 추가"
```

---

### Task 3: payment-service — 가짜 PG

**Files:**
- Modify: `settings.gradle` (`include 'payment-service'`)
- Create: `payment-service/build.gradle` (Task 2 와 같은 의존성 세트)
- Create: `.../payment/PaymentApplication.java`, `.../domain/Payment.java`, `.../domain/PaymentStatus.java`, `.../domain/PaymentRepository.java`
- Create: `.../payment/simulation/SimulationConfig.java`, `.../simulation/PaymentSimulator.java`, `.../PaymentService.java`
- Create: `.../payment/api/PaymentController.java`, `.../api/PayRequest.java`, `.../api/PayResponse.java`, `.../api/ApiExceptionHandler.java`
- Create: `payment-service/src/main/resources/application.yml`, `.../db/migration/V1__create_payments.sql`
- Test: `.../TestcontainersConfiguration.java`, `.../simulation/PaymentSimulatorTest.java`, `.../api/PaymentControllerTest.java`

**Interfaces:**
- Produces (HTTP, 포트 8085, 내부용):
  - `POST /internal/payments` `{"orderId": long, "userId": long, "amount": long}` → 200 `{"paymentId": long, "status": "APPROVED"|"DECLINED"}`
  - `GET /internal/payments/config` → `{"delayMs": long, "jitterMs": long, "failureRate": double}`
  - `PUT /internal/payments/config` 같은 본문 → 200 (적용된 값) / 400 (`delayMs<0`, `jitterMs<0`, `failureRate∉[0,1]`)
  - `DELETE /internal/payments` → 204 (전체 삭제)
- Produces (Java): `record SimulationConfig(long delayMs, long jitterMs, double failureRate)`; `PaymentSimulator { SimulationConfig current(); void update(SimulationConfig c); Duration nextDelay(); boolean shouldDecline(); }`; `PaymentService.pay(long orderId, long userId, long amount): PaymentResult(long paymentId, PaymentStatus status)`. 설정 `payment.delay-ms: 200`, `payment.jitter-ms: 50`, `payment.failure-rate: 0.0`.

- [ ] **Step 1: 모듈 골격** — 포트 8085, 공통 블록(`payment_db`/`payment_svc`). `V1__create_payments.sql`: `payments(id BIGINT AUTO_INCREMENT PK, order_id BIGINT NOT NULL, user_id BIGINT NOT NULL, amount BIGINT NOT NULL, status VARCHAR(16) NOT NULL, created_at DATETIME(6) NOT NULL)`.

- [ ] **Step 2: 시뮬레이터 단위 테스트 작성 (`PaymentSimulatorTest`)**

```java
delayStaysWithinJitterBounds:   config(200,50,0) → 1000회 nextDelay() 모두 150ms..250ms
delayNeverNegative:             config(10,50,0)  → 1000회 모두 >= 0ms
zeroFailureRateNeverDeclines:   config(0,0,0.0)  → 1000회 shouldDecline() 모두 false
fullFailureRateAlwaysDeclines:  config(0,0,1.0)  → 1000회 모두 true
updateReplacesConfig:           update(config(5,1,0.5)) → current() == config(5,1,0.5)
```

- [ ] **Step 3: 실패 확인** — Run: `./gradlew :payment-service:test --tests '*PaymentSimulatorTest'` → Expected: FAIL (컴파일 에러)

- [ ] **Step 4: `PaymentSimulator` 구현** — `AtomicReference<SimulationConfig>`, 지연 = `delayMs + uniform[-jitterMs, +jitterMs]` (0 미만은 0), 거절 = `ThreadLocalRandom.nextDouble() < failureRate`.

- [ ] **Step 5: 통과 확인** — 같은 명령 → PASS

- [ ] **Step 6: API 테스트 작성 (`PaymentControllerTest`, @SpringBootTest + MockMvc + Testcontainers, @BeforeEach 에서 `simulator.update(new SimulationConfig(0,0,0.0))` 와 `deleteAll`)**

```java
payApprovesAndPersists:            POST → 200, $.status APPROVED, DB 1건 status APPROVED
payDeclinesWhenFailureRateIsOne:   PUT config failureRate 1.0 → POST → DECLINED, DB status DECLINED
payWaitsConfiguredDelay:           PUT config delayMs 300 jitterMs 0 → POST 소요시간 >= 300ms
invalidConfigReturns400:           PUT config failureRate 1.5 → 400
getConfigReturnsCurrent:           PUT (5,1,0.5) → GET 같은 값
deleteClearsPayments:              결제 2건 → DELETE → 204, DB 0건
```

- [ ] **Step 7: 실패 확인** — Run: `./gradlew :payment-service:test --tests '*PaymentControllerTest'` → Expected: FAIL

- [ ] **Step 8: `PaymentService`·컨트롤러 구현** — `pay` 는 트랜잭션 밖에서 `Thread.sleep(nextDelay())` 후 짧은 트랜잭션으로 저장한다.

- [ ] **Step 9: 통과 확인** — Run: `./gradlew :payment-service:test` → PASS

- [ ] **Step 10: Commit** — `git add settings.gradle payment-service && git commit -m "지연과 실패율을 조절하는 가짜 결제 서비스 추가"`

---

### Task 4: product-service — 상품과 선착순 행사

**Files:**
- Modify: `settings.gradle` (`include 'product-service'`)
- Create: `product-service/build.gradle` (Task 2 와 같은 의존성 세트)
- Create: `.../product/ProductApplication.java`, `.../domain/Product.java`, `.../domain/FlashSaleEvent.java`, `.../domain/ProductRepository.java`, `.../domain/FlashSaleEventRepository.java`, `.../EventService.java`
- Create: `.../product/api/EventController.java`, `.../api/CreateEventRequest.java`, `.../api/CreateEventResponse.java`, `.../api/EventResponse.java`, `.../api/ApiExceptionHandler.java`
- Create: `product-service/src/main/resources/application.yml`, `.../db/migration/V1__create_products.sql`
- Test: `.../TestcontainersConfiguration.java`, `.../api/EventControllerTest.java`

**Interfaces:**
- Produces (HTTP, 포트 8082):
  - `POST /internal/events` `{"productName": string, "price": long>=0, "openAt": ISO-8601 instant, "saleQuantity": int>=1}` → 201 `{"eventId": long, "productId": long}` (상품+행사를 한 트랜잭션에서 생성)
  - `GET /api/products/events/{eventId}` → 200 `{"eventId","productId","productName","price","openAt","saleQuantity"}` / 404 `{"code":"EVENT_NOT_FOUND"}`
- 테이블: `products(id AI PK, name VARCHAR(200), price BIGINT)`, `flash_sale_events(id AI PK, product_id BIGINT FK, open_at DATETIME(6), sale_quantity INT)`.

- [ ] **Step 1: 모듈 골격** — 포트 8082, 공통 블록(`product_db`/`product_svc`), 마이그레이션, 엔티티·리포지토리.

- [ ] **Step 2: 테스트 작성 (`EventControllerTest`)**

```java
createThenGetEvent:        POST {productName:"flash", price:10000, openAt:"2026-11-01T10:00:00Z", saleQuantity:100} → 201
                           GET /api/products/events/{eventId} → productId 일치, price 10000, openAt "2026-11-01T10:00:00Z", saleQuantity 100
unknownEventReturns404:    GET /api/products/events/999 → 404, $.code "EVENT_NOT_FOUND"
invalidCreateReturns400:   saleQuantity 0 → 400
```

- [ ] **Step 3: 실패 확인** — Run: `./gradlew :product-service:test` → FAIL
- [ ] **Step 4: `EventService`·컨트롤러 구현**
- [ ] **Step 5: 통과 확인** — 같은 명령 → PASS
- [ ] **Step 6: Commit** — `git add settings.gradle product-service && git commit -m "상품과 선착순 행사 서비스 추가"`

---

### Task 5: user-service — 회원, 로그인, 테스트 회원 대량 발급

**Files:**
- Modify: `settings.gradle` (`include 'user-service'`)
- Create: `user-service/build.gradle` (Task 2 세트 + `org.springframework.security:spring-security-crypto`, `io.jsonwebtoken:jjwt-api:0.13.0`, runtimeOnly `io.jsonwebtoken:jjwt-impl:0.13.0`, `io.jsonwebtoken:jjwt-jackson:0.13.0`. `spring-boot-starter-security` 는 넣지 않는다)
- Create: `.../user/UserApplication.java`, `.../domain/User.java`, `.../domain/UserRepository.java`, `.../auth/JwtIssuer.java`, `.../auth/JwtProperties.java`, `.../UserService.java`, `.../TestUserService.java`
- Create: `.../user/api/UserController.java`, `.../api/TestUserController.java`, DTO record 들, `.../api/ApiExceptionHandler.java`
- Create: `user-service/src/main/resources/application.yml`, `.../db/migration/V1__create_users.sql`
- Test: `.../TestcontainersConfiguration.java`, `.../auth/JwtIssuerTest.java`, `.../api/UserApiTest.java`

**Interfaces:**
- Produces (HTTP, 포트 8081):
  - `POST /api/users/signup` `{"email","password","name"}` → 201 `{"userId"}` / 409 `{"code":"EMAIL_TAKEN"}` / 400
  - `POST /api/users/login` `{"email","password"}` → 200 `{"accessToken"}` / 401 `{"code":"INVALID_CREDENTIALS"}`
  - `POST /internal/test-users` `{"count": 1..50000}` → 200 `[{"userId": long, "accessToken": string}, ...]` — n=1..count 의 `loadtest-{n}@test.local` 회원을 보장(이미 있으면 재사용), n 순서로 반환
- Produces (Java): `JwtIssuer.issue(long userId): String`. 설정 `jwt.secret: ${JWT_SECRET:local-dev-secret-change-me-0123456789abcdef}`, `jwt.ttl: PT2H`. 이 서명 규칙을 Task 8 게이트웨이가 검증한다.
- 테이블: `users(id AI PK, email VARCHAR(255) UNIQUE NOT NULL, password_hash VARCHAR(100) NOT NULL, name VARCHAR(100) NOT NULL, created_at DATETIME(6) NOT NULL)`.

- [ ] **Step 1: 모듈 골격** — 포트 8081, 공통 블록(`user_db`/`user_svc`), 마이그레이션, 엔티티, `BCryptPasswordEncoder` 빈.

- [ ] **Step 2: 테스트 작성**

`JwtIssuerTest` (단위):
```java
issuedTokenCarriesSubjectAndExpiry: issue(42) → 같은 키로 파싱: subject "42", exp - iat == 2시간
```
`UserApiTest` (@SpringBootTest + MockMvc + Testcontainers):
```java
signupThenLoginReturnsToken:     signup → 201 userId; login → 200, 토큰 subject == userId
duplicateEmailReturns409:        같은 email 두 번 → 두 번째 409, $.code "EMAIL_TAKEN"
wrongPasswordReturns401:         → 401, $.code "INVALID_CREDENTIALS"
testUsersAreIdempotent:          count 3 두 번 호출 → 두 응답의 userId 목록 동일, users 테이블 3건
testUsersReturnsTokensInOrder:   count 3 → 3건, 각 토큰 subject == 해당 userId, 첫 항목 email 이 loadtest-1@test.local 인 회원
```

- [ ] **Step 3: 실패 확인** — Run: `./gradlew :user-service:test` → FAIL
- [ ] **Step 4: 구현** — `TestUserService` 는 BCrypt 해시를 호출당 한 번만 계산하고, `JdbcTemplate.batchUpdate("INSERT IGNORE INTO users ...")` 로 넣은 뒤 email 로 id 를 조회한다(5,000건이 수 초 안에 끝나야 함 — 회원마다 BCrypt 를 돌리면 안 됨).
- [ ] **Step 5: 통과 확인** — 같은 명령 → PASS
- [ ] **Step 6: Commit** — `git add settings.gradle user-service && git commit -m "회원 서비스와 JWT 발급, 부하 테스트용 회원 대량 발급 추가"`

---

### Task 6: order-service — 골격과 하위 서비스 클라이언트

**Files:**
- Modify: `settings.gradle` (`include 'order-service'`)
- Create: `order-service/build.gradle` (Task 2 세트)
- Create: `.../order/OrderApplication.java`, `.../order/client/ClientConfig.java`, `.../client/ClientProperties.java`
- Create: `.../order/client/ProductClient.java`, `.../client/InventoryClient.java`, `.../client/PaymentClient.java`
- Create: `.../order/client/EventInfo.java`, `.../client/StockDecreaseResult.java`, `.../client/PaymentOutcome.java`, `.../client/DownstreamException.java`, `.../client/EventNotFoundException.java`
- Create: `order-service/src/main/resources/application.yml`
- Test: `.../TestcontainersConfiguration.java`, `.../client/StubHttpServer.java`, `.../client/ClientsTest.java`

**Interfaces:**
- Consumes: Task 2/3/4 의 HTTP 계약.
- Produces (Java, 패키지 `com.flashsale.order.client`):
  - `record EventInfo(long eventId, long productId, long price, Instant openAt, int saleQuantity)`
  - `ProductClient.getEvent(long eventId): EventInfo` — 404 → `EventNotFoundException`
  - `InventoryClient.decrease(long productId, int quantity): StockDecreaseResult` (`SUCCESS`, `OUT_OF_STOCK`), `InventoryClient.increase(long productId, int quantity): void`
  - `PaymentClient.pay(long orderId, long userId, long amount): PaymentOutcome` (`APPROVED`, `DECLINED`)
  - 그 외 모든 실패(타임아웃, 연결 오류, 5xx, 예상 밖 4xx) → `DownstreamException extends RuntimeException`
  - `static ClientHttpRequestFactory ClientConfig.requestFactory(Duration connectTimeout, Duration readTimeout)` — `JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HTTP_1_1).connectTimeout(..).build())` + `setReadTimeout(..)`
- 설정(`application.yml`, 포트 8084, 공통 블록 `order_db`/`order_svc`): `clients.product-url: ${PRODUCT_SERVICE_URL:http://localhost:8082}`, `clients.inventory-url: ${INVENTORY_SERVICE_URL:http://localhost:8083}`, `clients.payment-url: ${PAYMENT_SERVICE_URL:http://localhost:8085}`, `clients.connect-timeout: 2s`, `clients.read-timeout: 10s`.

- [ ] **Step 1: 모듈 골격** — 앱, 설정, `ClientProperties`, `TestcontainersConfiguration` (Task 7 엔티티 전까지 마이그레이션 없음).

- [ ] **Step 2: 테스트 작성 (`ClientsTest`, Spring 컨텍스트 없이 `ClientConfig.requestFactory` 로 만든 RestClient 사용)**

`StubHttpServer`: `com.sun.net.httpserver.HttpServer` 를 임의 포트로 띄우고 경로별 응답(상태·JSON·지연)을 지정하며, 경로별 도달 횟수와 마지막 요청 본문을 기록한다.
```java
decreaseParsesResult:                 stub {"result":"OUT_OF_STOCK"} → OUT_OF_STOCK, stub 이 받은 본문 {"quantity":1}
payParsesOutcome:                     stub {"paymentId":1,"status":"DECLINED"} → DECLINED
getEventParsesInstant:                stub openAt "2026-11-01T10:00:00Z" → EventInfo.openAt() == Instant.parse(같은 값)
productNotFoundMapsToEventNotFound:   stub 404 → EventNotFoundException
serverErrorMapsToDownstreamException: stub 500 → DownstreamException
readTimeoutMapsToDownstreamException: stub 1초 지연, read timeout 300ms → DownstreamException
droppedConnectionIsNotRetried:        raw ServerSocket 이 연결을 받고 요청을 읽은 뒤 응답 없이 닫음
                                      → pay(...) 가 DownstreamException, 받은 연결 수 == 1
```

- [ ] **Step 3: 실패 확인** — Run: `./gradlew :order-service:test --tests '*ClientsTest'` → FAIL (컴파일 에러)
- [ ] **Step 4: 클라이언트 구현** — `RestClient` 를 base URL 별로 세 개 만든다. `RestClientException` 과 비 2xx 를 위 규칙대로 변환한다. 재시도 관련 코드나 의존성은 넣지 않는다.
- [ ] **Step 5: 통과 확인** — 같은 명령 → PASS
- [ ] **Step 6: Commit** — `git add settings.gradle order-service && git commit -m "주문 서비스 골격과 재시도 없는 하위 서비스 클라이언트 추가"`

---

### Task 7: order-service — 주문 흐름, API, 통계

**Files:**
- Create: `.../order/domain/Order.java`, `.../domain/OrderStatus.java`, `.../domain/FailReason.java`, `.../domain/OrderRepository.java`
- Create: `.../order/application/OrderService.java`, `.../application/OrderWriter.java`, `.../application/OrderResult.java`, `.../application/EventNotOpenException.java`, `.../application/OrderStats.java`, `.../config/ClockConfig.java`
- Create: `.../order/api/OrderController.java`, `.../api/InternalOrderController.java`, `.../api/PlaceOrderRequest.java`, `.../api/OrderResponse.java`, `.../api/ApiExceptionHandler.java`
- Create: `order-service/src/main/resources/db/migration/V1__create_orders.sql`
- Test: `.../application/OrderServiceTest.java`, `.../api/OrderApiTest.java`

**Interfaces:**
- Consumes: Task 6 클라이언트와 예외.
- Produces (Java):
  - `enum OrderStatus { PENDING, PAID, FAILED }`, `enum FailReason { SOLD_OUT, PAYMENT_DECLINED, INVENTORY_ERROR, PAYMENT_ERROR }`
  - `record OrderResult(long orderId, OrderStatus status, FailReason reason)` (reason 은 PAID 일 때 null)
  - `OrderService.placeOrder(long userId, long eventId): OrderResult` — `EventNotFoundException`, `EventNotOpenException` 을 던질 수 있음. **클래스·메서드에 @Transactional 없음.**
  - `OrderWriter`: `Order createPending(long userId, EventInfo event)`, `void markPaid(long orderId)`, `void markFailed(long orderId, FailReason reason)` — 각각 짧은 @Transactional
  - `record OrderStats(long eventId, long paid, long failed, long pending, Map<FailReason, Long> failedByReason)`
- Produces (HTTP, 포트 8084):
  - `POST /api/orders` 헤더 `X-User-Id: long`, 본문 `{"eventId": long}` → 본문 `{"orderId","status","reason"}`:

    | 결과 | 상태 코드 |
    |---|---|
    | PAID | 201 |
    | FAILED / SOLD_OUT | 409 |
    | FAILED / PAYMENT_DECLINED | 402 |
    | FAILED / INVENTORY_ERROR, PAYMENT_ERROR | 502 |
    | 행사 없음 (주문 미생성) | 404 `{"code":"EVENT_NOT_FOUND"}` |
    | 오픈 전 (주문 미생성) | 400 `{"code":"EVENT_NOT_OPEN"}` |
    | X-User-Id 없음 | 401 `{"code":"UNAUTHORIZED"}` |
  - `GET /internal/orders/stats?eventId={id}` → `{"eventId","paid","failed","pending","failedByReason":{"SOLD_OUT":n,...}}` (0건 사유는 생략)
  - `DELETE /internal/orders` → 204 (전체 삭제)
- 테이블: `orders(id AI PK, user_id BIGINT, event_id BIGINT, product_id BIGINT, quantity INT, amount BIGINT, status VARCHAR(16), fail_reason VARCHAR(32) NULL, created_at DATETIME(6), updated_at DATETIME(6))` + 인덱스 `(event_id, status)`.

`placeOrder` 순서(스펙 5장): ① `getEvent` ② `clock.instant() < openAt` 이면 `EventNotOpenException` ③ `createPending` (quantity 1, amount = price) ④ `decrease(productId,1)` — `OUT_OF_STOCK` → `SOLD_OUT`, `DownstreamException` → `INVENTORY_ERROR` (복구 없음) ⑤ `pay(orderId,userId,amount)` — `DECLINED` → `increase(productId,1)` 후 `PAYMENT_DECLINED` (복구 호출이 실패해도 로그만 남기고 같은 결과), `DownstreamException` → `PAYMENT_ERROR` (**복구 없음** — 결과를 알 수 없음) ⑥ `APPROVED` → `markPaid`.

- [ ] **Step 1: 마이그레이션·엔티티·리포지토리·`ClockConfig`(`Clock.systemUTC()`) 작성**

- [ ] **Step 2: 단위 테스트 작성 (`OrderServiceTest`, Mockito, 고정 Clock `2026-11-01T10:00:00Z`, 행사 openAt `09:59:00Z`, productId 7, price 10000)**

```java
paidWhenStockAndPaymentSucceed:      SUCCESS + APPROVED → PAID; markPaid 호출; increase 호출 없음
soldOutSkipsPayment:                 OUT_OF_STOCK → FAILED/SOLD_OUT; paymentClient 상호작용 없음
declinedPaymentRestoresStock:        DECLINED → increase(7,1) 정확히 1회; FAILED/PAYMENT_DECLINED
restoreFailureStillReportsDeclined:  DECLINED + increase 가 DownstreamException → 예외 없이 FAILED/PAYMENT_DECLINED
inventoryErrorDoesNotRestoreOrPay:   decrease 가 DownstreamException → FAILED/INVENTORY_ERROR; increase·pay 호출 없음
paymentErrorDoesNotRestoreStock:     pay 가 DownstreamException → FAILED/PAYMENT_ERROR; increase 호출 없음
notOpenRejectsWithoutOrder:          openAt 10:00:01Z → EventNotOpenException; orderWriter·inventoryClient 상호작용 없음
eventNotFoundPropagates:             getEvent 가 EventNotFoundException → 그대로 전파; orderWriter 상호작용 없음
```

- [ ] **Step 3: 실패 확인** — Run: `./gradlew :order-service:test --tests '*OrderServiceTest'` → FAIL
- [ ] **Step 4: `OrderService`·`OrderWriter` 구현**
- [ ] **Step 5: 통과 확인** — 같은 명령 → PASS

- [ ] **Step 6: API 테스트 작성 (`OrderApiTest`, @SpringBootTest + MockMvc + Testcontainers, 세 클라이언트는 `@MockitoBean`, @BeforeEach 에서 `deleteAll`)**

```java
paidReturns201:                    → 201, $.status PAID; DB 행 status PAID, quantity 1, amount 10000
soldOutReturns409WithReason:       → 409, $.reason SOLD_OUT
declinedReturns402:                → 402, $.reason PAYMENT_DECLINED
downstreamErrorReturns502:         decrease 가 DownstreamException → 502, $.reason INVENTORY_ERROR
notOpenReturns400:                 → 400, $.code EVENT_NOT_OPEN; orders 0건
unknownEventReturns404:            → 404, $.code EVENT_NOT_FOUND
missingUserIdHeaderReturns401:     헤더 없이 → 401, $.code UNAUTHORIZED
remoteCallsRunOutsideTransactions: 세 mock 의 answer 에서 TransactionSynchronizationManager.isActualTransactionActive() 기록 → 모두 false
statsCountOnlyRequestedEvent:      행사 1 에 PAID 2 + SOLD_OUT 1, 행사 2 에 PAID 1
                                   → stats(1) == {paid:2, failed:1, pending:0, failedByReason:{SOLD_OUT:1}}
deleteClearsOrders:                → 204, orders 0건
```

- [ ] **Step 7: 실패 확인** — Run: `./gradlew :order-service:test --tests '*OrderApiTest'` → FAIL
- [ ] **Step 8: 컨트롤러·통계·예외 핸들러 구현** — 통계는 `(event_id, status)` / `(event_id, fail_reason)` 그룹 집계 쿼리.
- [ ] **Step 9: 통과 확인** — Run: `./gradlew :order-service:test` → PASS (전체)
- [ ] **Step 10: Commit** — `git add order-service && git commit -m "주문 흐름(재고→결제→확정)과 주문 통계 API 추가"`

---

### Task 8: gateway — 라우팅과 JWT 검증

**Files:**
- Modify: `settings.gradle` (`include 'gateway'`)
- Create: `gateway/build.gradle` (`spring-boot-starter-actuator`, `org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc`, runtimeOnly `micrometer-registry-prometheus`, jjwt 0.13.0 세 개; test `spring-boot-starter-webmvc-test`. DB 의존성 없음)
- Create: `.../gateway/GatewayApplication.java`, `.../gateway/GatewayRoutes.java`, `.../gateway/JwtVerifier.java`, `.../gateway/GatewayProperties.java`
- Create: `gateway/src/main/resources/application.yml`
- Test: `.../gateway/GatewayRoutingTest.java`, `.../gateway/EchoServer.java`

**Interfaces:**
- Consumes: Task 5 의 JWT 규칙(HS256, `jwt.secret`, subject = userId).
- Produces (HTTP, 포트 8080):
  - `/api/users/**` → user-service (공개), `/api/products/**` → product-service (공개), `/api/orders/**` → order-service (인증 필요)
  - 모든 라우트: 들어온 `X-User-Id` 헤더 제거
  - 인증 라우트: `Authorization: Bearer <jwt>` 가 서명·만료 모두 유효하면 `X-User-Id: <sub>` 를 붙여 전달, 아니면 하위 서비스를 부르지 않고 401 `{"code":"UNAUTHORIZED"}`
  - 그 외 경로(특히 `/internal/**`) → 404
- 설정: `gateway.user-uri: ${USER_SERVICE_URL:http://localhost:8081}`, `gateway.product-uri: ${PRODUCT_SERVICE_URL:http://localhost:8082}`, `gateway.order-uri: ${ORDER_SERVICE_URL:http://localhost:8084}`, `jwt.secret` (Task 5 와 동일 기본값), 하위 서비스 read timeout 30s(게이트웨이 HTTP 클라이언트 설정으로 지정하고 아래 테스트로 확인), management 블록은 공통과 동일.

- [ ] **Step 1: 모듈 골격** — Java DSL `RouterFunction` 으로 라우트를 정의한다(`GatewayRouterFunctions.route`, `HandlerFunctions.http()`, `BeforeFilterFunctions.uri(..)`). 인증은 `/api/orders/**` 라우트에 거는 `HandlerFilterFunction`.

- [ ] **Step 2: 테스트 작성 (`GatewayRoutingTest`, @SpringBootTest(RANDOM_PORT), `EchoServer` = JDK HttpServer 가 받은 경로와 `X-User-Id` 헤더를 JSON 으로 돌려주고 도달 횟수를 셈, `@DynamicPropertySource` 로 세 URI 지정, 토큰은 테스트에서 jjwt 로 같은 키로 생성)**

```java
ordersWithoutTokenReturn401AndSkipDownstream:  POST /api/orders 토큰 없음 → 401, $.code UNAUTHORIZED, order stub 도달 0
ordersWithInvalidSignatureReturn401:           다른 키로 서명 → 401
ordersWithExpiredTokenReturn401:               exp 가 과거 → 401
ordersWithValidTokenForwardUserId:             sub "42" → 200, stub 이 받은 X-User-Id "42", 경로 /api/orders
clientSuppliedUserIdIsReplaced:                X-User-Id 999 + sub 42 토큰 → stub 이 받은 값 "42"
publicRoutesStripClientUserId:                 GET /api/products/events/1 + X-User-Id 999 → stub 이 받은 헤더 없음
internalPathsAreNotRouted:                     GET /internal/inventory/1 → 404
slowDownstreamWithinReadTimeoutSucceeds:       order stub 2초 지연 → 200
```

- [ ] **Step 3: 실패 확인** — Run: `./gradlew :gateway:test` → FAIL
- [ ] **Step 4: 라우트·`JwtVerifier`·인증 필터 구현**
- [ ] **Step 5: 통과 확인** — 같은 명령 → PASS
- [ ] **Step 6: Commit** — `git add settings.gradle gateway && git commit -m "게이트웨이 라우팅과 JWT 검증, X-User-Id 전달 추가"`

---

### Task 9: 컨테이너화와 스모크 테스트

**Files:**
- Create: `infra/service.Dockerfile`, `.dockerignore`, `scripts/lib.sh`, `scripts/smoke.sh`
- Modify: `compose.yaml` (서비스 6개 추가)

**Interfaces:**
- Consumes: Task 2~8 의 모든 HTTP 계약.
- Produces (`scripts/lib.sh`, `source` 해서 쓰는 함수. URL 은 env `GATEWAY_URL`/`USER_URL`/`PRODUCT_URL`/`INVENTORY_URL`/`ORDER_URL`/`PAYMENT_URL` 로 덮어쓰기 가능, 기본 `http://localhost:808x`):
  - `wait_healthy [timeout_seconds=180]` — 6개 `/actuator/health` 가 모두 UP 일 때까지 대기, 시간 초과 시 exit 1
  - `reset_data` — `DELETE /internal/orders`, `DELETE /internal/payments`
  - `create_event <sale_quantity>` — openAt = 현재보다 1초 전(UTC), price 10000, productName `flash-<epoch>` → stdout `"<eventId> <productId>"`
  - `set_stock <product_id> <quantity>`
  - `issue_tokens <count> <out_file>` — `POST /internal/test-users` 응답 JSON 배열을 파일에 저장
  - `current_strategy` — stdout 전략 이름
  - `payment_config` — stdout 결제 설정 JSON
  - `order_stats <event_id>` — stdout 통계 JSON

- [ ] **Step 1: Dockerfile·.dockerignore**

```dockerfile
FROM eclipse-temurin:25-jre
ARG MODULE
WORKDIR /app
COPY ${MODULE}/build/libs/${MODULE}.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```
`.dockerignore`: `*` 다음 줄 `!*/build/libs/*.jar`.

- [ ] **Step 2: compose 서비스 추가** — 각 서비스: `build: {context: ., dockerfile: infra/service.Dockerfile, args: {MODULE: <module>}}`, 포트 `808x:808x`, `DB_URL: jdbc:mysql://mysql:3306/<schema>`, `depends_on: {mysql: {condition: service_healthy}}`(gateway 제외), 한도 cpus "2" / memory 640m. `inventory-service` 에 `INVENTORY_STRATEGY: ${INVENTORY_STRATEGY:-naive}`. `order-service` 에 `PRODUCT_SERVICE_URL: http://product-service:8082` 등 3개. `gateway` 에 `USER_SERVICE_URL`/`PRODUCT_SERVICE_URL`/`ORDER_SERVICE_URL`. `JWT_SECRET` 은 user-service 와 gateway 가 같은 값(YAML anchor).

- [ ] **Step 3: `lib.sh` 와 `smoke.sh` 작성** — `smoke.sh`: `wait_healthy` → `reset_data` → `create_event 1` → `set_stock` → `issue_tokens 1` → 게이트웨이로 `POST /api/orders` → 201·`PAID` 확인 → `order_stats` 의 paid == 1 확인 → `SMOKE OK` 출력(아니면 exit 1). macOS `date`(BSD) 기준으로 작성.

- [ ] **Step 4: 검증**

Run: `./gradlew build && ./gradlew bootJar && docker compose up -d --build && scripts/smoke.sh`
Expected: 모든 테스트 통과, 마지막 줄 `SMOKE OK`.
Run: `docker stats --no-stream --format '{{.Name}} {{.MemLimit}}'` → 앱 컨테이너 한도 640MiB.

- [ ] **Step 5: Commit** — `git add infra/service.Dockerfile .dockerignore compose.yaml scripts && git commit -m "전체 서비스 컨테이너화와 스모크 테스트 스크립트 추가"`

---

### Task 10: 관측 — Prometheus와 Grafana

**Files:**
- Create: `infra/prometheus/prometheus.yml`, `infra/grafana/provisioning/datasources/prometheus.yml`, `infra/grafana/provisioning/dashboards/dashboards.yml`, `infra/grafana/dashboards/flash-sale.json`
- Modify: `compose.yaml` (`prometheus`, `grafana` 추가)

**Interfaces:**
- Produces: Prometheus `http://localhost:9090` (remote-write 수신 `/api/v1/write`), Grafana `http://localhost:3000` 대시보드 uid `flash-sale`. 서비스 지표의 서비스 구분 라벨은 **scrape job 이름**(`job="inventory-service"` 등).

- [ ] **Step 1: Prometheus 설정** — 이미지 `prom/prometheus:v3.5.0`, 인자 `--config.file=/etc/prometheus/prometheus.yml --web.enable-remote-write-receiver`, 한도 512m. 서비스마다 job 하나(`gateway`, `user-service`, …), `metrics_path: /actuator/prometheus`, `scrape_interval: 5s`, 대상 `<service>:<port>`.

- [ ] **Step 2: Grafana 프로비저닝** — 이미지 `grafana/grafana:12.1.0`, `GF_AUTH_ANONYMOUS_ENABLED=true`, `GF_AUTH_ANONYMOUS_ORG_ROLE=Admin`, 한도 256m. 데이터소스 uid `prometheus`. 대시보드 "Flash Sale"(refresh 5s, 최근 15분) 패널:

| 패널 | 쿼리 |
|---|---|
| k6 RPS | `sum(rate(k6_http_reqs_total[10s]))` |
| k6 응답시간 p95/p99 | `k6_http_req_duration_p95`, `k6_http_req_duration_p99` |
| k6 VUs | `k6_vus` |
| 서비스별 요청률 | `sum by (job) (rate(http_server_requests_seconds_count{uri!~"/actuator.*"}[10s]))` |
| 서비스별 p95 | `histogram_quantile(0.95, sum by (job, le) (rate(http_server_requests_seconds_bucket{uri!~"/actuator.*"}[10s])))` |
| Hikari active / pending | `hikaricp_connections_active`, `hikaricp_connections_pending` (by job) |
| Tomcat busy threads | `tomcat_threads_busy_threads` (by job) |
| 프로세스 CPU | `process_cpu_usage` (by job) |

- [ ] **Step 3: 검증**

Run: `docker compose up -d && curl -s localhost:8083/actuator/prometheus | grep -c 'http_server_requests_seconds_bucket'` → Expected: 1 이상 (먼저 아무 요청이나 한 번 보낸 뒤). 같은 방식으로 `tomcat_threads_busy_threads` 존재 확인. 없으면 Spring Boot 4 의 해당 설정 이름을 확인해 고친다.
Run: `curl -s localhost:9090/api/v1/targets | jq '[.data.activeTargets[].health] | unique'` → Expected: `["up"]`
Run: `printf 'import http from "k6/http"; export default function(){ http.get("http://localhost:8080/api/products/events/1"); }' > "$TMPDIR/rw.js" && K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write k6 run -q -i 5 -o experimental-prometheus-rw "$TMPDIR/rw.js" && sleep 6 && curl -s 'localhost:9090/api/v1/query?query=k6_http_reqs_total' | jq '.data.result | length'` → Expected: 1 이상
Run: `curl -s 'localhost:3000/api/search?query=Flash' | jq '.[0].uid'` → Expected: `"flash-sale"`

- [ ] **Step 4: Commit** — `git add infra/prometheus infra/grafana compose.yaml && git commit -m "Prometheus와 Grafana 대시보드 추가"`

---

### Task 11: 부하 시나리오와 실행·검증·기록 자동화

**Files:**
- Create: `load-test/k6/flash-sale.js`, `scripts/verify.sh`, `scripts/run-test.sh`, `docs/results/README.md`

**Interfaces:**
- Consumes: `scripts/lib.sh` (Task 9), Prometheus remote-write (Task 10).
- Produces:
  - `load-test/k6/flash-sale.js` — env `BASE_URL`(기본 `http://localhost:8080`), `EVENT_ID`(필수), `USERS`(기본 5000), `TOKENS_FILE`(필수). 커스텀 Counter `orders_paid`(201), `orders_sold_out`(409), `orders_other`(그 외).
  - `scripts/verify.sh <event_id> <product_id> <sale_quantity>` → stdout JSON `{"saleQuantity","paid","failed","pending","finalStock","oversold","stockMismatch","verdict"}`; `oversold = paid - saleQuantity`, `stockMismatch = (saleQuantity - finalStock) - paid`, `verdict = oversold > 0 ? "OVERSOLD" : "OK"`. 항상 exit 0.
  - `scripts/run-test.sh` — env `USERS`(5000), `SALE_QUANTITY`(100). 결과 디렉터리 `docs/results/runs/<RUN_ID>/` (`RUN_ID = <YYYYmmdd-HHMMSS>-<strategy>`) 에 `k6-summary.json`, `k6-stdout.txt`, `verify.json`, `order-stats.json`, `report.md`, 그리고 `docs/results/README.md` 표에 한 줄 추가.

- [ ] **Step 1: k6 스크립트 작성**
  - `SharedArray` 로 `TOKENS_FILE` 로드, `tokens.length < USERS` 면 init 에서 throw.
  - `options.scenarios.flash_sale = { executor: 'per-vu-iterations', vus: USERS, iterations: 1, maxDuration: '5m' }`, `summaryTrendStats: ['avg','med','p(95)','p(99)','max']`.
  - `http.setResponseCallback(http.expectedStatuses(201, 409))` — 품절은 실패로 세지 않는다.
  - VU 마다 `tokens[__VU - 1]` 로 `POST ${BASE_URL}/api/orders {eventId}` 한 번, 요청 timeout `60s`, 상태별 Counter 증가.

- [ ] **Step 2: `verify.sh` 작성** — `order_stats` 와 `GET /internal/inventory/{productId}` 를 jq 로 합쳐 위 JSON 을 만든다.

- [ ] **Step 3: `run-test.sh` 작성** — `set -euo pipefail`; `ulimit -n` 이 65536 미만이면 올린다; 순서: `wait_healthy` → `current_strategy` 로 RUN_ID 결정 → `reset_data` → `create_event $SALE_QUANTITY` → `set_stock` → `issue_tokens $USERS load-test/k6/.data/tokens.json` → `k6 run --summary-export <dir>/k6-summary.json --tag testid=$RUN_ID -e ...` (`curl -sf localhost:9090/-/ready` 가 되면 `-o experimental-prometheus-rw` 와 `K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write`, `K6_PROMETHEUS_RW_TREND_STATS='p(95),p(99),avg,max'` 추가, 아니면 경고 출력) → `verify.sh` → `order_stats` 저장 → `report.md` 생성 → README 행 추가 → `verdict` 출력.
  `report.md` 항목: RUN_ID, 실행 시각, git 커밋(`git rev-parse --short HEAD`, 작업 트리가 더러우면 `-dirty`), 전략, USERS, SALE_QUANTITY, `payment_config` JSON, verify 전체 값, 실패 사유별 건수, k6 총 요청 수·RPS·`http_req_duration` avg/p95/p99/max(ms)·`http_req_failed` 비율·`orders_*` 세 Counter·테스트 소요 시간. (jq 경로는 처음 생성된 `k6-summary.json` 을 열어 실제 키로 맞춘다.)

- [ ] **Step 4: `docs/results/README.md` 작성** — 한 줄 소개와 표 머리글: `| run | strategy | users | stock | paid | oversold | verdict | p95(ms) | p99(ms) | rps |`

- [ ] **Step 5: 검증 (작은 규모)**

Run: `USERS=200 SALE_QUANTITY=20 scripts/run-test.sh`
Expected: 마지막 줄에 verdict 출력; `report.md` 에 `null`·빈 값 없음; README 표에 한 줄 추가; Grafana "Flash Sale" 대시보드의 k6 패널에 값이 찍힘.
그다음 이 시험 실행의 디렉터리와 README 행은 지운다(기준 결과는 Task 12 에서만 남긴다).

- [ ] **Step 6: Commit** — `git add load-test scripts docs/results/README.md && git commit -m "k6 선착순 시나리오와 실행·검증·결과 기록 스크립트 추가"`

---

### Task 12: 0단계 기준선 측정과 실행 안내

**Files:**
- Create: `README.md`, `docs/results/runs/<RUN_ID>/*` (3회분)
- Modify: `docs/results/README.md` (3행)

- [ ] **Step 1: 깨끗한 환경에서 기동** — Run: `docker compose down && ./gradlew bootJar && docker compose up -d --build && scripts/smoke.sh` → Expected: `SMOKE OK`

- [ ] **Step 2: 기본값으로 3회 실행** — Run: `scripts/run-test.sh` 를 3번 (USERS 5000, SALE_QUANTITY 100)
Expected: 3회 모두 `verdict: OVERSOLD`. 한 번이라도 `OK` 이면 **코드나 설정을 바꾸지 말고 멈춰서** 세 실행의 숫자와 함께 사용자에게 보고한다. 대량 연결 오류(`orders_other` 가 대부분)로 측정이 안 되면 그것도 숫자와 함께 보고한다.

- [ ] **Step 3: Grafana 확인** — `http://localhost:3000/d/flash-sale` 에서 3회 실행 구간의 k6 패널과 서비스 패널에 값이 보이는지 확인한다.

- [ ] **Step 4: 루트 `README.md` 작성** — 목적(스펙 1장 한 문단), 준비물(JDK 25, Docker Desktop 메모리 8GB 이상, k6), 명령(`./gradlew bootJar`, `docker compose up -d --build`, `scripts/smoke.sh`, `scripts/run-test.sh` 와 env), 전략 교체(`INVENTORY_STRATEGY=<name> docker compose up -d inventory-service`), Grafana 주소, 결과 위치(`docs/results/`).

- [ ] **Step 5: Commit** — `git add README.md docs/results && git commit -m "0단계 naive 기준선 측정 결과와 실행 안내 추가"`
