# 선착순 MSA 2단계(이커머스 기본 기능) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 다섯 서비스를 DDD·헥사고날 패키지 구조로 옮기고, 상품 목록·장바구니·장바구니 주문·주문 내역 API 와 무작위로 실패하는 가짜 PG 를 더한 뒤, 선착순 실험이 그대로 도는지 매트릭스로 확인한다.

**Architecture:**
- 서비스 개수와 서비스 간 동기 HTTP(RestClient) 통신은 그대로 둔다.
- 각 서비스 안을 다음 패키지로 나눈다.
  - `domain`: 순수 자바
  - `application`: 애플리케이션 서비스와 `application.port.out`
  - `adapter.in.web`
  - `adapter.out.persistence` / `adapter.out.http` / `adapter.out.pg` / `adapter.out.token`
  - `config`
- 기능 배치:
  - 장바구니와 주문: order-service
  - 여러 상품 일괄 차감: inventory-service 의 트랜잭션 하나짜리 조건부 UPDATE
  - 가짜 PG: payment-service 의 `PaymentGateway` 포트 어댑터
- 앞의 다섯 태스크는 동작을 바꾸지 않고 구조만 옮긴다. 그 뒤 태스크들이 기능을 더한다.

**Tech Stack:**
- 1단계와 같다: Java 25, Spring Boot 4.1.2, Spring Cloud 2025.1.4, Gradle 9.8.2, MySQL 8.4, Flyway, Testcontainers 2.x, k6 2.2.
- 추가: `com.tngtech.archunit:archunit-junit5`(테스트 전용).

**Spec:** `docs/superpowers/specs/2026-11-09-ecommerce-core-design.md`. 0·1단계 스펙 위에 쌓인다.

## Global Constraints

- **브랜치와 커밋**
  - 브랜치 `feat/flash-sale-stage2`. push, main 머지, PR 없음.
  - 커밋 메시지는 한국어, prefix 없음. **트레일러(Co-Authored-By 등) 금지.**
  - 커밋 전 `git grep -nE "/(U)sers/"` 결과가 비어 있어야 한다(로컬 절대 경로 금지).
- **1단계 계획 규칙 중 그대로 지키는 것**
  - DTO 는 record, Lombok 없음.
  - 에러 본문 `{"code","message"}`.
  - HTTP 클라이언트: `RestClient` + `JdkClientHttpRequestFactory`, connect 2s / read 10s, 재시도 없음.
  - 원격 호출을 감싸는 `@Transactional` 금지.
  - 금지: 가상 스레드, spring-retry·resilience4j·OpenFeign.
  - 타임아웃·Tomcat·Hikari·MySQL 설정과 `compose.yaml` 의 컨테이너 한도는 바꾸지 않는다.
- **재고 전략**
  - 재고 JPA 엔티티에 `@Version` 금지.
  - `NaiveStockStrategy` 와 `NaiveStockStrategyTest` 는 패키지·import·타입 이름만 바꾼다. 로직은 그대로다.
  - `concurrentDecreasesLoseUpdates` 는 계속 통과해야 한다.
- **패키지 규칙**(스펙 §4). 루트는 `com.flashsale.<service>` 다(`user`, `product`, `inventory`, `order`, `payment`).
  - 인바운드 포트 인터페이스는 만들지 않는다. 컨트롤러가 애플리케이션 서비스를 직접 부른다.
  - 넣지 않는 것: CQRS, 도메인 이벤트 버스, Gradle 모듈 분리, 공통 베이스 클래스, 공용 모듈.
  - Spring Data 인터페이스 이름은 `*JpaRepository`, JPA 엔티티 이름은 `*JpaEntity` 다. `*Repository` 라는 이름은 `application.port.out` 의 포트가 쓴다.
- **ArchUnit 규칙**: 서비스마다 `src/test/java/com/flashsale/<service>/ArchitectureTest.java` 를 두고 정확히 이 두 규칙을 쓴다.

```java
@AnalyzeClasses(packages = "com.flashsale.<service>", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule domainIsPureJava = noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework..", "jakarta..", "org.hibernate..", "tools.jackson..", "com.fasterxml.jackson..",
            "io.micrometer..", "..application..", "..adapter..", "..config..");
    @ArchTest
    static final ArchRule applicationDoesNotSeeAdapters = noClasses().that().resideInAPackage("..application..")
        .should().dependOnClassesThat().resideInAPackage("..adapter..");
}
```

- **ArchUnit 의존성**
  - 각 서비스 `build.gradle` 에 `testImplementation 'com.tngtech.archunit:archunit-junit5:1.4.1'` 를 넣는다.
  - Java 25 클래스 파일을 못 읽어 `Unsupported class file major version` 경고나 오류가 나면, Maven Central 의 최신 `archunit-junit5` 로 올린다. 다섯 서비스 모두 같은 버전을 쓴다.
- **바꾸면 안 되는 API**: 측정 스크립트가 쓰는 API 다. 경로·요청·응답을 그대로 둔다.
  - `POST /api/orders` `{eventId}` → `{orderId,status,reason}`
  - `POST /internal/events`, `GET /api/products/events/{eventId}`
  - `PUT|GET /internal/inventory/{productId}`, `POST /internal/inventory/{productId}/decrease|increase`
  - `GET|DELETE /internal/inventory/stats`, `GET /internal/strategy`
  - `GET /internal/orders/stats?eventId=`, `DELETE /internal/orders`
  - `POST|DELETE /internal/payments`, `GET|PUT /internal/payments/config` (`{delayMs,jitterMs,failureRate}`)
  - `POST /internal/test-users`, `POST /api/users/signup|login`
- **`FailReason` 과 응답 상태**
  - 1단계의 10개 값과 응답 상태 매핑 그대로다: `SOLD_OUT`·`STOCK_CONFLICT` 409, `PAYMENT_DECLINED` 402, `*_TIMEOUT` 504, `*_ERROR` 502.
- **새 오류 코드**
  - `CART_EMPTY` 400, `PRODUCT_UNAVAILABLE` 400
  - `PRODUCT_NOT_FOUND` 404, `ORDER_NOT_FOUND` 404
  - 잘못된 요청 값은 기존처럼 `INVALID_REQUEST` 400
- **값**
  - 장바구니 수량 1~99.
  - 목록 API 의 `page` 는 0 이상(기본 0), `size` 는 1~100(기본 20). 범위 밖이면 400 `INVALID_REQUEST`.
  - 일괄 차감 `items` 는 1~100개이고 `productId` 가 겹치면 400.
- **결제**
  - 실패율 기본값 `0.1`. 환경변수 `PAYMENT_FAILURE_RATE` 로 바꾼다.
  - PG 선택은 `payment.gateway`(env `PAYMENT_GATEWAY`, 기본 `fake`).
  - 측정·스모크 스크립트는 실행 전에 실패율만 0 으로 고정한다.
- **차감 결과 값**
  - 응답 값은 지금처럼 `SUCCESS` / `OUT_OF_STOCK` / `CONFLICT` 다.
  - 스펙 §4 의 `DECREASED` 는 지금의 `SUCCESS` 를 가리킨다. 응답 값은 바꾸지 않는다.

## Review Focus

1. **실패율 10% 가 선착순 측정에 새어 듦**
   - 상황: payment-service 를 다시 띄우면 실패율이 0.1 로 돌아간다.
   - 기대: `run-test.sh` 와 `smoke.sh` 가 실행 전에 0 으로 고정한다. 실패율을 1.0 으로 둔 채 `smoke.sh` 를 돌려도 통과한다.
   - 담당: Task 3 Step 6.
2. **상태 변경이 주문 항목을 다시 씀**
   - 상황: 분리된 JPA 엔티티를 `save`(merge)하면 `order_items` 를 지우고 다시 넣거나, 쿼리가 늘 수 있다.
   - 기대: `markPaid`·`markFailed` 뒤에도 항목 행의 id 와 개수가 그대로다.
   - 담당: Task 5 `statusChangeKeepsOrderItems`.
