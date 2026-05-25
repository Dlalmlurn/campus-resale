# Campus Resale

校园二手交易平台工程基座，按 `docs/00_project_baseline.md` 的技术口径启动：Spring Boot 模块化单体、React 前端、PostgreSQL、Flyway migration、MinIO 对象存储和 Docker Compose。

## Quick Start

```bash
cp .env.example .env
docker compose up --build
```

服务入口：

- Frontend: http://localhost:3000
- Backend health: http://localhost:8080/api/health
- Actuator health: http://localhost:8080/actuator/health
- MinIO console: http://localhost:9001
- PostgreSQL host port: 15432

## Local Development

Backend:

```bash
cd backend
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Checks:

```bash
./scripts/check.sh
```

## M0 Scope

- `backend/`: Spring Boot base application, configuration loading, module registry, health API and Flyway migrations.
- `frontend/`: React/Vite base application with API health integration and workspace entry points.
- `compose.yaml`: one-command local stack for frontend, backend, PostgreSQL and MinIO.
- `scripts/check.sh`: backend and frontend test/build verification.
