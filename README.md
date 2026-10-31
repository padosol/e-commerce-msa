# 선착순 한정 판매 MSA 부하 테스트

실제 배포 없이 로컬에서 MSA 이커머스를 만들고, k6 로 선착순 한정 판매 시나리오를 돌리면서 문제를 직접 재현하고 단계적으로 고쳐 나가는 실험·학습 프로젝트다. 실패 기준은 하나, **재고보다 많이 팔리면 실패**다. 0단계는 일부러 순진하게(naive) 구현해 초과 판매가 실제로 터지는 것을 확인하고, 이후 단계마다 해결책을 하나씩 적용해 같은 시나리오로 다시 측정·비교한다. 한 번에 한 문제만 다룬다.

## 준비물

- JDK 25
- Docker Desktop (VM 메모리 8GB 이상)
- k6, jq, curl

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

예: `USERS=200 SALE_QUANTITY=20 scripts/run-test.sh`. 마지막 줄에 verdict(`OK` / `OVERSOLD` 등)가 출력된다.

종료는 `docker compose down`.

## 재고 전략 교체

```bash
./gradlew :inventory-service:bootJar && INVENTORY_STRATEGY=<name> docker compose up -d --build inventory-service
```

전략을 바꾸려면 이미지를 다시 빌드해야 한다. 유효한 이름은 현재 `naive` 하나뿐이다.
`up` 직후 첫 실행은 콜드 스타트라 느리다(기준선 1회차 p95 12.8s, 이후 8.8s / 7.6s).

현재 전략 이름은 결과의 RUN_ID 와 표의 strategy 열에 기록된다.

## 모니터링

- Grafana: http://localhost:3000/d/flash-sale (익명 접근)
- Prometheus: http://localhost:9090

## 결과

`docs/results/` 에 실행마다 `runs/<RUN_ID>/`(k6 요약, 검증, 주문 통계, report.md)가 쌓이고, `docs/results/README.md` 표에 한 줄씩 추가된다.

0단계 naive 기준선(USERS 5000, 재고 100, 3회): 모두 `OVERSOLD`, 판매 수량 423 / 566 / 720.