3. **일괄 차감이 일부만 차감되거나 데드락 남**
   - 상황: 같은 두 상품을 반대 순서로 담은 주문이 동시에 들어온다.
   - 기대: 예외 없이 끝나고, 성공 건수만큼만 줄며, 실패한 요청은 아무 행도 바꾸지 않는다.
   - 담당: Task 7 `opposingOrderBatchesNeitherDeadlockNorOversell`, `outOfStockLeavesEveryRowUnchanged`.
4. **결제 중에 장바구니가 바뀜**
   - 상황: 결제하는 동안 사용자가 새 상품을 담는다.
   - 기대: `PAID` 뒤에는 주문에 들어간 줄만 지워지고, 새로 담은 상품은 남는다.
   - 담당: Task 10 `paidRemovesOnlyOrderedLines`.
5. **담은 뒤 판매 중지된 상품**
   - 상황: 장바구니에 담은 뒤 상품이 판매 상품이 아니게 된다.
   - 기대 1: 장바구니 보기에서 그 줄은 `available=false` 이고 합계에서 빠진다.
   - 기대 2: 주문은 만들지 않고 400 `PRODUCT_UNAVAILABLE` 이다. 재고 호출도 없다.
   - 담당: Task 9 `unavailableLineShownButExcludedFromTotal`, Task 10 `unlistedProductCreatesNoOrderAndNoStockCall`.

---

### Task 1: user-service 를 헥사고날 구조로 옮기기

**Files:**
- Modify: `user-service/build.gradle` (ArchUnit)
- Test (create): `user-service/src/test/java/com/flashsale/user/ArchitectureTest.java`
- Move/rewrite: `user-service/src/main/java/com/flashsale/user/` 아래를 이렇게 옮긴다.

| 지금 | 옮길 곳 |
|---|---|
| `domain/User.java` (JPA) | `domain/User.java` (순수) + `adapter/out/persistence/UserJpaEntity.java` |
| `domain/UserRepository.java` | `application/port/out/UserRepository.java` + `adapter/out/persistence/UserJpaRepository.java` + `UserPersistenceAdapter.java` |
| `UserService.java` (중첩 예외 2개) | `application/UserService.java`, `domain/EmailTakenException.java`, `domain/InvalidCredentialsException.java` |
| `TestUserService.java` (JdbcTemplate) | `application/TestUserService.java` + `application/port/out/TestUserStore.java` + `adapter/out/persistence/JdbcTestUserStore.java` |
| `auth/JwtIssuer.java`, `auth/JwtProperties.java` | `adapter/out/token/` (`JwtIssuer implements TokenIssuer`) + `application/port/out/TokenIssuer.java` |
| `api/*` | `adapter/in/web/*` |

- Tests: `api/UserApiTest` → `adapter/in/web/UserApiTest`, `auth/JwtIssuerTest` → `adapter/out/token/JwtIssuerTest`. import 만 바꾼다.

**Interfaces:**
- Produces:
  - `domain.User`: `static User register(String email, String passwordHash, String name, Instant now)`, `static User restore(long id, String email, String passwordHash, String name, Instant createdAt)`. 접근자는 `Long id()`, `email()`, `passwordHash()`, `name()`, `createdAt()`.
  - `UserRepository`: `boolean existsByEmail(String)`, `Optional<User> findByEmail(String)`, `User save(User)`. 이메일 유니크 위반이면 `EmailTakenException` 을 던진다.
  - `TokenIssuer`: `String issue(long userId)`.
  - `TestUserStore`: `Map<String, Long> ensure(List<String> emails, String passwordHash, Instant now)`. 기존 `INSERT IGNORE` 일괄 입력과 조회를 그대로 한다.
  - 예외 메시지는 그대로다: `"email already registered"`, `"invalid credentials"`.
  - `PasswordEncoder` 빈은 `UserApplication` 에 그대로 두고, 애플리케이션 서비스가 직접 쓴다(Spring 의존 허용).

- [ ] **Step 1: ArchUnit 의존성과 `ArchitectureTest` 추가** (Global Constraints 의 코드 그대로)
- [ ] **Step 2: 실패 확인**: `./gradlew :user-service:test --tests '*ArchitectureTest'` → FAIL. 지금 `domain.User` 가 `jakarta.persistence` 에 의존하기 때문이다.
- [ ] **Step 3: 위 표대로 옮긴다.** 동작과 HTTP 계약은 바꾸지 않는다.
- [ ] **Step 4: 통과 확인**: `./gradlew :user-service:test` → 전부 PASS.
- [ ] **Step 5: 커밋**: `git commit -m "user-service 를 도메인·애플리케이션·어댑터 구조로 옮김"`

### Task 2: product-service 를 헥사고날 구조로 옮기기

**Files:**
- Modify: `product-service/build.gradle` (ArchUnit)
- Test (create): `product-service/src/test/java/com/flashsale/product/ArchitectureTest.java`
- Move/rewrite:
  - `domain/Product.java`, `domain/FlashSaleEvent.java`: 순수 자바로 바꾼다. JPA 는 `adapter/out/persistence/ProductJpaEntity.java`, `FlashSaleEventJpaEntity.java`(`@ManyToOne ProductJpaEntity`)로 옮긴다.
  - `domain/*Repository.java`: 포트 `application/port/out/ProductRepository.java`, `FlashSaleEventRepository.java` 로 바꾼다. Spring Data 는 `adapter/out/persistence/ProductJpaRepository.java`, `FlashSaleEventJpaRepository.java`, 구현은 `ProductPersistenceAdapter.java`, `FlashSaleEventPersistenceAdapter.java`.
  - `EventService.java` → `application/EventService.java`.
  - `EventNotFoundException.java` → `domain/EventNotFoundException.java`.
  - `api/*` → `adapter/in/web/*`.
- Test: `api/EventControllerTest` → `adapter/in/web/EventControllerTest` (import 만).

**Interfaces:**
- Produces:
  - `domain.Product`: `static Product create(String name, long price)`, `static Product restore(long id, String name, long price)`. 접근자는 `Long id()`, `name()`, `price()`.
  - `domain.FlashSaleEvent`: `static FlashSaleEvent create(Product product, Instant openAt, int saleQuantity)`, `restore(long id, Product product, Instant openAt, int saleQuantity)`. 접근자는 `Long id()`, `product()`, `openAt()`, `saleQuantity()`.
  - `ProductRepository`: `Product save(Product)`.
  - `FlashSaleEventRepository`: `FlashSaleEvent save(FlashSaleEvent)`, `Optional<FlashSaleEvent> findById(long)` (상품까지 채워서 돌려준다).
  - `EventService`: `FlashSaleEvent create(String productName, long price, Instant openAt, int saleQuantity)`, `FlashSaleEvent get(long eventId)`.

- [ ] **Step 1: ArchUnit 의존성과 `ArchitectureTest` 추가**
- [ ] **Step 2: 실패 확인**: `./gradlew :product-service:test --tests '*ArchitectureTest'` → FAIL
- [ ] **Step 3: 위 목록대로 옮긴다.** HTTP 계약은 바꾸지 않는다.
- [ ] **Step 4: 통과 확인**: `./gradlew :product-service:test` → 전부 PASS
- [ ] **Step 5: 커밋**: `git commit -m "product-service 를 도메인·애플리케이션·어댑터 구조로 옮김"`

### Task 3: payment-service 구조와 가짜 PG, 실패율 기본값 10%, 측정 스크립트의 실패율 고정

**Files:**
- Modify: `payment-service/build.gradle` (ArchUnit), `payment-service/src/main/resources/application.yml`
- Test (create): `payment-service/src/test/java/com/flashsale/payment/ArchitectureTest.java`, `.../adapter/out/pg/FakePgDefaultConfigTest.java`
- Move/rewrite:

