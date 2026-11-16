# 2단계: 선착순 회귀 매트릭스 (이커머스 기능 추가 뒤)

## 목적

2단계에서 모든 서비스를 헥사고날 패키지로 바꾸고 쇼핑 기능을 더했다.
구조를 바꾼 뒤에도 선착순 주문의 판정이 그대로인지 1단계와 같은 매트릭스로 확인한다.

## 환경

- 커밋 4161ac4. 고정 설정은 `docs/results/README.md` 와 같다(500명 / 1초, 재고 100, 결제 지연 200ms·실패율 0). 게이트웨이는 2단계 메모리 설정(1280m, swap 없음)이다.
- 스택을 새로 띄운 직후에 쟀다. 그 전에는 smoke 요청만 있었다.
- 다른 프로젝트의 컨테이너(`lol-tempo`, `lol-grafana`, `lol-prometheus`, `lol-postgres`, `lol-redis`, `lol-rabbitmq`)가 같이 떠 있었다. 건드리지 않았다.
- `lol-rabbitmq` 는 22:42 에 CPU 를 약 68% 썼다. 1차 매트릭스 직후(22:43)에는 38%, 진단 재측정 뒤(22:45)에는 2% 였다. 측정 중 값은 따로 재지 않았다.
- 호스트 포트 8080 은 다른 프로젝트의 java 프로세스가 쓰고 있어, 게이트웨이를 호스트 18080 으로 열었다(compose override 는 저장소 밖). k6 는 Docker 네트워크 안에서 `gateway:8080` 으로 직접 가므로 측정 경로가 아니다.
- 호스트 load average 는 3.5~4.0 이었다(`uptime`).
- order-service 핫패스는 1단계와 다르다.
  - `createPending` 이 `orders` 와 `order_items` 한 줄을 INSERT 한다.
  - `markPaid`/`markFailed` 는 `order_items` 를 left join 하는 SELECT 로 주문을 읽고, status·fail_reason·updated_at 만 UPDATE 한다.
  - `POST /api/orders` 를 프록시로 감싸던 클래스 수준 `@Validated` 는 측정 전에 지웠다.

## 1차 매트릭스 (본 기록)

2026-11-16 22:30~22:41. `scripts/run-matrix.sh` 7개 구성 × (워밍업 1 + 측정 3).
`FROM=20261116-2230 TO=20261116-2243 scripts/summarize.sh` 출력 그대로다.

| 구성 | 판정(3회) | paid | undersold | conflicts | timeouts | retries | p95(ms) | p99(ms) | rps | inventory 차감 서버 p95(ms) | MySQL 락 대기 횟수 | 평균 락 대기(ms) | inventory Hikari pending 최대 | inventory Tomcat busy 최대 | 결제 호출 수 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| naive | OVERSOLD/OVERSOLD/OVERSOLD | 444 | 0 | 0 | 0 | 0 | 939.77 | 1037.19 | 287.77 | 12.58 | 365 | 3.64 | 0 | 6 | 444 |
| pessimistic | OK/OK/OK | 100 | 0 | 0 | 0 | 0 | 706.01 | 834.66 | 353.17 | 53.34 | 275 | 5.61 | 0 | 2 | 100 |
| conditional | OK/OK/OK | 100 | 0 | 0 | 0 | 0 | 791.49 | 890.91 | 330.41 | 108.41 | 126 | 21.41 | 0 | 2 | 100 |
| optimistic-r0 | UNDERSOLD/UNDERSOLD/OK | 99 | 1 | 401 | 0 | 0 | 528.64 | 639.12 | 341.03 | 14.33 | 408 | 4.16 | 0 | 3 | 99 |
| optimistic-r1 | OK/OK/OK | 100 | 0 | 67 | 0 | 130 | 242.13 | 263.11 | 496.15 | 7.96 | 197 | 1.88 | 0 | 1 | 100 |
| optimistic-r2 | OK/OK/OK | 100 | 0 | 20 | 0 | 113 | 247.89 | 262.75 | 496.39 | 5.35 | 129 | 1.24 | 0 | 1 | 100 |
| optimistic-r3 | OK/OK/OK | 100 | 0 | 16 | 0 | 159 | 242.78 | 262.15 | 497.16 | 7.61 | 174 | 1.56 | 0 | 2 | 100 |

