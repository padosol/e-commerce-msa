# 선착순 한정 판매 MSA 1단계 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 재고 차감 전략 `pessimistic`·`conditional`·`optimistic`(재시도 0~3)을 추가하고, 판정·실패 분류·원인 지표 기록을 넓혀, 7개 구성을 같은 시나리오로 측정한 결과와 1단계 요약을 남긴다.

**Architecture:** 새 전략은 inventory-service 의 `StockStrategy` 구현체로 추가하고 `INVENTORY_STRATEGY` / `INVENTORY_OPTIMISTIC_MAX_RETRIES` 로 고른다(한 jar 에 전부 포함). order-service 는 타임아웃·충돌·주문 생성 전 실패를 사유별로 나눠 기록하고, 게이트웨이는 하위 타임아웃을 504 로 돌려준다. k6 는 응답을 7종으로 분류하고, `verify.sh`·`verdict.jq` 가 4가지 판정을 내리며, `run-test.sh` 가 MySQL·Prometheus·서비스 통계에서 원인 지표를 모아 실행마다 기록한다.

**Tech Stack:** 0단계와 같음(Java 25, Spring Boot 4.1.2, Spring Cloud 2025.1.4, Gradle 9.8.2, MySQL 8.4, Flyway, Testcontainers 2.x, k6 2.2, Prometheus v3.5.0, Grafana 12.1.0) + `prom/mysqld-exporter:v0.16.0`.

**Spec:** `docs/superpowers/specs/2026-11-01-flash-sale-stage1-design.md` (0단계 스펙 `2026-10-29-flash-sale-msa-design.md` 위에 쌓인다)

## Global Constraints

- 0단계 계획(`docs/superpowers/plans/2026-10-29-flash-sale-stage0.md`)의 Global Constraints 중 버전·포트·스키마·계정·DTO(record, Lombok 없음)·에러 본문 `{"code","message"}`·HTTP 클라이언트(`RestClient` + `JdkClientHttpRequestFactory`, connect 2s / read 10s)·테스트 패턴·컨테이너 한도·커밋 규칙은 그대로 적용한다.
- 브랜치 `feat/flash-sale-stage1`. main 머지와 PR 없음.
- 커밋 메시지: 한국어, prefix 없음, **트레일러(Co-Authored-By 등) 금지**.
- **1단계 금지 목록**: 공용 `Inventory` 엔티티의 JPA `@Version`, spring-retry·resilience4j·OpenFeign, 서비스 간 HTTP 재시도, 원격 호출을 감싸는 `@Transactional`, 가상 스레드, 타임아웃·Tomcat·Hikari·MySQL 설정 변경(격리 수준, `innodb_lock_wait_timeout` 포함). `SELECT ... FOR UPDATE`·조건부 UPDATE·`version` 컬럼은 **새 전략 클래스 안에서만** 쓴다.
- `NaiveStockStrategy` 와 `NaiveStockStrategyTest` 는 수정하지 않는다. `concurrentDecreasesLoseUpdates` 가 계속 통과해야 한다.
- 고정 설정(스펙 §2·§6): order → product/inventory/payment connect 2s / read 10s, gateway → order read 30s, k6 요청 timeout 60s, 앱 cpus 2 / 640m, MySQL cpus 4 / 1536m, Tomcat/Hikari 기본값(Hikari `connectionTimeout` 30s), virtual threads off, MySQL REPEATABLE READ, `innodb_lock_wait_timeout` 50s.
- 전략 설정값(`inventory.strategy`): `naive` | `pessimistic` | `conditional` | `optimistic`. 보고 이름(`GET /internal/strategy`): `naive`, `pessimistic`, `conditional`, `optimistic-r0` ~ `optimistic-r3`.
- 재시도 설정: 속성 `inventory.optimistic.max-retries`, env `INVENTORY_OPTIMISTIC_MAX_RETRIES`, 기본 0, 허용 0~3(범위 밖이면 기동 실패).
- 낙관적 락 재시도: 대기 없이 즉시, 한 요청의 모든 시도는 커넥션 하나로, 시도마다 autocommit 문장으로 새로 읽는다. `@Transactional` 금지.
- `FailReason` (정확히 이 10개): `SOLD_OUT`, `STOCK_CONFLICT`, `PAYMENT_DECLINED`, `INVENTORY_TIMEOUT`, `INVENTORY_ERROR`, `PAYMENT_TIMEOUT`, `PAYMENT_ERROR`, `PRODUCT_TIMEOUT`, `PRODUCT_ERROR`, `ORDER_DB_TIMEOUT`.
- 특성 사유(판정에서 허용): `SOLD_OUT`, `STOCK_CONFLICT`, `INVENTORY_TIMEOUT`, `PAYMENT_TIMEOUT`, `PRODUCT_TIMEOUT`, `ORDER_DB_TIMEOUT`. 나머지는 비특성.
- 주문 응답 상태: `SOLD_OUT`·`STOCK_CONFLICT` 409, `PAYMENT_DECLINED` 402, `*_TIMEOUT` 504, `*_ERROR` 502, 게이트웨이 하위 타임아웃 504 `{"code":"GATEWAY_TIMEOUT"}`.
- 판정 문자열: `OVERSOLD`, `INCONCLUSIVE`, `UNDERSOLD`, `OK`.
- 관측 설정: Prometheus 스크레이프 간격 1s(0단계 5s, 관측 설정이라 고정 설정 아님 — 결과에 기록). mysqld-exporter 한도 cpus 0.5 / 64m.
- 측정 기준: `USERS=5000`, `SALE_QUANTITY=100`, 워밍업도 같은 규모.

## Review Focus

1. **낙관적 재시도가 커넥션을 풀에 돌려줌** — 시도 사이에 커넥션을 반납하면 풀 대기가 숨은 백오프가 된다. 기대: 강제 충돌 1회가 있는 차감 한 번에 `getConnection()` 1회. → Task 2 `retriesReuseOneConnection`.
2. **재시도가 같은 스냅샷을 다시 읽음** — 트랜잭션이나 JPA 캐시 안에서 재조회하면 재시도가 영원히 충돌한다. 기대: 강제 충돌 뒤 재시도가 새 값을 읽고 성공. → Task 2 `forcedConflictIsRetriedAndSucceeds`.
3. **주문 생성 전 실패 카운터가 실행·행사 사이에 샘** — 이전 실행의 실패가 다음 판정에 섞이면 판정이 틀린다. 기대: 행사별로 세고 `DELETE /internal/orders` 가 초기화. → Task 4 `preOrderFailuresCountOnlyRequestedEvent`, `deleteResetsPreOrderFailures`.
4. **초과 판매가 INCONCLUSIVE 에 가려짐** — pending 이 남았어도 PAID 가 이미 재고를 넘으면 초과 판매가 확정이다(pending 은 PAID 를 늘릴 뿐). 기대: `OVERSOLD`. → Task 7 `oversoldWinsOverPending`.
5. **k6 연결 오류를 타임아웃으로 셈** — status 0 에는 연결 오류도 섞인다. 기대: 오류 코드 1050(요청 타임아웃)만 `client_timeout`, 나머지는 `other`. → Task 7 classify 테스트 `status0NonTimeoutIsOther`.

---

### Task 1: inventory-service — `conditional`·`pessimistic` 전략과 공통 계약 테스트