| 지금 | 옮길 곳 |
|---|---|
| `domain/Payment.java` (JPA), `domain/PaymentStatus.java` | `domain/Payment.java` (순수), `domain/PaymentStatus.java` + `adapter/out/persistence/PaymentJpaEntity.java` |
| `domain/PaymentRepository.java` | `application/port/out/PaymentRepository.java` + `adapter/out/persistence/PaymentJpaRepository.java` + `PaymentPersistenceAdapter.java` |
| `PaymentService.java`, `PaymentResult.java` | `application/PaymentService.java`, `application/PaymentResult.java` |
| `simulation/PaymentSimulator.java`, `SimulationConfig.java` | `adapter/out/pg/FakePaymentGateway.java`, `adapter/out/pg/FakePgConfig.java` + 포트 `application/port/out/PaymentGateway.java` |
| `api/PaymentController.java` 의 `/config` 두 메서드, `api/ConfigRequest.java` | `adapter/out/pg/FakePgConfigController.java`, `adapter/out/pg/FakePgConfigRequest.java` |
| `api/*` 나머지 | `adapter/in/web/*` |

- Modify: `scripts/lib.sh`, `scripts/run-test.sh`, `scripts/smoke.sh`
- Tests:
  - `simulation/PaymentSimulatorTest` → `adapter/out/pg/FakePaymentGatewayTest` (같은 경우들).
  - `api/PaymentControllerTest` → `adapter/in/web/PaymentControllerTest`. `@BeforeEach` 에서 설정을 `{"delayMs":0,"jitterMs":0,"failureRate":0.0}` 로 PUT 한다. 기본값이 0.1 이 되므로 이걸 하지 않으면 테스트가 흔들린다.

**Interfaces:**
- Produces:
  - `PaymentGateway`: `PaymentStatus pay(long orderId, long userId, long amount)`.
  - `PaymentRepository`: `Payment save(Payment)`, `void deleteAll()`.
  - `domain.Payment`: `static Payment record(long orderId, long userId, long amount, PaymentStatus status, Instant now)`, `restore(...)`. 접근자는 `Long id()` 등.
  - `PaymentService.pay(long orderId, long userId, long amount) -> PaymentResult(long paymentId, PaymentStatus status)`.
    - 트랜잭션 없이 실행한다(PG 지연 동안 커넥션을 쥐지 않는다).
    - 순서: `gateway.pay` → `repository.save`.
  - `FakePaymentGateway`:
    - `@ConditionalOnProperty(name = "payment.gateway", havingValue = "fake", matchIfMissing = true)`.
    - 메서드: `FakePgConfig current()`, `void update(FakePgConfig)`, `Duration nextDelay()`, `boolean shouldDecline()`. 동작은 지금 `PaymentSimulator` 와 같다.
    - `FakePgConfigController` 도 같은 조건을 붙인다. 가짜 PG 를 빼면 설정 API 도 같이 빠진다.
  - `FakePgConfig(long delayMs, long jitterMs, double failureRate)`. JSON 필드 이름은 지금과 같다.
  - `application.yml`: `payment.gateway: ${PAYMENT_GATEWAY:fake}`, `payment.failure-rate: ${PAYMENT_FAILURE_RATE:0.1}`. `delay-ms` 200 과 `jitter-ms` 50 은 그대로다.
  - `scripts/lib.sh`: `set_payment_failure_rate <rate>`. 지금 설정을 GET 하고 `failureRate` 만 바꿔 PUT 한다. 실패하면 비-0 을 반환한다.

- [ ] **Step 1: 실패하는 테스트**: `ArchitectureTest`, 그리고 `FakePgDefaultConfigTest`.

```java
@SpringBootTest @AutoConfigureMockMvc @Import(TestcontainersConfiguration.class)
class FakePgDefaultConfigTest {
    @Test
    void defaultFailureRateIsTenPercent() throws Exception {
        mvc.perform(get("/internal/payments/config"))
            .andExpect(jsonPath("$.delayMs").value(200))
            .andExpect(jsonPath("$.jitterMs").value(50))
            .andExpect(jsonPath("$.failureRate").value(0.1));
    }
}
```

- [ ] **Step 2: 실패 확인**: `./gradlew :payment-service:test --tests '*ArchitectureTest' --tests '*FakePgDefaultConfigTest'` → FAIL
- [ ] **Step 3: 위 표대로 옮기고 `application.yml` 을 바꾼다.**
- [ ] **Step 4: 통과 확인**: `./gradlew :payment-service:test` → 전부 PASS
- [ ] **Step 5: 측정·스모크 스크립트가 실패율을 고정하게 한다.**
  - `lib.sh` 에 `set_payment_failure_rate` 를 추가한다.
  - `run-test.sh` 는 `wait_healthy` 바로 뒤에 `set_payment_failure_rate 0 || { echo "결제 실패율을 0 으로 고정하지 못했다" >&2; exit 1; }` 를 부른다.
  - `smoke.sh` 는 `wait_healthy` 바로 뒤에 같은 호출을 넣는다.
  - `run-test.sh` 상단 주석에 "결제 실패율은 실행 전에 0 으로 고정한다(기본값 0.1)" 를 한 줄 더한다.
- [ ] **Step 6: 실제 스택에서 확인** (Review Focus 1)
  - 실행: `docker compose up -d --build payment-service` → `curl -fsS -X PUT localhost:8085/internal/payments/config -H 'Content-Type: application/json' -d '{"delayMs":200,"jitterMs":50,"failureRate":1.0}'` → `scripts/smoke.sh`.
  - 기대 1: `SMOKE OK`.
  - 기대 2: 이어서 `curl -fsS localhost:8085/internal/payments/config | jq .failureRate` 가 `0`.
  - 판정 스크립트 테스트도 돌린다: `scripts/test-verdict.sh` → 전부 통과.
- [ ] **Step 7: 커밋**: `git commit -m "payment-service 구조 정리와 가짜 PG(기본 실패율 10%), 측정·스모크 때 실패율 0 고정"`

### Task 4: inventory-service 를 헥사고날 구조로 옮기기

**Files:**
- Modify: `inventory-service/build.gradle` (ArchUnit)
- Test (create): `inventory-service/src/test/java/com/flashsale/inventory/ArchitectureTest.java`, `.../domain/InventoryTest.java`
- Move/rewrite:

| 지금 | 옮길 곳 |
|---|---|
| `domain/Inventory.java` (JPA) | `domain/Inventory.java` (순수) + `adapter/out/persistence/InventoryJpaEntity.java` (`product_id`, `quantity` 만 매핑. `version` 은 매핑하지 않음, `@Version` 금지) |
| `domain/InventoryRepository.java` | `application/port/out/InventoryRepository.java` + `adapter/out/persistence/InventoryJpaRepository.java` + `InventoryPersistenceAdapter.java` |
| `stock/StockStrategy.java` | `application/port/out/StockStrategy.java` (시그니처 그대로) |
| `stock/DecreaseResult.java`, `stock/InventoryNotFoundException.java` | `domain/` |
| `stock/StockStats.java` | `application/StockStats.java` (Micrometer `MeterBinder` 그대로) |
| `stock/*StockStrategy.java` 4개, `stock/OptimisticProperties.java` | `adapter/out/persistence/` |
| `api/*` | `adapter/in/web/*`. 컨트롤러는 새 `application/InventoryService.java` 와 `StockStats` 만 부른다 |

- Tests:
  - `stock/*` 테스트(계약, naive, pessimistic, conditional, optimistic 계열, `ForcedConflict`) → `adapter/out/persistence/`.
  - `StockStatsTest` → `application/`.
  - `api/InventoryControllerTest` → `adapter/in/web/`.
  - 바꾸는 것은 패키지·import, 그리고 `Inventory` → `InventoryJpaEntity`, `InventoryRepository` → `InventoryJpaRepository` 같은 타입 이름뿐이다.