`*`: 일부 실행의 값이 `n/a`(Prometheus 1s 표본 없음, 또는 1단계 4차까지라 기록 없음)라 숫자인 실행만으로 계산한 값. Hikari·Tomcat 최댓값과 서버 p95 는 1s 표본 값이다.

보조 표 (같은 실행): 끝단 값의 3회 범위(최소~최대)와 앞단 서비스 포화 지표의 중앙값

| 구성 | p95 범위(ms) | rps 범위 | order Hikari pending 최대 | order Tomcat busy 최대 | gateway Tomcat busy 최대 | order 커넥션 획득 평균 대기(ms) | order GC 정지 합(ms) | order GC 1s 최대(ms) | gateway GC 정지 합(ms) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| naive | 887.84~1358.51 | 229~295.07 | 115 | 200 | 200 | 123 | 57 | 42 | 81 |
| pessimistic | 589.9~757.32 | 333.75~377.36 | 177 | 200 | 179 | 154.74 | 44 | 31 | 66 |
| conditional | 709.56~864.83 | 313.64~346.97 | 161 | 179 | 200 | 164.3 | 49 | 33 | 74 |
| optimistic-r0 | 262.48~710.39 | 288.5~495.52 | 36 | 86 | 163 | 89.06 | 79 | 41 | 13 |
| optimistic-r1 | 236.85~254.35 | 495.04~497.35 | 0 | 35 | 62 | 0.3 | 15 | 11 | 4 |
| optimistic-r2 | 237.11~250.29 | 495.91~496.45 | 0 | 6 | 5 | 0.04 | 14 | 11 | 3 |
| optimistic-r3 | 233.42~249.34 | 495.38~497.44 | 0 | 5 | 69 | 0.09 | 13 | 7 | 4 |

중앙값에 쓴 실행 (RUN_ID 순, INCONCLUSIVE 제외 앞 3회):

- naive: 20261116-223039-naive, 20261116-223046-naive, 20261116-223052-naive
- pessimistic: 20261116-223109-pessimistic, 20261116-223114-pessimistic, 20261116-223120-pessimistic
- conditional: 20261116-223138-conditional, 20261116-223144-conditional, 20261116-223149-conditional
- optimistic-r0: 20261116-223206-optimistic-r0, 20261116-223213-optimistic-r0, 20261116-223219-optimistic-r0
- optimistic-r1: 20261116-223235-optimistic-r1, 20261116-223240-optimistic-r1, 20261116-223246-optimistic-r1
- optimistic-r2: 20261116-223302-optimistic-r2, 20261116-223307-optimistic-r2, 20261116-223312-optimistic-r2
- optimistic-r3: 20261116-223328-optimistic-r3, 20261116-223333-optimistic-r3, 20261116-223338-optimistic-r3

제외한 INCONCLUSIVE 측정 실행:

- 없음

판정:
- naive 3회 모두 `OVERSOLD`.
- 정확한 구성 18회 중 16회 `OK`. optimistic-r0 가 `UNDERSOLD` 2회(paid 96, 99). 나머지 5개 구성은 모두 `OK`.
- `INCONCLUSIVE` 0.
- 결제 실패율 0: 이번 28개 실행의 `payment 설정` 줄이 모두 `"failureRate":0.0` 이다.