**Files:**
- Create: `inventory-service/src/main/java/com/flashsale/inventory/stock/ConditionalStockStrategy.java`
- Create: `inventory-service/src/main/java/com/flashsale/inventory/stock/PessimisticStockStrategy.java`
- Modify: `inventory-service/build.gradle` (`JdbcTemplate` 빈이 없을 때만 `implementation 'org.springframework.boot:spring-boot-starter-jdbc'`)
- Test: `inventory-service/src/test/java/com/flashsale/inventory/stock/StockStrategyContract.java` (abstract)
- Test: `.../stock/ConditionalStockStrategyTest.java`, `.../stock/PessimisticStockStrategyTest.java`

**Interfaces:**
- Consumes: `StockStrategy` (`String name()`, `DecreaseResult decrease(long productId, int quantity)`, `void increase(long productId, int quantity)`), `InventoryNotFoundException(long)`, 테이블 `inventory(product_id BIGINT PK, quantity INT)`.
- Produces:
  - `ConditionalStockStrategy` — `@ConditionalOnProperty(name = "inventory.strategy", havingValue = "conditional")`, `name()` = `"conditional"`.
  - `PessimisticStockStrategy` — `havingValue = "pessimistic"`, `name()` = `"pessimistic"`.
  - `abstract class StockStrategyContract` — 하위 클래스는 `@SpringBootTest(properties = "inventory.strategy=<값>")` + `@Import(TestcontainersConfiguration.class)` 만 붙인다. 재고를 다 파는지 여부는 `protected boolean sellsOutUnderContention()` (기본 `true`) 로 바꾼다. Task 2 가 재사용한다.

- [ ] **Step 1: 계약 테스트 작성**

