# 1단계 4차 측정 환경

- 기록 시각: 2026-11-03 15:48 KST
- git 커밋: e7cc995
- Docker Desktop: 2026-11-03 14:3x 에 재시작한 상태 그대로, 엔진 29.6.1, VM 메모리 7935 MiB, CPU 14개
- 다른 컨테이너: 없음 (같은 VM 에서 실행 중인 컨테이너는 아래 e-commerce 스택과, 실행마다 뜨는 k6 컨테이너뿐)
- 호스트: 다른 프로젝트의 JVM 프로세스가 유휴 상태로 실행 중 — 컨테이너가 아니어서 Docker VM 자원을 쓰지 않는다
- k6: Docker 네트워크(e-commerce_default) 안 컨테이너 grafana/k6:2.2.0, cpus 4 / memory 2g, gateway:8080 에 직접 연결. 사전 확인 실행(naive 1회, 한도 없이)에서 k6 메모리 최대 약 1.0 GiB, 스택 합계 최대 약 4.4 GiB, 연결 단계 오류 0건
- mysqld-exporter: 공식 이미지 prom/mysqld-exporter:v0.16.0
- Prometheus/Grafana: 기본 포트 9090/3000

## e-commerce 스택 (docker stats, 측정 직전 유휴)

| 컨테이너 | CPU | 메모리 |
|---|---|---|
| e-commerce-gateway-1 | 0.60% | 602.7MiB / 640MiB |
| e-commerce-grafana-1 | 0.34% | 158.8MiB / 256MiB |
| e-commerce-inventory-service-1 | 0.97% | 473MiB / 640MiB |
| e-commerce-mysql-1 | 2.87% | 883.9MiB / 1.5GiB |
| e-commerce-mysqld-exporter-1 | 2.47% | 14.44MiB / 64MiB |
| e-commerce-order-service-1 | 0.61% | 622.6MiB / 640MiB |
| e-commerce-payment-service-1 | 0.50% | 592.2MiB / 640MiB |
| e-commerce-product-service-1 | 0.52% | 468.6MiB / 640MiB |
| e-commerce-prometheus-1 | 1.63% | 137.5MiB / 512MiB |
| e-commerce-user-service-1 | 0.92% | 418MiB / 640MiB |

## 측정 중 k6 컨테이너 자원 (1초 표본 72개)

- CPU 최대 166% (한도 cpus 4 = 400%), 메모리 최대 1036 MiB (한도 2g). 한도에 닿지 않았고 OOM 종료도 없었다.
- 28회 모두 k6 응답 합계 5000, 연결 단계 오류(k6-unclassified) 0건.