**Interfaces:**
- Produces:
  - `domain.Inventory(long productId, int quantity)`: record. 수량이 음수면 `IllegalArgumentException`.
  - `InventoryRepository`: `Optional<Inventory> findById(long productId)`, `void save(Inventory)`. save 는 있으면 수량만 갱신하고, 없으면 만든다. `version` 은 건드리지 않는다.
  - `InventoryService`:
    - `Inventory set(long productId, int quantity)`
    - `Inventory get(long productId)` (없으면 `InventoryNotFoundException`)
    - `DecreaseResult decrease(long productId, int quantity)`, `void increase(long productId, int quantity)` (모두 `StockStrategy` 로 위임)
    - `String strategyName()`
  - Task 7 이 `InventoryService` 에 일괄 메서드를 더한다.

- [ ] **Step 1: 실패하는 테스트**: `ArchitectureTest`, 그리고 `InventoryTest.negativeQuantityIsRejected`. `assertThatThrownBy(() -> new Inventory(1, -1)).isInstanceOf(IllegalArgumentException.class)`.
- [ ] **Step 2: 실패 확인**: `./gradlew :inventory-service:test --tests '*ArchitectureTest' --tests '*InventoryTest'` → FAIL
- [ ] **Step 3: 위 표대로 옮긴다.**
- [ ] **Step 4: 통과 확인**: `./gradlew :inventory-service:test` → 전부 PASS. `NaiveStockStrategyTest.concurrentDecreasesLoseUpdates` 를 포함한다.
- [ ] **Step 5: 커밋**: `git commit -m "inventory-service 를 도메인·애플리케이션·어댑터 구조로 옮기고 재고 전략을 영속성 어댑터로"`

### Task 5: order-service 를 헥사고날 구조로 옮기고 주문을 항목 모델로 바꾸기

이벤트 주문의 흐름·응답·통계는 바꾸지 않는다. 바뀌는 것은 저장 구조뿐이다(주문 1건 + 항목 1개).

**Files:**
- Modify: `order-service/build.gradle` (ArchUnit)
- Create: `order-service/src/main/resources/db/migration/V2__order_items.sql`

```sql
CREATE TABLE order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    unit_price BIGINT NOT NULL,
    quantity INT NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);
INSERT INTO order_items (order_id, product_id, product_name, unit_price, quantity)
    SELECT id, product_id, '', amount DIV quantity, quantity FROM orders;
ALTER TABLE orders
    MODIFY event_id BIGINT NULL,
    DROP COLUMN product_id,
    DROP COLUMN quantity,
    ADD INDEX idx_orders_user (user_id, id);
```

- Move/rewrite:

| 지금 | 옮길 곳 |
|---|---|
| `domain/Order.java` (JPA) | `domain/Order.java` (순수, 항목 목록) + `domain/OrderLine.java` + `adapter/out/persistence/OrderJpaEntity.java`, `OrderLineJpaEntity.java` |
| `domain/OrderRepository.java` | `application/port/out/OrderRepository.java` + `adapter/out/persistence/OrderJpaRepository.java` (통계 쿼리 두 개 포함) + `OrderPersistenceAdapter.java` |
| `domain/FailReason.java`, `domain/OrderStatus.java` | 그대로 `domain/` |
| `client/ProductClient`, `InventoryClient`, `PaymentClient` | 포트 `application/port/out/ProductPort`, `InventoryPort`, `PaymentPort` + 구현 `adapter/out/http/ProductHttpClient`, `InventoryHttpClient`, `PaymentHttpClient` |
| `client/ClientConfig`, `ClientProperties` | `adapter/out/http/HttpClientConfig`, `ClientProperties` |
| `client/EventInfo`, `StockDecreaseResult`, `PaymentOutcome`, `DownstreamException`, `EventNotFoundException` | `application/port/out/` |
| `application/*` | 그대로 `application/`. `OrderWriter` 는 포트 `OrderRepository` 를 쓴다 |
| `api/*` | `adapter/in/web/*` |
| `config/ClockConfig` | 그대로 |

- Tests:
  - `application/OrderServiceTest`: 포트를 목으로 쓴다. `ReflectionTestUtils` 대신 `Order.restore` 를 쓴다.
  - `api/OrderApiTest` → `adapter/in/web/`: `@MockitoBean` 대상을 포트로 바꾼다.
  - `client/ClientsTest` → `adapter/out/http/HttpClientsTest`, `StubHttpServer` → `adapter/out/http/`.
  - 새로 만들 것: `domain/OrderTest.java`, `adapter/out/persistence/OrderPersistenceAdapterTest.java`, `ArchitectureTest.java`.

**Interfaces:**
- Produces:
  - `domain.OrderLine(long productId, String productName, long unitPrice, int quantity)`: record. `quantity >= 1`, `unitPrice >= 0` 이 아니면 `IllegalArgumentException`. `long subtotal()`.
  - `domain.Order`:
    - 만들기: `static Order pending(long userId, Long eventId, List<OrderLine> lines, Instant now)`. 항목이 비면 `IllegalArgumentException`.
    - 저장에서 복원: `static Order restore(long id, long userId, Long eventId, List<OrderLine> lines, OrderStatus status, FailReason failReason, Instant createdAt, Instant updatedAt)`.
    - 상태 전이: `void markPaid(Instant now)`, `void markFailed(FailReason reason, Instant now)`. `PENDING` 이 아니면 `IllegalStateException`.
    - 접근자: `Long id()`, `long userId()`, `Long eventId()`, `List<OrderLine> lines()`(변경 불가), `long amount()`(항목 소계 합), `status()`, `failReason()`, `createdAt()`, `updatedAt()`.
  - `OrderRepository`:
    - `Order save(Order)`. id 가 없으면 주문과 항목을 넣고 id 를 채운 `Order` 를 돌려준다. id 가 있으면 `status`·`fail_reason`·`updated_at` 만 갱신한다. 항목은 다시 쓰지 않는다.
    - `Optional<Order> findById(long)`
    - `Map<OrderStatus, Long> countByStatus(long eventId)`, `Map<FailReason, Long> countFailedByReason(long eventId)`
    - `void deleteAll()`
    - Task 8 이 조회 메서드를 더한다.
  - `ProductPort`: `EventInfo getEvent(long eventId)`. Task 9 가 `findListed` 를 더한다.
  - `InventoryPort`: `StockDecreaseResult decrease(long productId, int quantity)`, `void increase(long productId, int quantity)`. Task 10 이 일괄 메서드를 더한다.
  - `PaymentPort`: `PaymentOutcome pay(long orderId, long userId, long amount)`.
  - `EventInfo(long eventId, long productId, String productName, long price, Instant openAt, int saleQuantity)`. `productName` 이 새로 들어간다. product-service 응답에 이미 있다.
  - `OrderWriter`(트랜잭션 짧게, 메서드마다): `Order createPending(Order pending)`, `void markPaid(long orderId)`, `void markFailed(long orderId, FailReason reason)`.
  - 이벤트 주문은 `Order.pending(userId, eventId, List.of(new OrderLine(productId, productName, price, 1)), now)` 로 만든다.

- [ ] **Step 1: 실패하는 테스트**

```java
class OrderTest {
    @Test void amountIsSumOfLineSubtotals()      // lines (7,"A",1000,2),(8,"B",500,1) → amount() == 2500
    @Test void pendingRequiresAtLeastOneLine()   // Order.pending(1, null, List.of(), NOW) → IllegalArgumentException
    @Test void markPaidOnlyFromPending()         // markPaid 뒤 markPaid / markFailed → IllegalStateException
    @Test void markFailedKeepsReason()           // markFailed(SOLD_OUT) → status FAILED, failReason SOLD_OUT
}
@SpringBootTest @Import(TestcontainersConfiguration.class)
class OrderPersistenceAdapterTest {
    @Test void savesLinesAndRestoresThem()       // 항목 2개 주문 저장 → findById 의 lines·amount·eventId(null) 가 같다
    @Test void statusChangeKeepsOrderItems()     // 저장 → markPaid → save → order_items 의 id 목록과 행 수가 그대로 (JdbcTemplate 로 확인)
    @Test void statsCountByEventAndReason()      // eventId 1 의 PAID 2·FAILED(SOLD_OUT) 1, eventId 2 의 PAID 1 → countByStatus(1)={PAID=2,FAILED=1}
}
```

  - `HttpClientsTest.getEventParsesInstant` 에 `productName` 확인을 더한다.