```java
abstract class StockStrategyContract {
    @Autowired StockStrategy strategy;
    @Autowired InventoryRepository repository;

    protected boolean sellsOutUnderContention() { return true; }

    @BeforeEach void clean() { repository.deleteAll(); }

    @Test void decreaseSubtractsWhenEnough() {
        repository.save(new Inventory(1L, 5));
        assertThat(strategy.decrease(1L, 1)).isEqualTo(DecreaseResult.SUCCESS);
        assertThat(quantity(1L)).isEqualTo(4);
    }
    @Test void decreaseReturnsOutOfStock() {   // 재고 0
        repository.save(new Inventory(1L, 0));
        assertThat(strategy.decrease(1L, 1)).isEqualTo(DecreaseResult.OUT_OF_STOCK);
        assertThat(quantity(1L)).isZero();
    }
    @Test void decreaseNeverGoesBelowZero() {  // 재고 1, 요청 2
        repository.save(new Inventory(1L, 1));
        assertThat(strategy.decrease(1L, 2)).isEqualTo(DecreaseResult.OUT_OF_STOCK);
        assertThat(quantity(1L)).isEqualTo(1);
    }
    @Test void increaseAdds() {
        repository.save(new Inventory(1L, 3));
        strategy.increase(1L, 2);
        assertThat(quantity(1L)).isEqualTo(5);
    }
    @Test void unknownProductThrows() {
        assertThatThrownBy(() -> strategy.decrease(999L, 1)).isInstanceOf(InventoryNotFoundException.class);
        assertThatThrownBy(() -> strategy.increase(999L, 1)).isInstanceOf(InventoryNotFoundException.class);
    }
    @Test void concurrentDecreasesNeverOversell() throws Exception {
        // NaiveStockStrategyTest.concurrentDecreasesLoseUpdates 와 같은 구성: 재고 50, 요청 200, 스레드 32, CountDownLatch 동시 출발
        ...
        assertThat(successes).isLessThanOrEqualTo(50);
        assertThat(successes + remaining).isEqualTo(50);
        if (sellsOutUnderContention()) {
            assertThat(successes).isEqualTo(50);
            assertThat(remaining).isZero();
        }
    }
    // quantity(id): repository.findById(id).orElseThrow().getQuantity()
}
```
`ConditionalStockStrategyTest`, `PessimisticStockStrategyTest` 는 위 어노테이션만 붙인 빈 하위 클래스다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :inventory-service:test --tests '*ConditionalStockStrategyTest' --tests '*PessimisticStockStrategyTest'`
Expected: FAIL — `StockStrategy` 빈 없음(`NoSuchBeanDefinitionException`).

- [ ] **Step 3: `ConditionalStockStrategy` 구현** — `JdbcTemplate` 사용, `@Transactional` 없음(문장 하나가 곧 트랜잭션).
  - decrease: `UPDATE inventory SET quantity = quantity - ? WHERE product_id = ? AND quantity >= ?` → 1행이면 `SUCCESS`, 0행이면 `SELECT COUNT(*) FROM inventory WHERE product_id = ?` 가 0 이면 `InventoryNotFoundException`, 아니면 `OUT_OF_STOCK`.
  - increase: `UPDATE inventory SET quantity = quantity + ? WHERE product_id = ?` → 0행이면 `InventoryNotFoundException`.

- [ ] **Step 4: `PessimisticStockStrategy` 구현** — `JdbcTemplate` + 메서드 단위 `@Transactional`.
  - decrease/increase 공통: `SELECT quantity FROM inventory WHERE product_id = ? FOR UPDATE` (없으면 `InventoryNotFoundException`) → 비교 → `UPDATE inventory SET quantity = ? WHERE product_id = ?`. 락은 커밋까지 유지된다.

- [ ] **Step 5: 통과 확인**

Run: `./gradlew :inventory-service:test`
Expected: PASS (naive 테스트 포함 전부)

- [ ] **Step 6: Commit** — `git add inventory-service && git commit -m "조건부 UPDATE와 비관적 락 재고 차감 전략 추가"`

---

### Task 2: inventory-service — `optimistic` 전략, 재시도 통계, 통계 API

**Files:**
- Create: `inventory-service/src/main/resources/db/migration/V2__add_inventory_version.sql`
- Modify: `.../stock/DecreaseResult.java` (`CONFLICT` 추가)
- Create: `.../stock/OptimisticStockStrategy.java`, `.../stock/OptimisticProperties.java`, `.../stock/StockStats.java`
- Modify: `.../api/InventoryController.java`; Create: `.../api/StockStatsResponse.java`
- Modify: `inventory-service/src/main/resources/application.yml`
- Test: `.../stock/OptimisticStockStrategyTest.java`, `.../stock/OptimisticRetryTest.java`, `.../stock/OptimisticNoRetryTest.java`, `.../stock/OptimisticPropertiesTest.java`, `.../api/InventoryControllerTest.java`

**Interfaces:**
- Consumes: Task 1 `StockStrategyContract`.
- Produces:
  - `DecreaseResult { SUCCESS, OUT_OF_STOCK, CONFLICT }` — 차감 응답 본문 `{"result":"CONFLICT"}` (200).
  - `@ConfigurationProperties("inventory.optimistic") @Validated record OptimisticProperties(@DefaultValue("0") @Min(0) @Max(3) int maxRetries)`.
  - `OptimisticStockStrategy` — `havingValue = "optimistic"`, `name()` = `"optimistic-r" + maxRetries`. 테스트 전용 package-private `void setAfterReadForTest(Runnable hook)` (기본 no-op, 읽기 직후·UPDATE 직전에 실행).
  - `@Component StockStats` — `void conflict()`, `void retry()`, `void exhausted()`, `Snapshot snapshot()`, `void reset()`; `record Snapshot(long conflicts, long retries, long exhausted)`. Micrometer `FunctionCounter` 3개: `inventory.optimistic.conflicts`, `inventory.optimistic.retries`, `inventory.optimistic.exhausted` (Prometheus 이름 `inventory_optimistic_*_total`).
  - `GET /internal/inventory/stats` → 200 `{"conflicts":n,"retries":n,"exhausted":n}`, `DELETE /internal/inventory/stats` → 204.
  - 불변식: `conflicts == retries + exhausted`.

- [ ] **Step 1: 테스트 작성**

`OptimisticStockStrategyTest extends StockStrategyContract` — `properties = {"inventory.strategy=optimistic", "inventory.optimistic.max-retries=3"}`, `sellsOutUnderContention()` → `false`. 추가 테스트:
```java
@Test void nameIncludesRetries() { assertThat(strategy.name()).isEqualTo("optimistic-r3"); }
@Test void conflictsEqualRetriesPlusExhausted() throws Exception {
    // stats.reset(); 재고 50, 요청 200, 스레드 32 동시 출발, 결과별 개수 집계
    StockStats.Snapshot s = stats.snapshot();
    assertThat(successes + remaining).isEqualTo(50);
    assertThat(s.conflicts()).isEqualTo(s.retries() + s.exhausted());
    assertThat(s.exhausted()).isEqualTo(conflictResults);
    assertThat(s.retries()).isLessThanOrEqualTo(200L * 3);
    assertThat(successes + outOfStock + conflictResults).isEqualTo(200);
}
```
`OptimisticRetryTest` — `max-retries=1`. 충돌을 일으키는 쪽은 **풀 밖의 커넥션**을 쓴다: `MySQLContainer` 빈에서 `getJdbcUrl()/getUsername()/getPassword()` 로 `new JdbcTemplate(new SingleConnectionDataSource(url, user, pw, true))`. 훅은 한 번만 실행되게 `AtomicBoolean` 으로 막는다.
```java
// 훅: other.update("UPDATE inventory SET quantity = quantity - 1, version = version + 1 WHERE product_id = 1")
@Test void forcedConflictIsRetriedAndSucceeds() {
    repository.save(new Inventory(1L, 10)); stats.reset(); strategy.setAfterReadForTest(onceConflict);
    assertThat(strategy.decrease(1L, 1)).isEqualTo(DecreaseResult.SUCCESS);
    assertThat(quantity(1L)).isEqualTo(8);
    assertThat(stats.snapshot()).isEqualTo(new StockStats.Snapshot(1, 1, 0));
}
@Test void retriesReuseOneConnection() {
    // 테스트 설정: BeanPostProcessor 로 DataSource 를 getConnection() 횟수를 세는 DelegatingDataSource 로 감싼다
    repository.save(new Inventory(1L, 10)); strategy.setAfterReadForTest(onceConflict);
    long before = counting.count();
    assertThat(strategy.decrease(1L, 1)).isEqualTo(DecreaseResult.SUCCESS);
    assertThat(counting.count() - before).isEqualTo(1);
}
```
`OptimisticPropertiesTest` — 컨텍스트 없이 `ApplicationContextRunner` 로:
```java
@Test void maxRetriesAboveThreeFailsStartup() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
        .withUserConfiguration(EnableOptimisticProperties.class)   // @EnableConfigurationProperties(OptimisticProperties.class)
        .withPropertyValues("inventory.optimistic.max-retries=4")
        .run(ctx -> assertThat(ctx).hasFailed());
}
```
`OptimisticNoRetryTest` — `max-retries=0`, 충돌 훅은 `OptimisticRetryTest` 와 같은 구성:
```java
@Test void forcedConflictWithoutRetryReturnsConflict() {
    repository.save(new Inventory(1L, 10)); stats.reset(); strategy.setAfterReadForTest(onceConflict);
    assertThat(strategy.decrease(1L, 1)).isEqualTo(DecreaseResult.CONFLICT);
    assertThat(quantity(1L)).isEqualTo(9);
    assertThat(stats.snapshot()).isEqualTo(new StockStats.Snapshot(1, 0, 1));
}
@Test void nameIsR0() { assertThat(strategy.name()).isEqualTo("optimistic-r0"); }
```
`InventoryControllerTest` 에 추가(기본 naive 컨텍스트, `StockStats` 주입):
```java
@Test void statsEndpointReportsAndResets() throws Exception {
    stats.conflict(); stats.conflict(); stats.retry();
    mvc.perform(get("/internal/inventory/stats")).andExpect(jsonPath("$.conflicts").value(2))
        .andExpect(jsonPath("$.retries").value(1)).andExpect(jsonPath("$.exhausted").value(0));
    mvc.perform(delete("/internal/inventory/stats")).andExpect(status().isNoContent());
    mvc.perform(get("/internal/inventory/stats")).andExpect(jsonPath("$.conflicts").value(0));
}
```

- [ ] **Step 2: 실패 확인** — Run: `./gradlew :inventory-service:test` → Expected: 컴파일 실패(`CONFLICT`, `StockStats`, `setAfterReadForTest` 없음)

- [ ] **Step 3: 마이그레이션과 설정** — `V2__add_inventory_version.sql`: `ALTER TABLE inventory ADD COLUMN version BIGINT NOT NULL DEFAULT 0;`. `Inventory` 엔티티에는 매핑하지 않는다. `application.yml` 에 `inventory.optimistic.max-retries: ${INVENTORY_OPTIMISTIC_MAX_RETRIES:0}`.

- [ ] **Step 4: `StockStats`, `OptimisticProperties`, `DecreaseResult.CONFLICT`, 통계 API 구현** — `StockStats` 는 `LongAdder` 3개, `reset()` 은 셋 다 0. `OptimisticProperties` 는 `OptimisticStockStrategy` 의 `@EnableConfigurationProperties` 로만 켠다.

- [ ] **Step 5: `OptimisticStockStrategy` 구현** — 이 알고리즘대로(커넥션 하나, autocommit, `@Transactional` 없음):

```java
return jdbcTemplate.execute((ConnectionCallback<DecreaseResult>) con -> {
    for (int attempt = 0; ; attempt++) {
        // SELECT quantity, version FROM inventory WHERE product_id = ?   (없으면 InventoryNotFoundException)
        if (quantityRead < quantity) return DecreaseResult.OUT_OF_STOCK;
        afterRead.run();
        // UPDATE inventory SET quantity = ?, version = version + 1 WHERE product_id = ? AND version = ?
        if (updated == 1) return DecreaseResult.SUCCESS;
        stats.conflict();
        if (attempt == maxRetries) { stats.exhausted(); return DecreaseResult.CONFLICT; }
        stats.retry();
    }
});
```
increase: `UPDATE inventory SET quantity = quantity + ?, version = version + 1 WHERE product_id = ?` (0행이면 `InventoryNotFoundException`).

- [ ] **Step 6: 통과 확인** — Run: `./gradlew :inventory-service:test` → Expected: PASS

- [ ] **Step 7: Commit** — `git add inventory-service && git commit -m "낙관적 락 재고 차감 전략과 충돌·재시도 통계 추가"`

---

### Task 3: order-service — 클라이언트 타임아웃 구분과 `CONFLICT` 수신

**Files:**
- Modify: `order-service/src/main/java/com/flashsale/order/client/DownstreamException.java`
- Modify: `.../client/StockDecreaseResult.java` (`CONFLICT` 추가)
- Modify(필요 시): `.../client/InventoryClient.java`, `PaymentClient.java`, `ProductClient.java`
- Test: `order-service/src/test/java/com/flashsale/order/client/ClientsTest.java`

**Interfaces:**
- Produces:
  - `DownstreamException#isTimeout()`. `DownstreamException(String message)` → `false`. `DownstreamException(String message, Throwable cause)` → 원인 사슬에 `java.net.http.HttpTimeoutException`(connect timeout 의 `HttpConnectTimeoutException` 포함) 또는 `java.net.SocketTimeoutException` 이 있으면 `true`.
  - `StockDecreaseResult { SUCCESS, OUT_OF_STOCK, CONFLICT }`.

