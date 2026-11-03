# 1단계 5차 측정 환경 (부하 조건 변경 뒤)

- 기록 시각: 2026-11-03 22:10 KST (측정 22:10~22:13)
- git 커밋: 6704a73 (`feat/flash-sale-stage1`)
- 부하 조건: USERS 500, `ARRIVAL_SECONDS` 1(사용자 i 가 i × 2ms 뒤 출발), 재고 100. k6 는 Docker 네트워크 안 컨테이너 grafana/k6:2.2.0, cpus 4 / memory 2g, `gateway:8080` 직접
- 앱 설정: 1단계 고정 설정. 모든 앱이 cpus 2 / 640 MiB, `-XX:MaxRAMPercentage=70`, JVM 이 고른 Serial GC
  - order-service 와 gateway 에서 `java -Xlog:gc*=info -version` 으로 `Using Serial`, `CPUs: 14 total, 2 available`, `Memory: 640M` 를 확인했다. 실행마다 `meta.json` 의 `jvm` 에 같은 값이 남는다
- Docker Desktop: 1단계 4차 때 재시작한 상태 그대로, 엔진 29.6.1, VM 메모리 7935 MiB, CPU 14개
- 다른 컨테이너: 없음
- 스택 준비:
  - Prometheus·Grafana 는 그대로 두고, 나머지 컨테이너는 `docker compose up -d --force-recreate` 로 새로 만들었다
  - `scripts/smoke.sh` 1회 뒤 바로 `scripts/run-matrix.sh` 를 시작했다. 사전 확인 실행은 없었다

## e-commerce 스택 (docker stats, 측정 직전 유휴)

| 컨테이너 | CPU | 메모리 |
|---|---|---|
| e-commerce-gateway-1 | 1.79% | 251.7MiB / 640MiB |
| e-commerce-grafana-1 | 0.15% | 99.19MiB / 256MiB |
| e-commerce-inventory-service-1 | 1.66% | 297.2MiB / 640MiB |
| e-commerce-mysql-1 | 2.95% | 497.4MiB / 1.5GiB |
| e-commerce-mysqld-exporter-1 | 2.40% | 15.45MiB / 64MiB |
| e-commerce-order-service-1 | 4.99% | 340.7MiB / 640MiB |
| e-commerce-payment-service-1 | 2.94% | 307.4MiB / 640MiB |
| e-commerce-product-service-1 | 2.18% | 304.9MiB / 640MiB |
| e-commerce-prometheus-1 | 0.95% | 182.6MiB / 512MiB |
| e-commerce-user-service-1 | 2.03% | 297MiB / 640MiB |

## 측정 뒤 gateway

- 컨테이너 시작(22:09)부터 누적한 값이다.
- `/proc/net/netstat`: `ListenOverflows` 0, `TCPReqQFullDoCookies` 0, `TCPSynRetrans` 0. 연결 대기열이 넘친 적이 없다
- cgroup: swap 사용 286 MiB, `memory.events` 의 max 1,642. 이 조건에서도 gateway 는 640 MiB 한도에 닿아 swap 을 쓴다
