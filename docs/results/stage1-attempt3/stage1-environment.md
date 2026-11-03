# 1단계 3차 측정 환경

- 기록 시각: 2026-11-03 14:36 KST
- git 커밋: 5596cb0
- Docker Desktop: 측정 직전 재시작(백엔드가 약 1.5개월 재시작 없이 돌던 상태였고 이미지 pull 이 멈췄었다), 엔진 29.6.1, VM 메모리 7935 MiB, CPU 14개
- 다른 컨테이너: 없음 (같은 VM 에서 실행 중인 컨테이너는 아래 e-commerce 스택뿐)
- 호스트: 다른 프로젝트의 JVM 프로세스가 유휴 상태(CPU 0%)로 실행 중 — 컨테이너가 아니어서 Docker VM 자원을 쓰지 않는다
- mysqld-exporter: 공식 이미지 prom/mysqld-exporter:v0.16.0 (2차까지는 같은 릴리스 바이너리로 로컬 빌드한 이미지)
- Prometheus/Grafana: 기본 포트 9090/3000

## e-commerce 스택 (docker stats, 측정 직전 유휴)

| 컨테이너 | CPU | 메모리 |
|---|---|---|
| e-commerce-gateway-1 | 1.73% | 295.8MiB / 640MiB |
| e-commerce-grafana-1 | 0.62% | 161.8MiB / 256MiB |
| e-commerce-inventory-service-1 | 1.56% | 302.4MiB / 640MiB |
| e-commerce-mysql-1 | 0.96% | 546.9MiB / 1.5GiB |
| e-commerce-mysqld-exporter-1 | 2.49% | 14.75MiB / 64MiB |
| e-commerce-order-service-1 | 2.46% | 352.9MiB / 640MiB |
| e-commerce-payment-service-1 | 2.61% | 308.3MiB / 640MiB |
| e-commerce-product-service-1 | 1.36% | 306.6MiB / 640MiB |
| e-commerce-prometheus-1 | 1.21% | 99.53MiB / 512MiB |
| e-commerce-user-service-1 | 2.33% | 302.1MiB / 640MiB |