- [ ] **Step 1: 테스트 작성** (`ClientsTest`)

```java
@Test void readTimeoutIsMarkedTimeout() {   // 세 클라이언트 모두: 응답 지연 1000ms, read timeout 300ms
    // payment: /internal/payments, inventory: /internal/inventory/7/decrease, product: /api/products/events/1
    assertThatThrownBy(() -> client.pay(1, 2, 3)).isInstanceOfSatisfying(DownstreamException.class,
        e -> assertThat(e.isTimeout()).isTrue());
    // decrease(7, 1), getEvent(1) 도 같은 단언
}
@Test void serverErrorIsNotTimeout() {      // 500
    assertThatThrownBy(() -> client.pay(1, 2, 3)).isInstanceOfSatisfying(DownstreamException.class,
        e -> assertThat(e.isTimeout()).isFalse());
}
@Test void decreaseParsesConflict() {       // 200 {"result":"CONFLICT"}
    assertThat(client.decrease(7, 1)).isEqualTo(StockDecreaseResult.CONFLICT);
}
```
기존 `droppedConnectionIsNotRetried` 에 `isTimeout()` 이 `false` 라는 단언을 추가한다.

- [ ] **Step 2: 실패 확인** — Run: `./gradlew :order-service:test --tests '*ClientsTest'` → Expected: 컴파일 실패(`isTimeout`, `CONFLICT` 없음)

- [ ] **Step 3: 구현** — 위 Produces 대로. 실제로 던져지는 예외 타입은 `readTimeoutIsMarkedTimeout` 이 확인한다(JDK 클라이언트의 read timeout 은 `ResourceAccessException` 안에 `HttpTimeoutException` 으로 온다고 예상). 다르면 판별 목록을 맞춘다.

- [ ] **Step 4: 통과 확인** — Run: `./gradlew :order-service:test --tests '*ClientsTest'` → Expected: PASS

- [ ] **Step 5: Commit** — `git add order-service && git commit -m "하위 호출 타임아웃 구분과 재고 충돌 응답 수신 추가"`

---

### Task 4: order-service — 실패 사유 확장, 주문 생성 전 실패 집계, 응답 상태

**Files:**
- Modify: `order-service/src/main/java/com/flashsale/order/domain/FailReason.java`
- Modify: `.../application/OrderService.java`, `.../application/OrderStats.java`
- Create: `.../application/PreOrderFailures.java`, `.../application/PreOrderFailedException.java`
- Create: `.../api/FailReasonStatus.java`; Modify: `.../api/OrderController.java`, `.../api/ApiExceptionHandler.java`
- Test: `.../application/OrderServiceTest.java`, `.../api/OrderApiTest.java`

**Interfaces:**
- Consumes: Task 3 `DownstreamException#isTimeout()`, `StockDecreaseResult.CONFLICT`.
- Produces:
  - `FailReason` — Global Constraints 의 10개.
  - `@Component PreOrderFailures` — `void record(long eventId, FailReason reason)`, `Map<FailReason, Long> snapshot(long eventId)` (0건 생략, `EnumMap`), `void reset()`.
  - `PreOrderFailedException(FailReason reason, Throwable cause)` + `FailReason reason()`.
  - `OrderStats(long eventId, long paid, long failed, long pending, Map<FailReason, Long> failedByReason, Map<FailReason, Long> preOrderFailedByReason)` → `GET /internal/orders/stats` 에 `preOrderFailedByReason` 필드 추가.
  - `FailReasonStatus.of(FailReason) -> HttpStatus` — Global Constraints 의 응답 상태표.
  - 주문 생성 전 실패 응답: 상태 `FailReasonStatus.of(reason)`, 본문 `{"code":"<REASON>","reason":"<REASON>","message":"..."}`.
  - `OrderService` 생성자에 `PreOrderFailures` 추가.

- [ ] **Step 1: 테스트 작성**

`OrderServiceTest` (`PreOrderFailures` 는 실제 인스턴스를 생성자에 넣는다). 타임아웃 예외는 `new DownstreamException("t", new java.net.http.HttpTimeoutException("t"))`.
```java
@Test void conflictMarksStockConflict()          // decrease → CONFLICT
    → r == new OrderResult(99, FAILED, STOCK_CONFLICT); verifyNoInteractions(paymentClient)
@Test void inventoryTimeoutMarksInventoryTimeout() // decrease 가 타임아웃 예외
    → FAILED INVENTORY_TIMEOUT; verify(inventoryClient, never()).increase(anyLong(), anyInt()); verifyNoInteractions(paymentClient)
@Test void paymentTimeoutMarksPaymentTimeout()   // pay 가 타임아웃 예외
    → FAILED PAYMENT_TIMEOUT; increase 호출 없음
@Test void productTimeoutCountsPreOrderFailureWithoutOrder() // getEvent 가 타임아웃 예외
    → assertThatThrownBy(...).isInstanceOfSatisfying(PreOrderFailedException.class, e -> reason == PRODUCT_TIMEOUT)
      verifyNoInteractions(orderWriter, inventoryClient); preOrderFailures.snapshot(EVENT) == Map.of(PRODUCT_TIMEOUT, 1L)
@Test void productErrorCountsAsProductError()    // getEvent 가 new DownstreamException("500")
    → PreOrderFailedException(PRODUCT_ERROR); snapshot == {PRODUCT_ERROR: 1}
@Test void orderDbTimeoutCountsPreOrderFailure() // createPending 이
      new CannotCreateTransactionException("x", new SQLTransientConnectionException("Connection is not available"))
    → PreOrderFailedException(ORDER_DB_TIMEOUT); verifyNoInteractions(inventoryClient); snapshot == {ORDER_DB_TIMEOUT: 1}
@Test void otherCreatePendingFailurePropagates() // createPending 이 IllegalStateException
    → 그대로 IllegalStateException; snapshot 비어 있음
```
기존 `inventoryErrorDoesNotRestoreOrPay`(→ `INVENTORY_ERROR`), `paymentErrorDoesNotRestoreStock`(예외를 `new DownstreamException("down")` 으로 바꿔 `PAYMENT_ERROR`)는 비타임아웃 경로로 유지한다.

