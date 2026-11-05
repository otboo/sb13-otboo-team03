# 옷장을 부탁해 (OTBOO)
[![codecov](https://codecov.io/gh/otboo/sb13-otboo-team03/graph/badge.svg?token=VWKJJ5DL3P)](https://codecov.io/gh/otboo/sb13-otboo-team03)

## 개발 문서

- [의상 이미지·피드 연동 검증 및 S3 정리 실패 재처리](docs/clothes-image-verification.md)

## 로컬 분산 환경 실행

Docker Compose로 Nginx, Spring Boot 2개 인스턴스, PostgreSQL, Redis를 실행합니다.

### 1. 환경변수 설정

`.env.example`을 참고하여 `.env` 파일을 작성합니다.

### 2. 실행

```bash
docker compose up -d --build
```

### 3. 확인

```bash
docker compose ps
```

애플리케이션 접속: http://localhost:3000

### 4. 종료

```bash
docker compose down
```