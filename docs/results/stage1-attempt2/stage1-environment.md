# 1단계 측정 환경 (2차 측정)

- 일시: 2026-11-02 22:14 KST
- git 커밋: f3a8029
- Docker VM 총 메모리: 8320954368 bytes (7.7 GiB)

## e-commerce 스택 (측정 시작 전, docker stats --no-stream)

| 컨테이너 | CPU | 메모리 |
|---|---|---|
| e-commerce-inventory-service-1 | 0.82% | 536.3MiB / 640MiB |
| e-commerce-product-service-1 | 0.34% | 385.6MiB / 640MiB |
| e-commerce-payment-service-1 | 0.59% | 418.3MiB / 640MiB |
| e-commerce-order-service-1 | 0.79% | 627.2MiB / 640MiB |
| e-commerce-user-service-1 | 0.36% | 414.2MiB / 640MiB |
| e-commerce-grafana-1 | 0.55% | 156.4MiB / 256MiB |
| e-commerce-mysqld-exporter-1 | 2.42% | 12.64MiB / 64MiB |
| e-commerce-mysql-1 | 0.97% | 733.3MiB / 1.5GiB |
| e-commerce-prometheus-1 | 1.28% | 79.88MiB / 512MiB |
| e-commerce-gateway-1 | 1.19% | 608.2MiB / 640MiB |

## 같은 Docker VM 의 다른 컨테이너

- 다른 프로젝트 컨테이너 7개가 유휴 상태로 실행 중이며 메모리 합계는 약 1175 MiB 이다.