`OrderApiTest` (`setUp` 에서 `preOrderFailures.reset()` 추가):
```java
@Test void conflictReturns409WithReason()        // decrease → CONFLICT: 409, $.reason == "STOCK_CONFLICT"
@Test void inventoryTimeoutReturns504()          // 504, $.reason == "INVENTORY_TIMEOUT"
@Test void productTimeoutReturns504WithoutOrder() // getEvent 타임아웃: 504, $.reason == "PRODUCT_TIMEOUT", repository.count() == 0,
                                                 // GET /internal/orders/stats?eventId=1 → $.preOrderFailedByReason.PRODUCT_TIMEOUT == 1
@Test void preOrderFailuresCountOnlyRequestedEvent() // preOrderFailures.record(1, PRODUCT_TIMEOUT); record(2, ORDER_DB_TIMEOUT)
                                                 // stats?eventId=1 → PRODUCT_TIMEOUT == 1, ORDER_DB_TIMEOUT 없음
@Test void deleteResetsPreOrderFailures()        // record(1, PRODUCT_TIMEOUT); DELETE /internal/orders → stats?eventId=1 의 preOrderFailedByReason 비어 있음
```

- [ ] **Step 2: 실패 확인** — Run: `./gradlew :order-service:test` → Expected: 컴파일 실패

- [ ] **Step 3: 구현**
  - `placeOrder`: `getEvent` 의 `DownstreamException` → `PRODUCT_TIMEOUT`/`PRODUCT_ERROR` 를 `record` 하고 `PreOrderFailedException` (`EventNotFoundException` 은 지금처럼 전파). `createPending` 의 `RuntimeException` 중 원인 사슬에 `java.sql.SQLTransientConnectionException` 이 있으면 `ORDER_DB_TIMEOUT` 을 `record` 하고 `PreOrderFailedException`, 아니면 그대로 던진다. 차감 `CONFLICT` → `STOCK_CONFLICT`. 차감·결제 `DownstreamException` → `isTimeout()` 으로 `*_TIMEOUT`/`*_ERROR`.
  - `markPaid`/`markFailed` 실패 처리는 바꾸지 않는다(PENDING 이 남고 판정이 INCONCLUSIVE — 스펙 §5).
  - `stats` 에 `preOrderFailures.snapshot(eventId)`, `deleteAll` 에 `preOrderFailures.reset()`.
  - `OrderController.statusOf` 와 `ApiExceptionHandler` 의 `PreOrderFailedException` 처리 모두 `FailReasonStatus.of` 를 쓴다.

- [ ] **Step 4: 통과 확인** — Run: `./gradlew :order-service:test` → Expected: PASS (`remoteCallsRunOutsideTransactions` 포함)

- [ ] **Step 5: Commit** — `git add order-service && git commit -m "주문 실패 사유를 타임아웃·충돌·주문 생성 전 실패로 나누고 응답 상태 정리"`

---

### Task 5: gateway — 하위 타임아웃을 504 로

**Files:**
- Modify: `gateway/src/main/java/com/flashsale/gateway/GatewayRoutes.java`
- Test: `gateway/src/test/java/com/flashsale/gateway/GatewayTimeoutTest.java`

**Interfaces:**
- Produces: order 라우트에서 하위 read timeout 이 나면 504, 본문 `{"code":"GATEWAY_TIMEOUT","message":"..."}`. 타임아웃이 아닌 하위 오류의 동작은 바꾸지 않는다.

- [ ] **Step 1: 테스트 작성** — 별도 클래스로 컨텍스트를 분리한다.

```java
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "spring.http.clients.read-timeout=1s")
class GatewayTimeoutTest {
    // GatewayRoutingTest 처럼 EchoServer 3개와 @DynamicPropertySource, 유효한 JWT
    @Test void downstreamReadTimeoutReturns504() {
        ORDER.setDelayMillis(3000);
        HttpResponse<String> r = send("POST", "/api/orders", "Authorization", "Bearer " + token);
        assertThat(r.statusCode()).isEqualTo(504);
        assertThat(JSON.readTree(r.body()).get("code").asText()).isEqualTo("GATEWAY_TIMEOUT");
    }
}
```

- [ ] **Step 2: 실패 확인** — Run: `./gradlew :gateway:test --tests '*GatewayTimeoutTest'` → Expected: FAIL (지금 상태 코드를 기록해 둔다 — 500 또는 502 예상)

- [ ] **Step 3: 구현** — order 라우트 빌더에 `.onError(<원인 사슬에 HttpTimeoutException/SocketTimeoutException>, (e, req) -> 504 JSON)` 를 붙인다. 빌더에 `onError` 가 없으면 같은 판별로 `HandlerFilterFunction` 에서 잡는다.

- [ ] **Step 4: 통과 확인** — Run: `./gradlew :gateway:test` → Expected: PASS (기존 `slowDownstreamWithinReadTimeoutSucceeds` 포함)

- [ ] **Step 5: Commit** — `git add gateway && git commit -m "게이트웨이 하위 타임아웃을 504 GATEWAY_TIMEOUT 으로 응답"`

---

### Task 6: 관측 인프라 — mysqld-exporter, 스크레이프 1s, 대시보드 패널

**Files:**
- Modify: `compose.yaml`, `infra/mysql/init/01-schemas.sql`, `infra/prometheus/prometheus.yml`, `infra/grafana/dashboards/flash-sale.json`

**Interfaces:**
- Produces:
  - Prometheus job `mysql` (타깃 `mysqld-exporter:9104`) — `mysql_global_status_innodb_row_lock_waits`, `mysql_global_status_innodb_row_lock_time`(ms), `mysql_global_status_innodb_row_lock_current_waits`.
  - 모든 job 과 global 의 `scrape_interval: 1s`.
  - `compose.yaml` inventory-service env `INVENTORY_OPTIMISTIC_MAX_RETRIES: ${INVENTORY_OPTIMISTIC_MAX_RETRIES:-0}`.

- [ ] **Step 1: MySQL 계정** — `01-schemas.sql` 끝에: `CREATE USER 'exporter'@'%' IDENTIFIED BY 'exporter_pw' WITH MAX_USER_CONNECTIONS 3;`, `GRANT PROCESS, REPLICATION CLIENT ON *.* TO 'exporter'@'%';`, `GRANT SELECT ON performance_schema.* TO 'exporter'@'%';`

- [ ] **Step 2: compose** — 서비스 `mysqld-exporter`: 이미지 `prom/mysqld-exporter:v0.16.0`, command `["--mysqld.address=mysql:3306", "--mysqld.username=exporter"]`, env `MYSQLD_EXPORTER_PASSWORD: exporter_pw`, `depends_on: mysql: condition: service_healthy`, 한도 cpus "0.5" / 64m, 호스트 포트 공개 없음. inventory-service env 한 줄 추가.

- [ ] **Step 3: Prometheus** — global 과 기존 job 의 `scrape_interval` 을 `1s` 로, job `mysql` 추가(`metrics_path` 기본 `/metrics`).

- [ ] **Step 4: 대시보드** — 패널 5개 추가(id 11~15, 기존 형식·datasource uid `prometheus` 그대로, 8×8 격자로 y=24 이후 배치):
  - 11 `MySQL 행 락 대기 (회/s)`: `rate(mysql_global_status_innodb_row_lock_waits[10s])`
  - 12 `MySQL 행 락 대기 시간 (ms/s)`: `rate(mysql_global_status_innodb_row_lock_time[10s])`
  - 13 `MySQL 현재 락 대기 수`: `mysql_global_status_innodb_row_lock_current_waits`
  - 14 `낙관적 락 충돌·재시도 (회/s)`: `rate(inventory_optimistic_conflicts_total[10s])`, `rate(inventory_optimistic_retries_total[10s])`, `rate(inventory_optimistic_exhausted_total[10s])`
  - 15 `타임아웃 응답 (회/s)`: `sum by (job) (rate(http_server_requests_seconds_count{status="504",uri!~"/actuator.*"}[10s]))`