기록이 말하는 것:
- `UNDERSOLD` 는 1단계 판정 규칙에서 "덜 팔림"이다. 초과 판매(`OVERSOLD`)가 아니라 낙관적 락 r0 의 특성이다.
- r0 은 재시도가 없다. 충돌에서 진 요청은 그대로 `STOCK_CONFLICT` 로 실패한다. 두 실행 모두 실패 사유는 `STOCK_CONFLICT`(404, 401건)뿐이고 품절 응답은 0이었다.
- 하지만 1단계 5차와 게이트웨이 메모리 측정에서는 r0 가 모두 `OK` 였다. 이번 결과는 그와 다르다.
- 충돌 수가 늘어난 원인을 이 기록만으로는 확정할 수 없다. 같은 시기에 order-service 가 느렸던 것(아래)과 겹친다.
- naive·비관적·조건부의 p95 도 1단계보다 훨씬 느렸다(아래).

## 실행 순서에 따른 지표: 데워지는 추세

1차 매트릭스에서 앞 구성일수록 order-service 가 느렸다. 측정 실행만, 실행한 순서대로다(워밍업 제외).

| RUN_ID | 판정 | p95(ms) | order Hikari pending 최대 | order 커넥션 대기 평균(ms) |
| --- | --- | --- | --- | --- |
| 20261116-223039-naive | OVERSOLD | 1358.51 | 131 | 191.94 |
| 20261116-223046-naive | OVERSOLD | 939.77 | 51 | 63.32 |
| 20261116-223052-naive | OVERSOLD | 887.84 | 115 | 123 |
| 20261116-223109-pessimistic | OK | 706.01 | 180 | 154.74 |
| 20261116-223114-pessimistic | OK | 589.9 | 64 | 119.61 |
| 20261116-223120-pessimistic | OK | 757.32 | 177 | 164.82 |
| 20261116-223138-conditional | OK | 864.83 | 177 | 180.2 |
| 20261116-223144-conditional | OK | 791.49 | 161 | 164.3 |
| 20261116-223149-conditional | OK | 709.56 | 50 | 146.96 |
| 20261116-223206-optimistic-r0 | UNDERSOLD | 710.39 | 84 | 146.01 |
| 20261116-223213-optimistic-r0 | UNDERSOLD | 528.64 | 36 | 89.06 |
| 20261116-223219-optimistic-r0 | OK | 262.48 | 1 | 1.67 |
| 20261116-223235-optimistic-r1 | OK | 242.13 | 0 | 0.32 |
| 20261116-223240-optimistic-r1 | OK | 254.35 | 0 | 0.3 |
| 20261116-223246-optimistic-r1 | OK | 236.85 | 0 | 0.05 |
| 20261116-223302-optimistic-r2 | OK | 237.11 | 0 | 0.04 |
| 20261116-223307-optimistic-r2 | OK | 247.89 | 0 | 0.01 |
| 20261116-223312-optimistic-r2 | OK | 250.29 | 0 | 0.15 |
| 20261116-223328-optimistic-r3 | OK | 233.42 | 0 | 0.03 |
| 20261116-223333-optimistic-r3 | OK | 249.34 | 0 | 0.1 |
| 20261116-223338-optimistic-r3 | OK | 242.78 | 0 | 0.09 |

- order 커넥션 대기 평균은 naive 192ms 에서 시작해 pessimistic·conditional 에서 120~180ms, r0 의 세 번째 실행에서 1.7ms, r1 부터 0.3ms 이하로 내려갔다.
- order Hikari pending 최대는 131 → 180 → 177 에서 0 으로 내려갔다.
- r0 의 p95 는 710 → 529 → 262ms 로 실행마다 빨라졌다.
- r1~r3 는 게이트웨이 메모리 측정과 같은 수준이다(아래 비교 표).

## 진단 재측정 (같은 스택, 데워진 뒤)

가설: 느린 앞 구성은 코드 때문이 아니라 새로 띄운 JVM 이 덜 데워졌기 때문이다.
1차 직후 스택을 그대로 둔 채 같은 절차로 앞 4개 구성만 다시 돌렸다(`scripts/run-matrix.sh naive pessimistic conditional optimistic-r0`, 워밍업 1 + 측정 3). 22:43~22:45.
1차 매트릭스 기록은 지우거나 바꾸지 않았다.
`FROM=20261116-2243 TO=20261116-2300 scripts/summarize.sh` 출력에서 4개 구성만 옮겼다(나머지 3개는 이번에 돌리지 않아 `n/a`).

