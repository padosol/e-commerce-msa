# 2단계: 게이트웨이 메모리 (swap 제거)

1단계 5차(500명이 1초 동안 요청, 재고 100)에서 게이트웨이는 640 MiB 한도에 닿아 swap 을 286 MiB 썼다.
매트릭스 동안 메모리 한도에 1,642번 닿았다(`stage1-attempt5-environment.md`).
2단계 진단(5000명 동시 조건, `feat/flash-sale-stage2-gc` 브랜치의 `docs/results/stage2-gateway-diagnosis/`)에서는 이 swap 때문에 게이트웨이 GC 가 0.4~1.8초씩 멈췄다.
그래서 게이트웨이만 swap 을 쓰지 않을 만큼 메모리를 준다. heap 크기와 GC 는 그대로 둔다.

## 바꾼 설정 (`compose.yaml` 의 gateway)

| 항목 | 1단계 | 2단계 |
|---|---|---|
| 메모리 한도 | 640m | 1280m |
| swap | 메모리 한도만큼 허용(Docker 기본) | 쓰지 않음(`memswap_limit` = 메모리 한도) |
| heap | `-XX:MaxRAMPercentage=70` → 448 MiB | `-Xmx448m` → 448 MiB (같음) |
| GC | JVM 이 고른 Serial | `-XX:+UseSerialGC` (같음) |
| CPU | 2 | 2 |

- heap 을 `-Xmx` 로 고정한 이유: `MaxRAMPercentage=70` 그대로 두면 heap 이 1280m 의 70%(896 MiB)로 커진다. 그러면 heap 밖 메모리를 더해 다시 한도를 넘는다.
- GC 를 명시한 이유: 지금 동작(Serial)을 문서로 남기려는 것이다. 컨테이너 메모리가 약 1.8GB 를 넘으면 JVM 이 G1 을 고르는데, 1280m 은 그보다 작아 명시하지 않아도 Serial 이다.

## 메모리를 정한 근거 (2026-11-03)

**1. 필요한 양을 쟀다.**
- 게이트웨이를 한도 2g·swap 금지로 잠시 띄웠다. heap 448 MiB 와 Serial GC 는 위와 같다.
- 지금 부하 조건(500명/1초, 재고 100)으로 45회 돌렸다. 결과 기록에는 넣지 않았다.
- cgroup `memory.peak`(컨테이너가 쓴 메모리의 최댓값)는 이렇게 늘었다.

| 실행 | memory.peak | heap committed | 비고 |
|---|---|---|---|
| smoke 뒤 | 268 MiB | | |
| 1회 | 481 MiB | | |
| 15회 | 722 MiB | 293 MiB | 실행마다 약 15 MiB 씩 늘어남 |
| 18회 | 855 MiB | 429 MiB | Full GC 1회 |
| 21~45회 | 866 MiB | 429 MiB | 더 늘지 않음 |

- 멈춘 뒤 RSS 는 873 MiB 였다. JVM 이 보고하는 heap 429 MiB + non-heap 90 MiB 말고도, 약 350 MiB 가 더 든다(스레드 약 260개의 스택, JIT, malloc 등).
- heap 이 최대(448 MiB)까지 커져도 약 885 MiB 다.
- 640m 일 때 메모리 안 623 MiB + swap 286 MiB(약 909 MiB)와도 맞는다.

**2. 1280m 로 정했다.** 최댓값 약 885 MiB 에 약 30% 여유를 둔 값이다. swap 을 막았으므로, 만에 하나 넘으면 조용히 느려지지 않고 OOM 으로 드러난다.

**3. 바꾼 설정으로 확인했다.** 같은 조건으로 25회 돌렸다(결과 기록에는 넣지 않음).
- `memory.peak` 864 MiB 에서 멈췄다(20회째 862, 25회째 864).
- swap 0, `memory.events` 의 max(한도 도달) 0, OOM 종료 0, `ListenOverflows` 0.
- k6 p95 는 25회 중 최소 236 / 중앙 250 / 최대 316ms.

## 매트릭스 결과: 1단계 5차와 비교 (2026-11-03 22:35~22:38)

절차는 1단계 5차와 같다. 다른 것은 게이트웨이 메모리 설정 하나다.
- 커밋 79c1aee. Prometheus·Grafana 를 뺀 컨테이너를 새로 만들고 smoke 1회 뒤 바로 `scripts/run-matrix.sh` 를 돌렸다
- 7개 구성 × (워밍업 1 + 측정 3)
- order-service 등 나머지 앱은 640 MiB·Serial GC 그대로다. gateway 는 `Using Serial`, `Memory: 1280M`, swap.max 0 을 확인했다

측정 직전 유휴 상태(docker stats):