- [ ] **Step 5: 검증**

Run: `docker compose down && ./gradlew bootJar && docker compose up -d --build && scripts/smoke.sh`
Expected: `SMOKE OK`
Run: `curl -s 'http://localhost:9090/api/v1/query?query=up' | jq -r '.data.result[] | "\(.metric.job) \(.value[1])"'`
Expected: `mysql 1` 을 포함해 모든 job 이 1
Run: `curl -s 'http://localhost:9090/api/v1/query?query=mysql_global_status_innodb_row_lock_waits' | jq '.data.result | length'`
Expected: `1` (지표 이름이 다르면 패널·이후 Task 의 PromQL 을 실제 이름으로 맞춘다)
Run: `curl -s http://localhost:3000/api/dashboards/uid/flash-sale | jq '.dashboard.panels | length'`
Expected: `15`

- [ ] **Step 6: Commit** — `git add compose.yaml infra && git commit -m "MySQL 지표 수집기와 락·충돌·타임아웃 패널 추가, 스크레이프 1초로"`

---

### Task 7: 판정 — k6 응답 분류, `verdict.jq`, `verify.sh`

**Files:**
- Create: `load-test/k6/classify.js`, `load-test/k6/classify-test.js`
- Modify: `load-test/k6/flash-sale.js`
- Modify: `scripts/verdict.jq`, `scripts/verify.sh`
- Create: `scripts/test-verdict.sh`

**Interfaces:**
- Consumes: Task 4 사유 이름과 응답 본문 `reason`, Task 5 `GATEWAY_TIMEOUT`.
- Produces:
  - `classify.js` — `export function classify(res)` → `'paid' | 'sold_out' | 'conflict' | 'order_timeout' | 'gateway_timeout' | 'client_timeout' | 'other'`. 규칙: 201 → paid; 409 + `reason` SOLD_OUT → sold_out, STOCK_CONFLICT → conflict; 504 + `reason` ∈ {INVENTORY_TIMEOUT, PAYMENT_TIMEOUT, PRODUCT_TIMEOUT, ORDER_DB_TIMEOUT} → order_timeout; 504 + `code` GATEWAY_TIMEOUT → gateway_timeout; status 0 + `error_code` 1050 → client_timeout; 그 밖(본문 파싱 실패 포함) → other.
  - `flash-sale.js` Counter 7개: `orders_paid`, `orders_sold_out`, `orders_conflict`, `orders_order_timeout`, `orders_gateway_timeout`, `orders_client_timeout`, `orders_other`.
  - `verify.sh <event_id> <product_id> <sale_quantity> <users> <k6_summary_json>` → stdout JSON:
    `saleQuantity, users, paid, failed, pending, preOrderFailed, finalStock, oversold (= paid − saleQuantity), undersold (= max(saleQuantity − paid, 0)), stockMismatch (= (saleQuantity − finalStock) − paid), conflicts (= failedByReason.STOCK_CONFLICT), failedByReason, preOrderFailedByReason, timeouts {inventory, payment, product, orderDb, gateway, client, total}, k6 {paid, soldOut, conflict, orderTimeout, gatewayTimeout, clientTimeout, other, total}, verdict, inconclusive`. 항상 exit 0.
  - `verdict.jq` — 입력은 위 객체(verdict/inconclusive 제외), 출력 `{"verdict": "...", "inconclusive": [<검사 이름>...]}`.

`verdict.jq` 규칙(이 순서. 검사 이름은 정확히 이 문자열):
1. `paid > saleQuantity` → `OVERSOLD` (다른 검사와 무관하게 우선)
2. 아래 검사 중 실패한 것을 `inconclusive` 에 모아, 하나라도 있으면 `INCONCLUSIVE`:
   - `pending` — `pending > 0`
   - `order-total` — `paid + failed + pending + preOrderFailed != users` (pending 은 `pending` 검사가 따로 잡으므로 여기선 주문 행으로 센다)
   - `non-characteristic-reason` — `failedByReason`·`preOrderFailedByReason` 에 특성 사유가 아닌 키가 1건 이상
   - `k6-unclassified` — `k6.other > 0`
   - `k6-total` — `k6.total != users`
   - `k6-db-paid` — `k6.paid <= paid <= k6.paid + k6.gatewayTimeout + k6.clientTimeout` 이 아님
3. `paid < saleQuantity` → `UNDERSOLD`
4. 그 밖 → `OK`

- [ ] **Step 1: classify 테스트 작성** — `classify-test.js` (vus 1, iterations 1, HTTP 없음). 가짜 응답 `{status, body, error_code}` 마다 `check(...)` 로 기대 분류를 확인하고(`throw` 하지 않는다), `thresholds: { checks: ['rate==1'] }` 를 둬 하나라도 틀리면 k6 가 0이 아닌 코드로 끝나게 한다. 경우:
  `201 → paid`, `409 SOLD_OUT → sold_out`, `409 STOCK_CONFLICT → conflict`, `409 기타 reason → other`, `504 INVENTORY_TIMEOUT → order_timeout`, `504 ORDER_DB_TIMEOUT → order_timeout`, `504 code GATEWAY_TIMEOUT → gateway_timeout`, `504 본문 없음 → other`, `0 + 1050 → client_timeout`, **`status0NonTimeoutIsOther`: `0 + 1000 → other`**, `502 INVENTORY_ERROR → other`, `400 → other`, `401 → other`.

- [ ] **Step 2: verdict 테스트 작성** — `scripts/test-verdict.sh`: 각 경우를 `jq -n` 으로 만든 입력에 `jq -f scripts/verdict.jq` 를 돌려 `verdict` 와 `inconclusive` 를 비교, 끝에 `verdict tests: N passed` 출력, 하나라도 틀리면 exit 1. 공통 기준 `users 10, saleQuantity 5`:

| 이름 | 입력 요지 | 기대 |
|---|---|---|
| `ok` | paid 5, failed 5 {SOLD_OUT 5}, k6 paid 5 / soldOut 5 | `OK`, `[]` |
| `oversold` | paid 7, failed 3 {SOLD_OUT 3}, k6 일치 | `OVERSOLD` |
| `oversoldWinsOverPending` | paid 7, pending 2, failed 1 | `OVERSOLD` |
| `undersoldByConflict` | paid 3, failed 7 {SOLD_OUT 4, STOCK_CONFLICT 3}, k6 paid 3 / soldOut 4 / conflict 3 | `UNDERSOLD` |
| `undersoldByTimeouts` | paid 4, failed 5 {SOLD_OUT 3, INVENTORY_TIMEOUT 2}, preOrder {PRODUCT_TIMEOUT 1}, k6 paid 4 / soldOut 3 / orderTimeout 3 | `UNDERSOLD` |
| `gatewayTimeoutExplainsPaidGap` | paid 5, failed 5 {SOLD_OUT 5}, k6 paid 4 / soldOut 5 / gatewayTimeout 1 | `OK` |
| `pendingIsInconclusive` | paid 4, pending 1, failed 5 {SOLD_OUT 5}, k6 paid 4 / soldOut 5 / gatewayTimeout 1 | `INCONCLUSIVE`, `["pending"]` |
| `nonCharacteristicReason` | paid 5, failed 5 {SOLD_OUT 4, INVENTORY_ERROR 1}, k6 other 1 | `INCONCLUSIVE`, `["non-characteristic-reason","k6-unclassified"]` |
| `orderTotalMismatch` | paid 5, failed 4, k6 total 10 | `INCONCLUSIVE`, `["order-total"]` |
| `k6PaidExceedsDb` | paid 5, k6 paid 6 / soldOut 4 | `INCONCLUSIVE`, `["k6-db-paid"]` |