| 구성 | 판정(3회) | paid | undersold | conflicts | timeouts | retries | p95(ms) | p99(ms) | rps | inventory 차감 서버 p95(ms) | MySQL 락 대기 횟수 | 평균 락 대기(ms) | inventory Hikari pending 최대 | inventory Tomcat busy 최대 | 결제 호출 수 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| naive | OVERSOLD/OVERSOLD/OVERSOLD | 215 | 0 | 0 | 0 | 0 | 253.61 | 265.21 | 495.53 | 4.52 | 106 | 1.58 | 0 | 2 | 215 |
| pessimistic | OK/OK/OK | 100 | 0 | 0 | 0 | 0 | 245.85 | 273.76 | 496.15 | 23.14 | 126 | 6.19 | 0 | 1 | 100 |
| conditional | OK/OK/OK | 100 | 0 | 0 | 0 | 0 | 242.49 | 260.31 | 496.42 | 3.96 | 60 | 3.04 | 0 | 1 | 100 |
| optimistic-r0 | OK/OK/OK | 100 | 0 | 93 | 0 | 0 | 238.46 | 258.97 | 496.56 | 4.05 | 88 | 1.36 | 0 | 1 | 100 |

| 구성 | p95 범위(ms) | rps 범위 | order Hikari pending 최대 | order Tomcat busy 최대 | gateway Tomcat busy 최대 | order 커넥션 획득 평균 대기(ms) | order GC 정지 합(ms) | order GC 1s 최대(ms) | gateway GC 정지 합(ms) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| naive | 249.67~290.84 | 495.37~497.87 | 0 | 5 | 12 | 0.03 | 13 | 13 | 4 |
| pessimistic | 241.41~258.51 | 495.65~497.42 | 0 | 5 | 28 | 0.04 | 11 | 9 | 3 |
| conditional | 240.74~247.81 | 495.1~498.16 | 0 | 4 | 83 | 0.05 | 11 | 7 | 3 |
| optimistic-r0 | 237.19~239.59 | 496.16~497.7 | 0 | 52 | 22 | 0.03 | 11 | 6 | 4 |

실행별 기록:

| RUN_ID | 판정 | p95(ms) | order Hikari pending 최대 | order 커넥션 대기 평균(ms) |
| --- | --- | --- | --- | --- |
| 20261116-224317-naive | OVERSOLD | 290.84 | 0 | 4.8 |
| 20261116-224322-naive | OVERSOLD | 253.61 | 0 | 0.03 |
| 20261116-224327-naive | OVERSOLD | 249.67 | 0 | 0.02 |
| 20261116-224343-pessimistic | OK | 258.51 | 0 | 0.04 |
| 20261116-224348-pessimistic | OK | 245.85 | 0 | 0.17 |
| 20261116-224353-pessimistic | OK | 241.41 | 0 | 0.02 |
| 20261116-224409-conditional | OK | 240.74 | 0 | 0.05 |
| 20261116-224414-conditional | OK | 242.49 | 0 | 0.06 |
| 20261116-224419-conditional | OK | 247.81 | 0 | 0.05 |
| 20261116-224435-optimistic-r0 | OK | 237.19 | 0 | 0.04 |
| 20261116-224441-optimistic-r0 | OK | 238.46 | 0 | 0.03 |
| 20261116-224446-optimistic-r0 | OK | 239.59 | 0 | 0.01 |

판정:
- naive 3회 `OVERSOLD`, pessimistic·conditional·optimistic-r0 는 모두 3회 `OK`. `INCONCLUSIVE` 0. 결제 실패율 0(16개 실행 모두 `"failureRate":0.0`).
- p95 중앙값은 naive 254, pessimistic 246, conditional 242, r0 238ms 다. 모두 게이트웨이 메모리 측정과 같은 수준이거나 더 빠르다.
- order Hikari pending 최대는 12개 실행 모두 0, order 커넥션 대기 평균은 첫 실행(naive 4.8ms)을 빼면 0.2ms 이하다. 1차에서 131~180 이던 값이 사라졌다.
- r0 의 UNDERSOLD 는 다시 나오지 않았다(3회 모두 `OK`, paid 100).