- [ ] **Step 2: 실패 확인**: `./gradlew :order-service:test` → 새 테스트들이 컴파일 실패 또는 FAIL
- [ ] **Step 3: 위 표대로 옮기고 V2 마이그레이션을 추가한다.**
- [ ] **Step 4: 통과 확인**: `./gradlew :order-service:test` → 전부 PASS. 기존 `OrderApiTest` 의 이벤트 주문·통계·삭제 경우들을 포함한다.
- [ ] **Step 5: 커밋**: `git commit -m "order-service 를 도메인·애플리케이션·어댑터 구조로 옮기고 주문을 항목 모델로 바꿈"`

### Task 6: product-service 카탈로그 (목록·상세·등록·여러 상품 조회)

**Files:**
- Create: `product-service/src/main/resources/db/migration/V2__products_listed.sql`: `ALTER TABLE products ADD COLUMN listed BOOLEAN NOT NULL DEFAULT FALSE;`
- Create: `application/ProductService.java`, `domain/ProductNotFoundException.java`, `adapter/in/web/ProductController.java`(+ 요청·응답 record)
- Modify: `domain/Product.java`, `application/port/out/ProductRepository.java`, `adapter/out/persistence/*`, `application/EventService.java`, `adapter/in/web/ApiExceptionHandler.java`
- Test: `product-service/src/test/java/com/flashsale/product/adapter/in/web/ProductApiTest.java`

**Interfaces:**
- Consumes: Task 2 의 `Product`, `ProductRepository`, `EventService`.
- Produces:
  - `Product`: `boolean listed()`. 만드는 메서드는 `Product.listed(String name, long price)`(카탈로그용)와 `Product.unlisted(String name, long price)`(이벤트용)다. `restore` 에 `listed` 를 더한다. 이벤트 생성은 `unlisted` 를 쓴다.
  - `ProductRepository` 에 추가하는 메서드:
    - `Optional<Product> findListedById(long)`
    - `List<Product> findListed(int page, int size)` (id 오름차순)
    - `long countListed()`
    - `List<Product> findListedByIds(Collection<Long> ids)`
  - `ProductService`:
    - `Product register(String name, long price)`
    - `Product getListed(long productId)` (없거나 판매 상품이 아니면 `ProductNotFoundException`)
    - `ProductPage list(int page, int size)`. `ProductPage(List<Product> products, int page, int size, long total)` 이다.
    - `List<Product> findListed(Collection<Long> ids)`
  - HTTP:
    - `GET /api/products?page=0&size=20` → `{"products":[{"productId","name","price"}],"page","size","total"}`
    - `GET /api/products/{productId}` → `{"productId","name","price"}`. 없으면 404 `PRODUCT_NOT_FOUND`.
    - `POST /internal/products` `{"name","price"}` → 201 `{"productId"}`. 이름은 공백 아님·200자 이하, 가격은 0 이상이다.
    - `GET /internal/products?ids=1,2,3` → `[{"productId","name","price"}]`. 판매 상품만 담고, 없는 id 는 뺀다. ids 는 1~100개다.
  - `page`·`size` 범위 검사는 메서드 파라미터 검증으로 한다(`@Validated` + `@Min`/`@Max`). 이때 나는 `HandlerMethodValidationException` 을 `ApiExceptionHandler` 가 400 `INVALID_REQUEST` 로 바꾼다.

- [ ] **Step 1: 실패하는 테스트**

```java
class ProductApiTest {
    @Test void listShowsOnlyListedProductsInIdOrder()  // register A,B + 이벤트 1개 → 목록에 A,B 순서, 이벤트 상품 없음, total 2
    @Test void eventProductDetailIs404()               // POST /internal/events 의 productId → GET /api/products/{id} 404 PRODUCT_NOT_FOUND
    @Test void eventLookupStillWorks()                 // GET /api/products/events/{eventId} 200, productName 일치
    @Test void internalLookupReturnsOnlyListed()       // ids=A,이벤트상품,999 → [A] 하나
    @Test void sizeOver100Returns400()                 // size=101 → 400 INVALID_REQUEST
    @Test void registerRejectsNegativePrice()          // price -1 → 400 INVALID_REQUEST
}
```

- [ ] **Step 2: 실패 확인**: `./gradlew :product-service:test --tests '*ProductApiTest'` → FAIL
- [ ] **Step 3: 구현**: 마이그레이션, 도메인 필드, 포트 메서드, 서비스, 컨트롤러
- [ ] **Step 4: 통과 확인**: `./gradlew :product-service:test` → 전부 PASS
- [ ] **Step 5: 커밋**: `git commit -m "상품 카탈로그: 목록·상세·등록 API, 이벤트 상품은 목록에서 제외"`

### Task 7: inventory-service 일괄 차감·복구

**Files:**
- Create:
  - `domain/StockLine.java`
  - `application/port/out/BatchStockStore.java`
  - `adapter/out/persistence/JdbcBatchStockStore.java`
  - `adapter/in/web/BatchStockRequest.java`
- Modify: `application/InventoryService.java`, `adapter/in/web/InventoryController.java`, `adapter/in/web/ApiExceptionHandler.java`
- Test: `inventory-service/src/test/java/com/flashsale/inventory/adapter/out/persistence/JdbcBatchStockStoreTest.java`, `.../adapter/in/web/BatchStockApiTest.java`

**Interfaces:**
- Consumes: Task 4 의 `InventoryService`, `DecreaseResult`, `InventoryNotFoundException`.
- Produces:
  - `domain.StockLine(long productId, int quantity)`: record. `quantity >= 1`.
  - `BatchStockStore`:
    - `DecreaseResult decreaseAll(List<StockLine> lines)`. `SUCCESS` 또는 `OUT_OF_STOCK` 만 돌려준다.
    - `void increaseAll(List<StockLine> lines)`. 재고 행이 없는 상품이 있으면 전부 롤백하고 `InventoryNotFoundException` 을 던진다.
  - `JdbcBatchStockStore`:
    - 트랜잭션 하나에서 **productId 오름차순으로** 처리한다. 차감 문장은 `UPDATE inventory SET quantity = quantity - ? WHERE product_id = ? AND quantity >= ?` 이다.
    - 0행이면 전부 롤백하고 `OUT_OF_STOCK` 이다. 재고 행이 없는 경우도 `OUT_OF_STOCK` 이다.
    - 복구 문장은 `quantity = quantity + ?` 이다.
    - `StockStrategy` 와 `INVENTORY_STRATEGY` 를 쓰지 않는다.
  - `InventoryService`: `DecreaseResult decreaseAll(List<StockLine>)`, `void increaseAll(List<StockLine>)`. 비었거나 productId 가 겹치면 `IllegalArgumentException` 이고, 웹 어댑터가 400 `INVALID_REQUEST` 로 바꾼다.
  - HTTP:
    - `POST /internal/inventory/decrease-all` `{"items":[{"productId":1,"quantity":2}]}` → 200 `{"result":"SUCCESS"|"OUT_OF_STOCK"}`
    - `POST /internal/inventory/increase-all` (같은 본문) → 204. 없는 상품이면 404 `INVENTORY_NOT_FOUND`.

- [ ] **Step 1: 실패하는 테스트** (`@SpringBootTest(properties = "inventory.strategy=naive")`. 전략과 관계없이 정확한지 보기 위해서다.)