- [ ] **Step 3: 실패 확인** — Run: `k6 run --quiet load-test/k6/classify-test.js; echo $?` → Expected: 0이 아님(`classify.js` 없음). Run: `scripts/test-verdict.sh` → Expected: 실패 출력, exit 1

- [ ] **Step 4: 구현** — `classify.js`, `verdict.jq`(위 규칙), `verify.sh`(`order_stats` + 재고 조회 + `k6_summary_json` 의 `.metrics.orders_*.count // 0` 을 합쳐 입력을 만들고 `verdict.jq` 결과를 합친다), `flash-sale.js` 가 `classify` 결과로 Counter 를 올리게 바꾼다(`setResponseCallback(expectedStatuses(201, 409))` 은 유지).

- [ ] **Step 5: 통과 확인** — Run: `k6 run --quiet load-test/k6/classify-test.js && scripts/test-verdict.sh` → Expected: exit 0, `verdict tests: 10 passed`

- [ ] **Step 6: Commit** — `git add load-test scripts && git commit -m "k6 응답 7종 분류와 4가지 판정 규칙 추가"`

---

### Task 8: 실행 기록 — 원인 지표, 워밍업 표시, 결과표 확장

**Files:**
- Modify: `scripts/lib.sh`, `scripts/run-test.sh`, `docs/results/README.md`

**Interfaces:**
- Consumes: Task 2 통계 API, Task 6 Prometheus job, Task 7 `verify.sh` 새 인자.
- Produces:
  - `lib.sh`: `reset_data` 가 `DELETE $INVENTORY_URL/internal/inventory/stats` 도 호출. 새 함수 `inventory_stats`, `mysql_lock_status` (→ `{"waits":n,"timeMs":n}`, `docker compose exec -T mysql mysql -uroot -proot -N -e "SHOW GLOBAL STATUS WHERE Variable_name IN ('Innodb_row_lock_waits','Innodb_row_lock_time')"`), `mysql_settings` (→ `{"isolation":"...","lockWaitTimeout":n}`, `SELECT @@transaction_isolation, @@innodb_lock_wait_timeout`), `prom_query <promql> <unix_time>` (→ 첫 결과 값, 없으면 `n/a`). `wait_orders_settled <event_id> <users> [timeout=60]` — `pending == 0` 이고 `paid + failed + preOrderFailed == users` 가 될 때까지.
  - `run-test.sh`: env `WARMUP` (기본 0). `WARMUP=1` 이면 `RUN_ID` 끝에 `-warmup`. 실행 디렉터리에 `meta.json` `{runId, strategy, kind: "warmup"|"measure", users, saleQuantity, commit}` 과 `causes.json` 추가.
  - `causes.json`: `mysql {lockWaits, lockTimeMs, avgLockWaitMs, maxCurrentWaits}`, `hikariPendingMax {inventory, order, payment, product}`, `tomcatBusyMax {gateway, inventory, order, payment, product}`, `optimistic {conflicts, retries, exhausted}`, `timeouts` (verify.json 과 같음), `inventoryDecreaseP95Ms`, `decreaseCalls (= paid + failed)`, `paymentCalls (= paid + PAYMENT_TIMEOUT + PAYMENT_ERROR + PAYMENT_DECLINED)`.
  - 결과표 머리글: `| run | kind | strategy | users | stock | paid | oversold | undersold | finalStock | conflicts | timeouts | retries | verdict | p95(ms) | p99(ms) | rps |`

- [ ] **Step 1: lib.sh 함수 추가·수정** — 위 Produces 대로.

- [ ] **Step 2: run-test.sh 확장**
  - k6 직전: `mysql_lock_status` 저장, `t_start=$(date +%s)`. k6 직후: `t_end`, `wait_orders_settled "$EVENT_ID" "$USERS" 60`, `sleep 2`(마지막 스크레이프), `mysql_lock_status` 다시 저장.
  - `verify.sh "$EVENT_ID" "$PRODUCT_ID" "$SALE_QUANTITY" "$USERS" "$RUN_DIR/k6-summary.json"`.
  - Prometheus 구간 `W = (t_end − t_start + 4)s`, 평가 시각 `t_end + 2`. 질의: `max(max_over_time(hikaricp_connections_pending{job="<svc>"}[W]))`, `max(max_over_time(tomcat_threads_busy_threads{job="<svc>"}[W]))`, `max_over_time(mysql_global_status_innodb_row_lock_current_waits[W])`, `histogram_quantile(0.95, sum by (le) (increase(http_server_requests_seconds_bucket{job="inventory-service",uri="/internal/inventory/{productId}/decrease"}[W]))) * 1000`.
  - `report.md`:
    - "검증" 절에 verify.json 의 새 값(undersold, preOrderFailed, conflicts, timeouts, verdict, inconclusive)을 넣는다. 기존 "k6 orders_paid vs DB paid" 줄은 `inconclusive` 표시로 바꾼다.
    - "원인 지표" 절에 causes.json 을 한 줄씩 넣는다. Prometheus 에서 온 값에는 "(1s 표본)"을 표시한다.
    - "고정 설정" 절에 다음 넷을 추가한다: MySQL 격리 수준과 `innodb_lock_wait_timeout`(`mysql_settings`), Hikari connectionTimeout 30s(기본값), Prometheus 스크레이프 간격(prometheus.yml 에서 읽음), kind.
  - README 표 행은 새 머리글 순서를 따르고, `retries` 는 inventory stats 의 `retries` 값이다.

- [ ] **Step 3: README 갱신** — `docs/results/README.md`:
  - verdict 정의를 4가지로 바꾼다. 스펙 §5 를 한 줄씩 옮기고, `inconclusive` 검사 이름도 적는다.
  - "고정 설정"에 MySQL 격리 수준·락 대기 타임아웃·Hikari connectionTimeout 을 추가한다. 스크레이프 간격은 "0단계 5s / 1단계부터 1s"로 적는다.
  - 표 머리글을 새 형식으로 바꾼다.
  - 기존 0단계 3행을 새 열로 다시 쓴다. kind `measure`, undersold 0, finalStock 0, conflicts 0, timeouts 0, retries `-` 이다(각 행의 `verify.json`·`order-stats.json` 으로 확인).
  - 표 위에 "0단계 기준선(커밋 b3cf43c) 행은 1단계 이전 환경" 한 줄을 둔다.

- [ ] **Step 4: 검증 (작은 규모)**

Run: `USERS=200 SALE_QUANTITY=20 scripts/run-test.sh` (naive) 그리고 `WARMUP=1 USERS=200 SALE_QUANTITY=20 scripts/run-test.sh`
Expected: 각 실행이 끝에 판정을 출력한다. `report.md`·`causes.json`·`meta.json` 에 `null` 이 없다(Prometheus 값의 `n/a` 는 허용). 두 번째 실행의 RUN_ID 가 `-warmup` 으로 끝나고 README 행의 kind 가 `warmup` 이다.
Run: `INVENTORY_STRATEGY=conditional docker compose up -d inventory-service && scripts/smoke.sh && USERS=200 SALE_QUANTITY=20 scripts/run-test.sh`
Expected: `OK`, `causes.json` 의 `mysql.lockWaits` 가 숫자
확인 뒤 이 시험 실행들의 디렉터리와 README 행을 지우고, inventory-service 를 naive 로 되돌린다(`docker compose up -d inventory-service`).