| 컨테이너 | CPU | 메모리 |
|---|---|---|
| e-commerce-gateway-1 | 1.55% | 249.2MiB / 1.25GiB |
| e-commerce-grafana-1 | 0.17% | 99.46MiB / 256MiB |
| e-commerce-inventory-service-1 | 2.61% | 296.8MiB / 640MiB |
| e-commerce-mysql-1 | 3.43% | 482.6MiB / 1.5GiB |
| e-commerce-mysqld-exporter-1 | 1.88% | 12.63MiB / 64MiB |
| e-commerce-order-service-1 | 4.20% | 340.3MiB / 640MiB |
| e-commerce-payment-service-1 | 3.34% | 300.5MiB / 640MiB |
| e-commerce-product-service-1 | 2.31% | 304.8MiB / 640MiB |
| e-commerce-prometheus-1 | 0.79% | 195.4MiB / 512MiB |
| e-commerce-user-service-1 | 4.22% | 303.1MiB / 640MiB |

아래는 `FROM=20261103-2235 scripts/summarize.sh` 출력 그대로다.

| 구성 | 판정(3회) | paid | undersold | conflicts | timeouts | retries | p95(ms) | p99(ms) | rps | inventory 차감 서버 p95(ms) | MySQL 락 대기 횟수 | 평균 락 대기(ms) | inventory Hikari pending 최대 | inventory Tomcat busy 최대 | 결제 호출 수 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| naive | OVERSOLD/OVERSOLD/OVERSOLD | 461 | 0 | 0 | 0 | 0 | 757.55 | 820.1 | 319.36 | 17.95 | 388 | 3.05 | 0 | 3 | 461 |
| pessimistic | OK/OK/OK | 100 | 0 | 0 | 0 | 0 | 333.08 | 410.51 | 467.56 | 22.26 | 265 | 3.71 | 3 | 12 | 100 |
| conditional | OK/OK/OK | 100 | 0 | 0 | 0 | 0 | 267.8 | 311.09 | 494.37 | 18.13 | 103 | 5.62 | 0 | 1 | 100 |
| optimistic-r0 | OK/OK/OK | 100 | 0 | 105 | 0 | 0 | 246.51 | 261.42 | 496.06 | 3.84 | 97 | 1.31 | 0 | 1 | 100 |
| optimistic-r1 | OK/OK/OK | 100 | 0 | 41 | 0 | 90 | 241.47 | 260.28 | 497.49 | 5.18 | 127 | 1.26 | 0 | 2 | 100 |
| optimistic-r2 | OK/OK/OK | 100 | 0 | 9 | 0 | 74 | 235.58 | 254.83 | 496.98 | 3.65 | 78 | 0.87 | 0 | 1 | 100 |
| optimistic-r3 | OK/OK/OK | 100 | 0 | 7 | 0 | 57 | 238.14 | 257.82 | 495.84 | 2.62 | 64 | 0.92 | 0 | 1 | 100 |

`*`: 일부 실행의 값이 `n/a`(Prometheus 1s 표본 없음, 또는 1단계 4차 이전이라 기록 없음)라 숫자인 실행만으로 계산한 값. Hikari·Tomcat 최댓값과 서버 p95 는 1s 표본 값이다.

보조 표 (같은 실행): 끝단 값의 3회 범위(최소~최대)와 앞단 서비스 포화 지표의 중앙값

| 구성 | p95 범위(ms) | rps 범위 | order Hikari pending 최대 | order Tomcat busy 최대 | gateway Tomcat busy 최대 | order 커넥션 획득 평균 대기(ms) | order GC 정지 합(ms) | order GC 1s 최대(ms) | gateway GC 정지 합(ms) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| naive | 625.25~1122.42 | 242.04~363.24 | 15 | 194 | 200 | 29.71 | 56 | 33 | 62 |
| pessimistic | 297.73~392.59 | 445.86~472.2 | 0 | 59 | 83 | 42.13 | 34 | 30 | 55 |
| conditional | 257.88~306.5 | 486.44~496.57 | 17 | 66 | 5 | 3.52 | 24 | 13 | 48 |
| optimistic-r0 | 237.73~250.08 | 494.96~496.09 | 0 | 45 | 23 | 0.12 | 26 | 13 | 4 |
| optimistic-r1 | 239.94~244.52 | 495.43~497.57 | 0 | 12 | 50 | 0.03 | 21 | 10 | 4 |
| optimistic-r2 | 234.48~241.72 | 492.95~498.53 | 0 | 12 | 71 | 0.04 | 16 | 10 | 2 |
| optimistic-r3 | 232.44~241.87 | 494.59~498.15 | 0 | 3 | 9 | 0.04 | 17 | 15 | 3 |

중앙값에 쓴 실행 (RUN_ID 순, INCONCLUSIVE 제외 앞 3회):

