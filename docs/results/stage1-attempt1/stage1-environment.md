# 1단계 측정 환경

- 일시: 2026-11-02 21:40 KST
- git 커밋: 2b23ce0
- Docker VM 총 메모리: 8320954368 bytes (7.7 GiB)

## e-commerce 스택 (측정 시작 전 유휴 상태, docker stats --no-stream)

| 컨테이너 | CPU | 메모리 |
|---|---|---|
| e-commerce-product-service-1 | 0.62% | 299.3MiB / 640MiB |
| e-commerce-inventory-service-1 | 0.69% | 265.4MiB / 640MiB |
| e-commerce-payment-service-1 | 1.14% | 277.8MiB / 640MiB |
| e-commerce-order-service-1 | 1.71% | 316.8MiB / 640MiB |
| e-commerce-user-service-1 | 0.91% | 299.2MiB / 640MiB |
| e-commerce-grafana-1 | 0.20% | 112.5MiB / 256MiB |
| e-commerce-mysqld-exporter-1 | 2.09% | 10.69MiB / 64MiB |
| e-commerce-mysql-1 | 0.88% | 489.4MiB / 1.5GiB |
| e-commerce-prometheus-1 | 1.03% | 39.38MiB / 512MiB |
| e-commerce-gateway-1 | 0.64% | 247.6MiB / 640MiB |

## 같은 Docker VM 의 다른 컨테이너

- 다른 프로젝트 컨테이너 7개가 유휴 상태로 실행 중이며 메모리 합계는 약 1705 MiB 이다.