- [ ] **Step 5: Commit** — `git add scripts docs/results/README.md && git commit -m "실행마다 원인 지표와 워밍업 구분을 기록하고 결과표 열 확장"`

---

### Task 9: 측정 매트릭스 스크립트와 실행 안내

**Files:**
- Create: `scripts/run-matrix.sh`
- Modify: `README.md`

**Interfaces:**
- Consumes: Task 8 `run-test.sh` (`WARMUP`), `lib.sh` `current_strategy`, `scripts/smoke.sh`.
- Produces: `scripts/run-matrix.sh [config...]` — 인자가 없으면 `naive pessimistic conditional optimistic-r0 optimistic-r1 optimistic-r2 optimistic-r3`. env `RUNS`(기본 3), `USERS`·`SALE_QUANTITY` 는 그대로 `run-test.sh` 에 전달.

- [ ] **Step 1: 스크립트 작성** — 구성마다 다음 순서로 진행한다.
  1. 구성 이름을 `INVENTORY_STRATEGY` 와 `INVENTORY_OPTIMISTIC_MAX_RETRIES` 로 나눈다(`optimistic-rN` → `optimistic`, N; 나머지 → 그 이름, 0).
  2. `docker compose up -d inventory-service` 로 컨테이너를 다시 만든다(env 가 바뀌면 재생성된다).
  3. `wait_healthy` 를 기다리고, `current_strategy` 가 구성 이름과 같은지 확인한다. 다르면 중단한다.
  4. `scripts/smoke.sh` 를 돌린다. order-service 에 남은 예전 컨테이너 연결을 정리하기 위해서다.
  5. `WARMUP=1 scripts/run-test.sh` 를 한 번 돌린 뒤, `scripts/run-test.sh` 를 `RUNS` 번 돌린다.

  모든 구성이 끝나면 구성별 판정 목록을 출력하고, inventory-service 를 naive 로 되돌린다.

- [ ] **Step 2: README 갱신** — "재고 전략 교체" 절을 고친다.
  - 재빌드는 필요 없고, 환경변수를 바꾼 뒤 `docker compose up -d inventory-service` 만 하면 된다.
  - 유효한 이름과 `INVENTORY_OPTIMISTIC_MAX_RETRIES`(0~3)를 적는다.
  - 매트릭스 실행 명령과 예상 소요 시간(구성당 약 5분)을 적는다.
  - 새 Grafana 패널과 `scripts/test-verdict.sh`·`classify-test.js` 실행법을 한 줄씩 적는다.

- [ ] **Step 3: 검증 (작은 규모)**

Run: `RUNS=1 USERS=200 SALE_QUANTITY=20 scripts/run-matrix.sh conditional optimistic-r1`
Expected: 구성마다 워밍업 1 + 측정 1 실행, strategy 열이 `conditional`, `optimistic-r1`, 마지막에 판정 목록, inventory-service 가 naive 로 돌아옴(`curl -s localhost:8083/internal/strategy` → `naive`)
확인 뒤 이 시험 실행들의 디렉터리와 README 행을 지운다.

- [ ] **Step 4: Commit** — `git add scripts/run-matrix.sh README.md && git commit -m "1단계 측정 매트릭스 실행 스크립트와 전략 교체 안내 갱신"`

---

### Task 10: 1단계 측정

**Files:**
- Create: `docs/results/runs/<RUN_ID>/*` (워밍업 7 + 측정 21)
- Modify: `docs/results/README.md` (28행)

- [ ] **Step 1: 깨끗한 환경에서 기동** — Run: `docker compose down && ./gradlew test bootJar && docker compose up -d --build && scripts/smoke.sh` → Expected: 테스트 전부 통과, `SMOKE OK`

- [ ] **Step 2: 매트릭스 실행** — Run: `scripts/run-matrix.sh` (USERS 5000, SALE_QUANTITY 100, RUNS 3). 30분 안팎이 걸리므로 백그라운드로 돌리고 끝날 때까지 기다린다.

- [ ] **Step 3: 완료 기준 확인** — README 표에서 kind `measure` 행만 보고 다음을 확인한다.
  - `naive` 3행은 모두 `OVERSOLD` 다.
  - `pessimistic`·`conditional`·`optimistic-r0~r3` 18행 중 `OVERSOLD` 와 `INCONCLUSIVE` 가 0이다.

  기준을 어긴 행이 있으면 거기서 멈추고 보고한다. 보고에는 해당 RUN_ID 와 `verify.json` 의 `inconclusive`·사유를 담는다. 스펙 §5 에 따라 원인을 고친 뒤 그 구성을 다시 재야 하며, 이 판단은 컨트롤러가 내린다. 설정 튜닝은 해결책으로 쓰지 않는다.

- [ ] **Step 4: Commit** — `git add docs/results && git commit -m "1단계 측정 결과 기록: 7개 구성 워밍업 1회와 측정 3회"`

---

### Task 11: 1단계 요약

**Files:**
- Create: `scripts/summarize.sh`, `docs/results/stage1-summary.md`

**Interfaces:**
- Consumes: Task 8 `meta.json`·`verify.json`·`causes.json`·`k6-summary.json`.
- Produces: `scripts/summarize.sh` — `meta.json` 이 있고 `kind == "measure"`, `users == 5000`, `saleQuantity == 100` 인 실행만 모아, 구성별(매트릭스 순서) 3회 **중앙값** 마크다운 표를 stdout 에 출력한다. 열: 구성, 판정(3회 목록), paid, undersold, conflicts, timeouts, retries, p95, p99, rps, inventory 차감 서버 p95, MySQL 락 대기 횟수, 평균 락 대기(ms), inventory Hikari pending 최대, inventory Tomcat busy 최대, 결제 호출 수.

- [ ] **Step 1: `summarize.sh` 작성과 확인** — Run: `scripts/summarize.sh` → Expected: 7행 표, 빈 칸·`null` 없음

- [ ] **Step 2: `stage1-summary.md` 작성** — 표는 `summarize.sh` 출력을 그대로 붙인다. 아래 4절을 쓰되, 각 주장에는 **기록된 수치**를 근거로 단다.
  - **정답:** 구성별 OK/UNDERSOLD 와 그 이유(충돌·타임아웃 건수)를 적는다.
  - **비용:** naive 대비 p95/p99/RPS 와, inventory 차감 서버 p95 를 함께 적는다. 결제 호출 수 차이로 끝단 지연이 왜곡될 수 있다는 점도 짚는다.
  - **원인:** 락 대기, 커넥션 풀 대기, Tomcat 포화, 재시도 증가가 지연·덜 팔림과 어떻게 이어지는지 적는다.
  - **2단계로 넘어갈 근거:** "DB 한 행에 몰리는 구조의 한계"를 숫자로 적는다.

  끝에 0단계 기준선(423/566/720)을 참고로 한 줄 남긴다.

- [ ] **Step 3: Commit** — `git add scripts/summarize.sh docs/results/stage1-summary.md && git commit -m "1단계 요약: 방식별 정답·비용·원인 비교"`