- naive: 20261103-223553-naive, 20261103-223559-naive, 20261103-223605-naive
- pessimistic: 20261103-223621-pessimistic, 20261103-223626-pessimistic, 20261103-223631-pessimistic
- conditional: 20261103-223647-conditional, 20261103-223652-conditional, 20261103-223657-conditional
- optimistic-r0: 20261103-223713-optimistic-r0, 20261103-223718-optimistic-r0, 20261103-223723-optimistic-r0
- optimistic-r1: 20261103-223739-optimistic-r1, 20261103-223744-optimistic-r1, 20261103-223749-optimistic-r1
- optimistic-r2: 20261103-223805-optimistic-r2, 20261103-223810-optimistic-r2, 20261103-223815-optimistic-r2
- optimistic-r3: 20261103-223831-optimistic-r3, 20261103-223836-optimistic-r3, 20261103-223841-optimistic-r3

제외한 INCONCLUSIVE 측정 실행:

- 없음

**한 줄 결론:** 게이트웨이 swap 은 사라졌다. 하지만 이 부하(초당 500건)에서는 끝단 지연이 눈에 띄게 달라지지 않았다.
1단계 5차에서 한 번 튀었던 실행(p95 646ms)은 이번에 다시 나오지 않았다. 다만 한 번 있었던 일이라 swap 때문이었다고 말할 수는 없다.

### 게이트웨이

| 항목 | 1단계 5차 (640m) | 2단계 (1280m, swap 없음) |
|---|---|---|
| swap 사용 (매트릭스 뒤) | 286 MiB | 0 |
| 메모리 한도 도달 (`memory.events` max) | 1,642회 | 0 |
| 메모리 최댓값 (`memory.peak`) | 640 MiB (한도) | 865 MiB |
| OOM 종료 | 0 | 0 |
| 연결 대기열 넘침 (`ListenOverflows`) | 0 | 0 |
| GC 정지 1초 표본 최대 (정확한 구성 18회) | 2~40ms | 0~43ms |

이 부하에서는 swap 이 있을 때도 게이트웨이 GC 정지가 짧았다(최대 40ms). 2단계 진단(5000명 동시)에서 본 0.4~1.8초 정지는 여기서는 나타나지 않았다.

### 끝단 지연

| 구성 | p95 중앙값 (5차 → 2단계) | p95 범위 (5차 → 2단계) | inventory 차감 서버 p95 | order 커넥션 대기 평균 |
|---|---|---|---|---|
| naive | 751 → 758ms | 531~1,048 → 625~1,122ms | 16.5 → 18.0ms | 34.4 → 29.7ms |
| 비관적 락 | 285 → 333ms | 275~352 → 298~393ms | 13.1 → 22.3ms | 5.9 → 42.1ms |
| 조건부 UPDATE | 264 → 268ms | 253~307 → 258~307ms | 14.0 → 18.1ms | 2.9 → 3.5ms |
| 낙관적 락 r0 | 246 → 247ms | 239~646 → 238~250ms | 4.3 → 3.8ms | 0.06 → 0.12ms |
| 낙관적 락 r1 | 238 → 241ms | 227~246 → 240~245ms | 4.1 → 5.2ms | 0.03 → 0.03ms |
| 낙관적 락 r2 | 240 → 236ms | 237~242 → 234~242ms | 5.3 → 3.7ms | 0.01 → 0.04ms |
| 낙관적 락 r3 | 239 → 238ms | 233~240 → 232~242ms | 6.0 → 2.6ms | 0.1 → 0.04ms |

정확한 구성 18회 전체로 보면 이렇다.
- 응답 최대: 262~713ms → 262~559ms
- p99 최대: 681ms → 447ms
- p95 가 400ms 를 넘은 실행: 1회 → 0회

- **조건부 UPDATE·낙관적 락:** p95 중앙값이 ±5ms 안에서 같다.
- **비관적 락:** 285ms → 333ms 로 느려졌다. 그런데 차이가 생긴 곳은 게이트웨이가 아니라 inventory 쪽이다(차감 서버 p95 13 → 22ms, 락 평균 대기 2.6 → 3.7ms).
  - 두 측정 모두 측정 3회에 걸쳐 계속 빨라졌다. 5차는 352 → 285 → 275ms, 2단계는 393 → 333 → 298ms 다.
  - 구성을 바꿀 때마다 새로 띄운 inventory-service 가 아직 데워지는 중이었다는 뜻이다. 워밍업 1회(요청 500건)로는 부족하다(`stage1-attempt5-summary.md` §3).
  - 게이트웨이 변경과 이어지는 근거는 찾지 못했다.
- **정확성:** 같다. 정확한 6개 구성 18회 모두 `OK`, naive 3회 모두 `OVERSOLD`(paid 중앙값 461, 5차 426), `INCONCLUSIVE` 0.

## 남은 것

- 비관적 락처럼 데워지는 추세가 남아 있는 구성이 있다. 구성마다 워밍업을 늘리면 비교가 더 안정된다.
- 5000명 동시 조건에서는 heap 밖 메모리가 이보다 클 수 있다. 1280m 은 지금 기본 조건으로 정한 값이다.