가설에 대해 기록이 말하는 것:
- 같은 코드, 같은 설정에서 스택만 데워진 뒤에는 느림과 pending 이 사라졌다. 앞 구성의 느림은 코드 변경보다 덜 데워진 상태와 맞는다.
- 다만 이것만으로 원인을 확정하지는 못한다. 그 사이 `lol-rabbitmq` CPU 가 내려갔다(68% → 2%). 두 요인을 이번 기록으로 떼어 볼 수 없다.
- r0 의 UNDERSOLD 도 같이 사라졌지만, 3회 중 2회가 한 번 덜 팔린 것이라 우연일 가능성도 따로 닫지 못했다.
- 진단 재측정에는 optimistic-r1~r3 가 없다. 이번 기록에서 r1~r3 는 1차 매트릭스 값만 있다.

## 게이트웨이 메모리 측정과 p95 중앙값 비교

기준은 `FROM=20261103-2235 TO=20261103-2300 scripts/summarize.sh`(`stage2-gateway-memory.md`)다. p95 중앙값(ms)이다.

| 구성 | 게이트웨이 메모리 측정 (11-03) | 1차 매트릭스 | 진단 재측정 |
|---|---|---|---|
| naive | 758 | 940 | 254 |
| pessimistic | 333 | 706 | 246 |
| conditional | 268 | 791 | 242 |
| optimistic-r0 | 247 | 529 | 238 |
| optimistic-r1 | 241 | 242 | - |
| optimistic-r2 | 236 | 248 | - |
| optimistic-r3 | 238 | 243 | - |

- 1차 매트릭스: 앞 4개 구성이 2~3배 느렸다. r1~r3 는 ±10ms 안에서 같다.
- 진단 재측정: 4개 구성 모두 게이트웨이 메모리 측정보다 빠르거나 같다. naive 도 758 에서 254 로 빨랐다(paid 중앙값 461 → 215). 이유는 이 기록으로 말할 수 없다. 판정은 `OVERSOLD` 로 같다.

## 2단계 핫패스 차이와 해석

- 이벤트 주문은 `order_items` 를 INSERT 한 줄 더 쓴다. `markPaid`/`markFailed` 는 left join SELECT 를 한 번 한다. 쓰기는 status 등 세 칼럼 UPDATE 로 그대로다.
- r1~r3 는 같은 핫패스를 타는데 게이트웨이 메모리 측정과 ±10ms 안에서 같다. 진단 재측정의 4개 구성도 느려지지 않았다. 이 차이의 비용이 작다는 근거다.
- 다만 확정은 아니다. 이번 측정은 7개 구성 한 번씩이고, 같은 조건에서 코드만 바꿔 비교한 것이 아니다. 다른 프로젝트 컨테이너도 떠 있었다.

## 남은 것

- 새로 띄운 스택은 매트릭스 전에 충분히 데워야 한다. 1단계부터 있던 워밍업 부족 문제와 같다. 구성마다 워밍업을 늘리거나, 시작 전에 부하를 몇 번 걸어 둔다.
- 다른 프로젝트 부하가 없는 조건에서 다시 재면 더 깨끗하다.
- 진단 재측정에 빠진 r1~r3 와, r0 UNDERSOLD 가 우연인지는 닫지 못했다.

## 재현 명령

게이트웨이 호스트 포트가 8080 이 아니면 `GATEWAY_URL` 을 맞춘다.

```
scripts/run-matrix.sh
FROM=20261116-2230 TO=20261116-2243 scripts/summarize.sh   # 1차 매트릭스
scripts/run-matrix.sh naive pessimistic conditional optimistic-r0
FROM=20261116-2243 TO=20261116-2300 scripts/summarize.sh   # 진단 재측정
```
