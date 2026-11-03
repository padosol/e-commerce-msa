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

## 남은 것

- 이 설정으로 7개 구성 매트릭스는 아직 재지 않았다. 1단계 5차와 비교하려면 같은 조건(`scripts/run-matrix.sh`, 기본 500명/1초)으로 다시 잰다.
- 5000명 동시 조건에서는 heap 밖 메모리가 이보다 클 수 있다. 1280m 은 지금 기본 조건으로 정한 값이다.
