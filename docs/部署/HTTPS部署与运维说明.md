# HTTPS 生产部署与运维说明

本文档描述如何把校园二手交易平台部署到一台公网服务器，并启用 HTTPS。
对应编排文件：仓库根目录的 [`compose.prod.yaml`](../../compose.prod.yaml)、[`deploy/Caddyfile`](../../deploy/Caddyfile)、[`.env.prod.example`](../../.env.prod.example)。

## 拓扑

```text
浏览器 --HTTPS(443)--> Caddy --(80)--> frontend(nginx) --/api,/ws--> backend(8080)
                                                                   backend --> postgres / minio
```

- Caddy 是唯一对公网开放的入口（80/443），负责 TLS 终止与自动证书续期。
- frontend 容器内部 nginx 把 `/api` 与 `/ws` 反代到 backend，因此整站对浏览器是**同源**，无需放开跨域，Cookie 也能稳定回传。
- postgres、minio、backend、frontend 均只在 compose 内网通信，不对公网暴露端口；MinIO 控制台仅绑定 `127.0.0.1:9001`，运维可经 SSH 隧道访问。

## 前置条件

1. 一台具备公网 IP 的 Linux 服务器，已安装 Docker 与 Docker Compose 插件。
2. 一个域名（如 `campus.example.edu`）的 A/AAAA 记录已解析到该服务器。
3. 安全组/防火墙放行入站 **80** 和 **443**（80 用于 ACME HTTP-01 验证，443 用于正式访问）。

## 部署步骤

```bash
# 1. 拉取代码
git clone <repo-url> campus-resale && cd campus-resale

# 2. 准备生产环境变量
cp .env.prod.example .env.prod
#   编辑 .env.prod：
#   - PUBLIC_DOMAIN / ACME_EMAIL 填真实域名与邮箱
#   - POSTGRES_PASSWORD / MINIO_ROOT_PASSWORD 改成强随机密码
#   - 如需启用真实 AI，填 CAMPUS_RESALE_AI_API_KEY 并设 CAMPUS_RESALE_AI_ENABLED=true

# 3. 构建并启动
docker compose --env-file .env.prod -f compose.prod.yaml up -d --build

# 4. 观察日志，确认 Flyway 迁移与 Caddy 取证成功
docker compose --env-file .env.prod -f compose.prod.yaml logs -f caddy backend
```

启动完成后验证：

```bash
curl -I https://<PUBLIC_DOMAIN>/                 # 200，且证书有效
curl    https://<PUBLIC_DOMAIN>/api/health        # {"status":"UP"} 等健康响应
```

## 关键安全收口（已在编排中固定）

- **Cookie Secure**：backend 设 `CAMPUS_RESALE_COOKIE_SECURE=true`，登录 Cookie 追加 `Secure`，仅经 HTTPS 回传；`HttpOnly`、`SameSite=Lax` 始终生效。
- **CORS/CSRF Origin**：backend 的 `CAMPUS_RESALE_CORS_ALLOWED_ORIGINS` 收敛为 `https://<PUBLIC_DOMAIN>`，写请求的 Origin/Referer 校验只认该域名。
- **HSTS 与基础响应头**：Caddy 注入 `Strict-Transport-Security`、`X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy`。
- **密码与密钥不入库**：所有口令来自 `.env.prod`（被 `.gitignore` 排除），仓库只保留 `.env.prod.example` 模板。

## 无公网域名的演示降级

仅用 IP 或内网做答辩演示、暂无可解析域名时，把 `deploy/Caddyfile` 的 site 块改为：

```caddyfile
:443 {
	tls internal
	reverse_proxy frontend:80
}
```

Caddy 会签发本地自签证书，浏览器首次访问需手动信任。此模式下仍建议保持 `CAMPUS_RESALE_COOKIE_SECURE=true`（HTTPS 已生效）。

## 常用运维命令

```bash
# 查看状态
docker compose --env-file .env.prod -f compose.prod.yaml ps

# 滚动更新（重新构建并替换）
git pull && docker compose --env-file .env.prod -f compose.prod.yaml up -d --build

# 备份数据库
docker compose --env-file .env.prod -f compose.prod.yaml exec postgres \
  pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" > backup_$(date +%F).sql

# 停止（保留数据卷）
docker compose --env-file .env.prod -f compose.prod.yaml down
```

## 回滚与排错

- **证书申请失败**：确认域名已解析到本机且 80 端口可入站；查看 `caddy` 日志。无域名时用上文 `tls internal` 降级。
- **登录后立即掉登录态**：多为 Cookie 未带 `Secure`/`SameSite` 不匹配或 Origin 不在白名单；核对 `CAMPUS_RESALE_COOKIE_SECURE` 与 `CAMPUS_RESALE_CORS_ALLOWED_ORIGINS`。
- **迁移失败**：查看 backend 启动日志中的 Flyway 输出；生产库不要手工改表，统一走新增 migration。
