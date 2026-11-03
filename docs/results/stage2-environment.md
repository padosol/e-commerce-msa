# 2단계(order-service GC) 측정 환경

- 기록 시각: 2026-11-03 17:18 KST (측정 17:18~17:33)
- git 커밋: 8e46ac0 (측정 하네스 931d63b 포함)
- Docker Desktop: 1단계 4차 때 재시작한 상태 그대로, 엔진 29.6.1, VM 메모리 7935 MiB, CPU 14개
- 다른 컨테이너: 없음 (아래 e-commerce 스택과 실행마다 뜨는 k6 컨테이너뿐)
- k6: 1단계 4차와 같다. Docker 네트워크(e-commerce_default) 안 컨테이너 grafana/k6:2.2.0, cpus 4 / memory 2g, gateway:8080 에 직접 연결
- 스택 준비: Prometheus·Grafana 는 그대로 두고(1단계 4차 데이터를 같은 Prometheus 에 남기려고) 나머지 컨테이너를 `docker compose up -d --force-recreate` 로 새로 만들었다. 그 뒤 `scripts/smoke.sh` 1회와 하네스 확인용 소규모 실행(USERS 200, 기록은 지움) 1회를 돌리고 바로 매트릭스를 시작했다
  - 1단계 4차는 매트릭스 전에 USERS 5000 사전 확인 실행을 1회 돌렸다. 그래서 이번 매트릭스 시작 때 앱 JVM 이 4차보다 덜 데워진 상태였다
- 측정 중 표본: `docker stats` 로 order-service·gateway 를 약 2초마다 360회 표본(17:18:36~17:30:39, 마지막 구성 optimistic-r3 은 표본 밖)

## 앱 JVM 설정 (실행 중 컨테이너에서 확인)

| 서비스 | CPU | 메모리 한도 | JAVA_TOOL_OPTIONS | GC | 최대 heap |
|---|---|---|---|---|---|
| order-service | 2 | 1024 MiB | `-XX:MaxRAMPercentage=70 -XX:+UseG1GC` | G1 | 718 MiB |
| gateway, user, product, inventory, payment | 2 | 640 MiB | `-XX:MaxRAMPercentage=70` | Serial (JVM 자동 선택) | 448 MiB |

order-service 는 `java -Xlog:gc*=info -version` 으로 `Using G1`, `Memory: 1024M`, `CPUs: 14 total, 2 available` 를 확인했다. 실행마다 같은 값이 `meta.json` 의 `jvm` 에 남는다.

## e-commerce 스택 (docker stats, 측정 직전 유휴)

| 컨테이너 | CPU | 메모리 |
|---|---|---|
| e-commerce-gateway-1 | 2.85% | 471.3MiB / 640MiB |
| e-commerce-grafana-1 | 0.20% | 160.1MiB / 256MiB |
| e-commerce-inventory-service-1 | 0.88% | 322.2MiB / 640MiB |
| e-commerce-mysql-1 | 2.60% | 521.6MiB / 1.5GiB |
| e-commerce-mysqld-exporter-1 | 2.34% | 11.16MiB / 64MiB |
| e-commerce-order-service-1 | 3.09% | 582.8MiB / 1GiB |
| e-commerce-payment-service-1 | 2.40% | 416.7MiB / 640MiB |
| e-commerce-product-service-1 | 1.60% | 337.4MiB / 640MiB |
| e-commerce-prometheus-1 | 1.23% | 164.3MiB / 512MiB |
| e-commerce-user-service-1 | 1.02% | 303.5MiB / 640MiB |

## 측정 중 자원

- order-service: 컨테이너 메모리 최대 719.2 MiB / 1 GiB, CPU 최대 219%. heap 사용량 최대 187 MiB(Prometheus 5초 표본). OOM 종료·재시작 없음
- gateway: 컨테이너 메모리가 17:19:12 에 640.0 MiB 로 한도에 닿았고 이후 639 MiB 근처에 머물렀다. CPU 최대 133%. OOM 종료·재시작 없음
- gateway 컨테이너 안 `/proc/net/netstat` (17:31, 컨테이너 시작 17:17 부터 누적): `ListenOverflows` 191634, `ListenDrops` 191634, `TCPReqQFullDoCookies` 59805, `TCPSynRetrans` 810. 1단계 4차에서는 이 값을 기록하지 않았다