```java
class JdbcBatchStockStoreTest {
    @Test void decreasesEveryLineWhenEnough()            // A=5,B=5; [A:2,B:1] → SUCCESS, A=3, B=4
    @Test void outOfStockLeavesEveryRowUnchanged()       // A=5,B=0; [A:2,B:1] → OUT_OF_STOCK, A=5, B=0
    @Test void missingRowIsOutOfStock()                  // [A:1, 999:1] → OUT_OF_STOCK, A 그대로
    @Test void opposingOrderBatchesNeitherDeadlockNorOversell()
        // A=30,B=30; 스레드 80개를 동시에 출발(CountDownLatch): 절반은 [A:1,B:1], 절반은 [B:1,A:1]
        // → 예외 0, SUCCESS 정확히 30, A=0, B=0
    @Test void increaseAllRestoresEveryLine()             // A=3,B=4; increaseAll [A:2,B:1] → A=5, B=5
    @Test void increaseAllWithUnknownProductRollsBack()   // A=3; [A:1, 999:1] → InventoryNotFoundException, A=3
}
class BatchStockApiTest {
    @Test void duplicateProductIdsReturn400()   // [A:1, A:1] → 400 INVALID_REQUEST
    @Test void emptyItemsReturn400()            // [] → 400
    @Test void decreaseAllReturnsResult()       // 200 {"result":"SUCCESS"}
}
```

- [ ] **Step 2: 실패 확인**: `./gradlew :inventory-service:test --tests '*BatchStock*'` → FAIL
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**: `./gradlew :inventory-service:test` → 전부 PASS
- [ ] **Step 5: 커밋**: `git commit -m "재고 일괄 차감·복구: 트랜잭션 하나의 조건부 UPDATE, 상품 id 순서"`

### Task 8: order-service 주문 내역 조회

**Files:**
- Create:
  - `application/OrderQueryService.java`, `application/OrderPage.java`
  - `domain/OrderNotFoundException.java`
  - `adapter/in/web/OrderHistoryResponses.java`. 목록·상세 응답 record 를 중첩으로 둔다.
- Modify: `application/port/out/OrderRepository.java`, `adapter/out/persistence/*`, `adapter/in/web/OrderController.java`, `adapter/in/web/ApiExceptionHandler.java`
- Test: `order-service/src/test/java/com/flashsale/order/adapter/in/web/OrderHistoryApiTest.java`

**Interfaces:**
- Consumes: Task 5 의 `Order`, `OrderLine`, `OrderRepository`.
- Produces:
  - `OrderRepository` 에 추가하는 메서드:
    - `List<Order> findByUserId(long userId, int page, int size)`. id 내림차순(최신순)이다. 한 페이지를 고정된 개수의 쿼리로 읽고, 주문마다 항목 쿼리를 하지 않는다.
    - `long countByUserId(long userId)`
    - `Optional<Order> findByIdAndUserId(long orderId, long userId)`
  - `OrderQueryService`:
    - `OrderPage myOrders(long userId, int page, int size)`. `OrderPage(List<Order> orders, int page, int size, long total)` 이다.
    - `Order myOrder(long userId, long orderId)`. 없거나 남의 주문이면 `OrderNotFoundException` 이다.
  - HTTP (`X-User-Id` 필요):
    - `GET /api/orders?page=0&size=20` → `{"orders":[{"orderId","status","reason","amount","createdAt","itemCount","firstProductName"}],"page","size","total"}`
    - `GET /api/orders/{orderId}` → `{"orderId","status","reason","amount","createdAt","eventId","items":[{"productId","productName","unitPrice","quantity","subtotal"}]}`. 404 이면 `ORDER_NOT_FOUND`.
  - `page`·`size` 검증은 Task 6 과 같다.

- [ ] **Step 1: 실패하는 테스트**

```java
class OrderHistoryApiTest {
    @Test void listsOnlyMyOrdersNewestFirst()      // 사용자 5 주문 3건, 사용자 6 주문 1건 → 5 의 목록 3건, orderId 내림차순, total 3
    @Test void paginates()                         // size=1&page=1 → 두 번째로 최신인 주문 1건
    @Test void detailHasItemsAndAmount()           // 항목 2개(1000×2, 500×1) → amount 2500, items 2개, subtotal 2000/500
    @Test void otherUsersOrderReturns404()         // 사용자 6 이 5 의 주문 조회 → 404 ORDER_NOT_FOUND
    @Test void eventOrderAppearsInHistory()        // POST /api/orders 로 만든 주문 → 목록에 있고 eventId 가 채워짐
    @Test void sizeOver100Returns400()
}
```

- [ ] **Step 2: 실패 확인**: `./gradlew :order-service:test --tests '*OrderHistoryApiTest'` → FAIL
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**: `./gradlew :order-service:test` → 전부 PASS
- [ ] **Step 5: 커밋**: `git commit -m "주문 내역 조회 API: 내 주문 목록(최신순)과 상세"`

### Task 9: order-service 장바구니

**Files:**
- Create:
  - `order-service/src/main/resources/db/migration/V3__cart_items.sql`: `CREATE TABLE cart_items (user_id BIGINT NOT NULL, product_id BIGINT NOT NULL, quantity INT NOT NULL, PRIMARY KEY (user_id, product_id));`
  - `domain/Cart.java`, `domain/CartItem.java`, `domain/InvalidQuantityException.java`
  - `application/CartService.java`, `application/CartView.java`, `application/ProductUnavailableException.java`
  - `application/port/out/CartRepository.java`, `application/port/out/CatalogProduct.java`
  - `adapter/out/persistence/CartPersistenceAdapter.java`. 엔티티를 쓸지 JdbcTemplate 을 쓸지는 빌더가 고른다.
  - `adapter/in/web/CartController.java` (+ 요청·응답 record)
- Modify: `application/port/out/ProductPort.java`, `adapter/out/http/ProductHttpClient.java`, `adapter/in/web/ApiExceptionHandler.java`
- Test: `.../domain/CartTest.java`, `.../adapter/in/web/CartApiTest.java`, `.../adapter/out/http/HttpClientsTest.java` (추가)

**Interfaces:**
- Consumes: Task 6 의 `GET /internal/products?ids=` 응답 `[{"productId","name","price"}]`.
- Produces:
  - `domain.CartItem(long productId, int quantity)`.
  - `domain.Cart`:
    - 만들기: `static Cart empty(long userId)`, `static Cart restore(long userId, List<CartItem> items)`.
    - 변경: `void put(long productId, int quantity)` (1~99 밖이면 `InvalidQuantityException`, 같은 상품은 수량을 바꿈), `void remove(long productId)` (없으면 아무것도 안 함), `void removeAll(Collection<Long> productIds)`.
    - 조회: `List<CartItem> items()` (담은 순서), `boolean isEmpty()`, `long userId()`.
  - `CartRepository`: `Cart findByUserId(long userId)` (없으면 빈 장바구니), `void save(Cart cart)`. save 는 그 사용자의 줄을 트랜잭션 하나에서 통째로 바꾼다.
  - `CatalogProduct(long productId, String name, long price)`.
  - `ProductPort` 에 `List<CatalogProduct> findListed(Collection<Long> productIds)` 를 추가한다. `GET /internal/products?ids=…` 를 부르고, 실패하면 `DownstreamException` 이다.
  - `CartService`:
    - `CartView view(long userId)`. 빈 장바구니면 상품 호출을 하지 않는다.
    - `CartView put(long userId, long productId, int quantity)`. 판매 상품이 아니면 `ProductUnavailableException` 이다.
    - `void remove(long userId, long productId)`.
  - `CartView(List<Line> items, long total)`, `CartView.Line(long productId, String name, Long unitPrice, int quantity, Long subtotal, boolean available)`. 판매 상품이 아닌 줄은 `name`·`unitPrice`·`subtotal` 이 null, `available=false` 이고 합계에서 빠진다.
  - HTTP (`X-User-Id` 필요):
    - `GET /api/cart` → `{"items":[{"productId","name","unitPrice","quantity","subtotal","available"}],"total"}`
    - `PUT /api/cart/items/{productId}` `{"quantity"}` → 200 장바구니 응답
    - `DELETE /api/cart/items/{productId}` → 204
  - 오류: `PRODUCT_UNAVAILABLE` 400. `InvalidQuantityException` 이나 검증 실패는 400 `INVALID_REQUEST`. product-service 실패는 기존 `DOWNSTREAM_ERROR` 502.

