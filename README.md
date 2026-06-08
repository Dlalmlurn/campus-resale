# Campus Resale

校园二手交易平台工程基座，按 `docs/正式文档/00_project_baseline.md` 的技术口径启动：Spring Boot 模块化单体、React 前端、PostgreSQL、Flyway migration、MinIO 对象存储和 Docker Compose。

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

## M1 Auth Session Notes

A 成员身份会话分支提供 `/api/auth/register`、`/api/auth/login`、`/api/auth/logout`、`/api/auth/me`。

登录态使用 `CR_SESSION` HttpOnly Cookie。浏览器保存真实 session token，数据库只保存 `SHA-256(token)`。当前实现会滑动续期服务端 session，默认闲置 7 天、绝对 30 天；但浏览器 Cookie 的 `Max-Age` 目前按登录时写入的 7 天计算，普通请求不会刷新 Cookie。M1 CSRF 防护采用 `Origin` / `Referer` 校验。

用户名规则：3 到 20 位，只允许字母、数字和下划线；服务端统一转小写入库，不允许空格和其他符号。

演示账号密码统一为 `520zikejiang`：

| 用户名 | 角色 | 说明 |
| --- | --- | --- |
| `content_admin` | `CONTENT_ADMIN` | 内容管理员，允许多端登录，便于联调。 |
| `super_admin` | `SUPER_ADMIN` | 超级管理员，只允许单端登录。 |
| `seller_demo` | `REGISTERED_USER`, `VERIFIED_STUDENT` | 卖家演示账号，名下有在售商品（V21 种子）。 |
| `buyer_demo` | `REGISTERED_USER`, `VERIFIED_STUDENT` | 买家演示账号，演示完整下单闭环（V21 种子）。 |
| `student_demo` | `REGISTERED_USER`, `VERIFIED_STUDENT` | 已认证学生演示账号。 |
| `user_demo` | `REGISTERED_USER` | 普通注册用户演示账号。 |

本地联调示例：

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "Origin: http://localhost:5173" \
  -d '{"username":"student_demo","password":"520zikejiang"}'
```

注册接口永远只授予 `REGISTERED_USER`，不会接受前端传入角色。B 成员认证表合并后，`CurrentUser.canTrade` 已改为完整规则：认证分数至少 60、学生证或校园卡因子通过、认证状态为 `APPROVED`，并且用户拥有 `VERIFIED_STUDENT` 角色。

## M1 File And Campus Verification Notes

B 成员文件与校园认证分支提供 `/api/files/*`、`/api/verifications/*` 和 `/api/admin/verifications/*`。

文件二进制进入 MinIO，数据库 `stored_files` 只保存元数据。校园认证材料固定为 `ADMIN_ONLY`：本人只能查看元数据和脱敏预览，管理员读取原件时会写入 `sensitive_access_logs`。

校园认证流程为：

```text
上传认证材料
  -> PUT /api/verifications/me 保存资料
  -> POST /api/verifications/me/submit 提交审核
  -> 管理员 approve/reject
```

M1 中 `PUT /api/verifications/me` 不会自动进入审核队列；提交审核必须已有学生证或校园卡材料。`student_demo` 已补齐认证表种子数据，用于演示完整交易权限。

## AI 发布辅助（N4）

`POST /api/intelligence/goods-assist` 提供商品发布优化建议。采用"LLM + 稳健规则"双引擎：

- 配置 `campus-resale.ai`（DeepSeek，OpenAI 兼容）后由 LLM 生成文案；未配置或调用失败时自动退回本地规则引擎，离线/答辩均可用。
- 响应 `assistSource` 标识来源（`LLM` / `RULES`）。
- 风险判定以规则引擎为下限：即使 LLM 判低风险，命中高风险词仍强制按更高风险提示并发治理提醒，AI 不会自动审核/下架/处罚。
- 默认每人每天 5 次，调用写入 `ai_assist_records` 与后台审计日志。

环境变量（留空 `CAMPUS_RESALE_AI_API_KEY` 即走规则引擎）：

```bash
CAMPUS_RESALE_AI_ENABLED=true
CAMPUS_RESALE_AI_BASE_URL=https://api.deepseek.com
CAMPUS_RESALE_AI_API_KEY=sk-xxx
CAMPUS_RESALE_AI_MODEL=deepseek-chat
```

## 生产部署（HTTPS）

一键起 NAS HTTPS 生产栈：

```bash
cp .env.prod.example .env.prod   # 填域名、邮箱、强密码、可选 AI 密钥
docker compose --env-file .env.prod -f compose.prod.yaml up -d --build
```

前置 Caddy 默认对外发布 HTTP 4342 / HTTPS 4343，后端固定开启 Cookie `Secure` 并把 CORS/CSRF Origin 收敛到真实域名端口。当前 NAS 非标准端口默认使用 Caddy 内置 CA；需要浏览器原生信任证书时，按部署文档配置 DNS challenge 或上游 80/443 转发。
详见 [`docs/部署/HTTPS部署与运维说明.md`](docs/部署/HTTPS部署与运维说明.md)，答辩材料见 [`docs/答辩/`](docs/答辩/)。