- [ ] **Step 1: 실패하는 테스트**

```java
class CartTest {
    @Test void putAddsThenReplacesQuantity()          // put(7,2), put(7,5) → items == [(7,5)]
    @Test void quantityOutside1To99IsRejected()       // put(7,0), put(7,100) → InvalidQuantityException
    @Test void removeMissingIsNoop()
    @Test void removeAllRemovesOnlyGiven()            // (7,1),(8,1),(9,1) removeAll([7,9]) → [(8,1)]
}
class CartApiTest {   // ProductPort 는 @MockitoBean
    @Test void putThenGetShowsPricesAndTotal()        // A(1000)×2, B(500)×1 → total 2500, subtotal 2000/500
    @Test void unlistedProductReturns400()            // findListed 가 빈 목록 → PUT 400 PRODUCT_UNAVAILABLE, 장바구니 그대로
    @Test void unavailableLineShownButExcludedFromTotal()  // 담은 뒤 B 가 판매 중지 → B available=false, total 2000
    @Test void deleteRemovesLine()
    @Test void cartsAreSeparatedByUser()
    @Test void missingUserIdReturns401()
}
// HttpClientsTest 에 추가: findListedSendsIdsAndParsesProducts — StubHttpServer 의 /internal/products 가 받은 쿼리 ids=7,8, 결과 2개 파싱
```

- [ ] **Step 2: 실패 확인**: `./gradlew :order-service:test --tests '*Cart*' --tests '*HttpClientsTest'` → FAIL
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**: `./gradlew :order-service:test` → 전부 PASS
- [ ] **Step 5: 커밋**: `git commit -m "장바구니: 담기·수량 바꾸기·빼기·보기"`

### Task 10: order-service 장바구니 주문

**Files:**
- Create:
  - `application/CheckoutService.java`, `application/CartEmptyException.java`
  - `application/port/out/StockLine.java`
  - `adapter/in/web/CheckoutController.java`. 또는 `CartController` 에 메서드를 추가해도 된다.
- Modify: `application/port/out/InventoryPort.java`, `adapter/out/http/InventoryHttpClient.java`, `adapter/in/web/ApiExceptionHandler.java`. `OrderController` 의 응답 상태 결정(`statusOf`)은 두 컨트롤러가 함께 쓰도록 `FailReasonStatus` 옆으로 옮긴다.
- Test: `.../application/CheckoutServiceTest.java`, `.../adapter/in/web/CheckoutApiTest.java`, `HttpClientsTest` (추가)

**Interfaces:**
- Consumes:
  - Task 5: `Order`, `OrderLine`, `OrderWriter`, `PaymentPort`, `OrderResult`, `PreOrderFailedException`
  - Task 7: `POST /internal/inventory/decrease-all|increase-all`
  - Task 9: `CartRepository`, `ProductPort.findListed`, `ProductUnavailableException`
- Produces:
  - `StockLine(long productId, int quantity)` (order-service 쪽 포트 타입).
  - `InventoryPort` 에 추가하는 메서드: `StockDecreaseResult decreaseAll(List<StockLine> lines)`, `void increaseAll(List<StockLine> lines)`.
  - `CheckoutService.checkout(long userId) -> OrderResult`. 스펙 §5.3 의 1~7 단계를 그대로 따른다. 원격 호출은 트랜잭션 밖에서 한다.
    - 빈 장바구니 → `CartEmptyException` → 400 `CART_EMPTY`. 주문을 만들지 않는다.
    - 판매 상품이 아닌 줄이 있음 → `ProductUnavailableException` → 400 `PRODUCT_UNAVAILABLE`. 주문을 만들지 않는다.
    - product 호출 실패, 주문 저장 커넥션 타임아웃 → `new PreOrderFailedException(PRODUCT_TIMEOUT|PRODUCT_ERROR|ORDER_DB_TIMEOUT, cause)`. 이벤트 통계인 `PreOrderFailures` 에는 기록하지 않는다.
    - 일괄 차감 결과:
      - `OUT_OF_STOCK` → `SOLD_OUT`
      - `CONFLICT` → `STOCK_CONFLICT` (일어나지 않아야 하지만 방어)
      - `DownstreamException` → `INVENTORY_TIMEOUT|INVENTORY_ERROR`, 복구 없음
    - 결제 결과:
      - `DECLINED` → `increaseAll` 후 `PAYMENT_DECLINED`. 복구 실패는 로그만 남긴다.
      - `DownstreamException` → `PAYMENT_TIMEOUT|PAYMENT_ERROR`, 복구 없음
    - `PAID` → 장바구니를 **다시 읽어** 주문에 들어간 productId 줄만 `removeAll` 하고 저장한다. 실패하면 장바구니는 그대로다.
  - HTTP: `POST /api/cart/checkout` → `{orderId,status,reason}`. 상태 코드는 이벤트 주문과 같은 규칙이다(`PAID` 201, `FAILED` 는 사유별).

- [ ] **Step 1: 실패하는 테스트**

```java
class CheckoutServiceTest {   // 포트 전부 목, Clock 고정
    @Test void paidCopiesPricesAndChargesTotal()          // A(1000)×2,B(500)×1 → pay(…, 2500), 주문 lines 단가 1000/500, PAID
    @Test void paidRemovesOnlyOrderedLines()               // 결제 뒤 다시 읽은 장바구니에 C 가 더해져 있음 → 저장된 장바구니 == [C]
    @Test void declinedRestoresAllAndKeepsCart()           // DECLINED → increaseAll([A:2,B:1]) 1회, FAILED PAYMENT_DECLINED, cartRepository.save 호출 없음
    @Test void soldOutDoesNotPayOrRestore()                // OUT_OF_STOCK → pay·increaseAll 호출 없음, SOLD_OUT
    @Test void inventoryUnknownDoesNotRestore()            // decreaseAll 이 timeout DownstreamException → INVENTORY_TIMEOUT, increaseAll 없음
    @Test void paymentUnknownDoesNotRestore()              // pay 가 DownstreamException(오류) → PAYMENT_ERROR, increaseAll 없음
    @Test void emptyCartCreatesNoOrder()                   // CartEmptyException, orderWriter 호출 없음
    @Test void unlistedProductCreatesNoOrderAndNoStockCall() // findListed 에서 B 빠짐 → ProductUnavailableException, orderWriter·decreaseAll 호출 없음
}
class CheckoutApiTest {   // ProductPort·InventoryPort·PaymentPort 는 @MockitoBean, DB 는 Testcontainers
    @Test void paidReturns201AndAppearsInHistory()   // 201 PAID → GET /api/orders/{id} items 2개, GET /api/cart items 비어 있음
    @Test void declinedReturns402AndKeepsCart()      // 402 PAYMENT_DECLINED, 장바구니 그대로
    @Test void emptyCartReturns400()                 // 400 CART_EMPTY, orders 행 0
}
// HttpClientsTest 에 추가: decreaseAllSendsItemsAndParsesResult, increaseAllSendsItems
```

- [ ] **Step 2: 실패 확인**: `./gradlew :order-service:test --tests '*Checkout*' --tests '*HttpClientsTest'` → FAIL
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**: `./gradlew :order-service:test` → 전부 PASS
- [ ] **Step 5: 커밋**: `git commit -m "장바구니 주문: 일괄 차감, 결제 거절 시 전부 복구, 성공 시 주문한 줄만 장바구니에서 제거"`

### Task 11: gateway 에 장바구니 라우트 추가

**Files:**
- Modify: `gateway/src/main/java/com/flashsale/gateway/GatewayRoutes.java`. `orderRoute` 의 경로 조건을 `path("/api/orders/**").or(path("/api/cart/**"))` 로 바꾼다. 인증·하류 타임아웃 필터는 그대로다.
- Test: `gateway/src/test/java/com/flashsale/gateway/GatewayRoutingTest.java`

- [ ] **Step 1: 실패하는 테스트**

```java
@Test void cartWithoutTokenReturns401AndSkipsDownstream()  // GET /api/cart → 401 UNAUTHORIZED, ORDER.hits() == 0
@Test void cartWithValidTokenForwardsUserId()             // 토큰 subject "42" → ORDER 가 받은 X-User-Id == "42", 경로 /api/cart/items/7
```

- [ ] **Step 2: 실패 확인**: `./gradlew :gateway:test --tests '*GatewayRoutingTest'` → FAIL (404)
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**: `./gradlew :gateway:test` → 전부 PASS
- [ ] **Step 5: 커밋**: `git commit -m "게이트웨이: /api/cart 를 인증 뒤 order-service 로"`

### Task 12: 사용자 흐름 스모크 스크립트와 README

**Files:**
- Create: `scripts/smoke-shop.sh`
- Modify: `scripts/lib.sh` (`create_product <name> <price>` → productId 출력), `README.md`

**Interfaces:**
- Consumes: Task 3 의 `set_payment_failure_rate`, Task 6·8·9·10·11 의 API, 기존 `create_event`·`set_stock`.
- `smoke-shop.sh` 동작 (실패하면 `SHOP SMOKE FAIL: <단계> <http 코드> <본문 앞 300자>` 를 찍고 exit 1, 끝까지 가면 `SHOP SMOKE OK`)
  1. **준비**
     - `wait_healthy`.
     - 지금 결제 설정을 저장하고, `trap` 으로 끝날 때 되돌린다.
     - 상품 A(가격 1000)·B(500)를 `create_product` 로 만들고 재고를 각 5 로 둔다.
     - 이벤트 하나를 `create_event 1` 로 만든다.
  2. **회원**: `smoke-<epoch초>-<RANDOM>@test.local` 로 게이트웨이를 통해 회원가입하고 로그인해 토큰을 받는다.
  3. **상품 조회**
     - `GET /api/products/{A}` 200, 가격 1000.
     - `GET /api/products/{이벤트상품}` 404.
     - `GET /api/products` 200.
  4. **장바구니 담기**
     - `PUT /api/cart/items/{이벤트상품}` → 400 `PRODUCT_UNAVAILABLE`.
     - A×2, B×1 을 담는다. `GET /api/cart` 의 `total` 이 2500 이다.
  5. **결제 거절 경로**
     - `set_payment_failure_rate 1.0` → `POST /api/cart/checkout` 402 `PAYMENT_DECLINED`.
     - 재고 A=5·B=5 (`GET /internal/inventory/{id}`), 장바구니 줄 2개.
  6. **결제 성공 경로**
     - `set_payment_failure_rate 0` → checkout 201 `PAID`.
     - 재고 A=3·B=4, 장바구니 비어 있음.
  7. **주문 내역**
     - `GET /api/orders?size=2` 의 첫 주문이 `PAID`·`amount` 2500, 둘째가 `FAILED`·`PAYMENT_DECLINED` 다.
     - 첫 주문 상세의 `items` 가 2개다.
- `README.md` 에 넣을 것
  - "2단계: 이커머스 기본 기능" 절:
    - 사용자 API 표
    - 서비스 안 패키지 구조 한 문단
    - 결제 실패율 기본 10%·`PAYMENT_FAILURE_RATE`·측정과 스모크 때 0 고정
    - 장바구니 주문의 일괄 차감은 `INVENTORY_STRATEGY` 와 관계없이 조건부 UPDATE
    - `scripts/smoke-shop.sh` 사용법
  - 로드맵 한 줄: Redis 단계는 근거가 생길 때까지 보류.
  - 이름 구분 한 줄: 기존 "2단계 GC 실험"·"2단계: 게이트웨이 메모리" 는 로드맵을 바꾸기 전 1단계 브랜치에서 한 추가 실험이다. 절 제목은 그대로 둔다.

- [ ] **Step 1: 스크립트 작성** (`chmod +x`)
- [ ] **Step 2: 스택을 새 코드로 올린다**: `docker compose up -d --build`. 이전 스택이 떠 있으면 Flyway V2·V3 가 기존 볼륨에 적용된다.
- [ ] **Step 3: 확인**: `scripts/smoke.sh` → `SMOKE OK`, `scripts/smoke-shop.sh` → `SHOP SMOKE OK`. 두 번 연달아 돌려도 통과해야 한다.
- [ ] **Step 4: README 갱신**
- [ ] **Step 5: 커밋**: `git commit -m "사용자 흐름 스모크 스크립트와 2단계 README"`

### Task 13: 선착순 회귀 매트릭스

**Files:**
- Create:
  - `docs/results/stage2-ecommerce-regression.md`
  - `docs/results/runs/<RUN_ID>/` (스크립트가 만든다)
- Modify:
  - `docs/results/README.md`: 결과 표 절을 추가한다.
  - `README.md`: summarize 재현 명령 표에 한 줄을 추가한다.

- [ ] **Step 1: 준비**
  - Task 12 의 스택이 떠 있어야 한다.
  - 9090/3000 포트를 다른 컨테이너가 쓰면 `PROMETHEUS_PORT`/`GRAFANA_PORT` 로 옮긴다.
  - 시작 시각을 적는다: `START=$(date +%Y%m%d-%H%M)`.
- [ ] **Step 2: 측정**: `scripts/run-matrix.sh`. 7개 구성, 워밍업 1회 + 측정 3회이고, 기본값 USERS 500, ARRIVAL_SECONDS 1, 재고 100 이다. 실패하면 스크립트가 멈춘다. 그때는 원인을 적고 사용자에게 보고한다. 설정을 바꿔 다시 돌리지 않는다.
- [ ] **Step 3: 확인**
  - 실행: `FROM=$START TO=<끝 시각> scripts/summarize.sh`.
  - 판정: 정확한 6개 구성 18회 모두 `OK`, naive 3회 모두 `OVERSOLD`, `INCONCLUSIVE` 0.
  - 실패율: 모든 측정 실행에서 결제 실패율이 0 이다. 결제 설정은 `meta.json` 이 아니라 `report.md` 의 `- payment 설정:` 줄에 남는다(스펙 §5.5 의 "meta.json" 표현을 이것으로 바로잡는다). `grep -h "payment 설정" docs/results/runs/<이번 RUN_ID들>/report.md` 의 모든 줄에 `"failureRate":0` 이 있어야 한다.
  - 하나라도 다르면 멈추고 보고한다.
- [ ] **Step 4: 결과 문서** `stage2-ecommerce-regression.md`
  - 목적: 구조 변경 뒤에도 판정이 그대로인지 확인한다.
  - 환경: 1단계 5차·게이트웨이 메모리와 같은 고정 설정, 이 커밋의 코드.
  - summarize 표 전체.
  - 게이트웨이 메모리 측정(`FROM=20261103-2235 TO=20261103-2300 scripts/summarize.sh`)과 구성별 p95 중앙값 비교 표. 이벤트 주문이 `order_items` 를 하나 더 쓴다는 점을 적는다. 차이의 원인은 기록된 지표로 말할 수 있는 만큼만 쓴다.
  - 재현 명령.
- [ ] **Step 5: 확인과 커밋**
  - `git grep -nE "/(U)sers/"` → 출력 없음.
  - `git add docs/results README.md && git commit -m "2단계 선착순 회귀 매트릭스: 구조 변경 뒤에도 판정 동일 확인"`.
