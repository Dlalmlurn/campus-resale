# HTTPS 生产部署与运维说明

本文档描述如何把校园二手交易平台部署到飞牛 NAS 或同类 Debian 系公网主机，并启用 HTTPS。
对应编排文件：仓库根目录的 [`compose.prod.yaml`](../../compose.prod.yaml)、[`deploy/Caddyfile`](../../deploy/Caddyfile)、[`.env.prod.example`](../../.env.prod.example)。

## 拓扑

```text
浏览器 --HTTPS(4343)--> Caddy --(80)--> frontend(nginx)
浏览器 --HTTPS(4343)/api,/ws --> Caddy --> backend(8080)
backend --> postgres / minio
```

- Caddy 是唯一对公网开放的入口（默认 HTTP 4342 / HTTPS 4343），负责 TLS 终止、HTTP 跳转、API 与 WebSocket 反代。
- frontend 容器内部 nginx 只负责静态 SPA；`/api` 与 `/ws` 由 Caddy 直接反代到 backend，因此整站对浏览器是**同源**，Cookie 能稳定回传。
- postgres、minio、backend、frontend 均只在 compose 内网通信，不对公网暴露端口；MinIO 控制台仅绑定 `127.0.0.1:9001`，运维可经 SSH 隧道访问。

## 前置条件

1. 一台具备公网 IP 的 Linux 服务器，已安装 Docker 与 Docker Compose 插件。
2. 一个域名（如 `campus.example.edu`）的 A/AAAA 记录已解析到该服务器。
3. 安全组/防火墙放行入站 **4342** 和 **4343**。若路由器或云安全组还做了外层端口转发，务必让外部访问端口保持为 **4342/4343**。

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
curl -k -I https://<PUBLIC_DOMAIN>:4343/                 # 200；默认内置 CA 场景需 -k 或在浏览器信任证书
curl -k    https://<PUBLIC_DOMAIN>:4343/api/health        # {"status":"UP"} 等健康响应
```

## 关键安全收口（已在编排中固定）

- **Cookie Secure**：backend 设 `CAMPUS_RESALE_COOKIE_SECURE=true`，登录 Cookie 追加 `Secure`，仅经 HTTPS 回传；`HttpOnly`、`SameSite=Lax` 始终生效。
- **CORS/CSRF Origin**：backend 的 `CAMPUS_RESALE_CORS_ALLOWED_ORIGINS` 收敛为 `https://<PUBLIC_DOMAIN>:4343`，写请求的 Origin/Referer 校验只认该域名端口。
- **HSTS 与基础响应头**：Caddy 注入 `Strict-Transport-Security`、`X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy`。
- **密码与密钥不入库**：所有口令来自 `.env.prod`（被 `.gitignore` 排除），仓库只保留 `.env.prod.example` 模板。

## TLS 证书口径

飞牛 NAS 固定使用 4342/4343 这类非标准公网端口时，Let's Encrypt 的 HTTP-01/TLS-ALPN 校验无法直接访问标准 80/443 端口。当前 `deploy/Caddyfile` 默认使用 `tls internal`，浏览器首次访问需要信任 Caddy 本地 CA 或接受自签证书风险。

需要浏览器原生信任的公网证书时，采用二选一方案：

- 路由器或上游网关把公网 80/443 转发到本机 Caddy，并把 Caddy 站点改回标准端口。
- 使用 Caddy DNS challenge 插件和域名 DNS API 签发证书，再保留 4342/4343 对外访问。

仅用 IP 或内网做答辩演示、暂无可解析域名时，也可以继续使用默认 `tls internal`，无需再手工修改 Caddyfile。等价最小站点块如下：

```caddyfile
:4343 {
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

- **浏览器提示证书不受信任**：默认 `tls internal` 属于内置 CA 证书，公网浏览器不会自动信任；按上文 TLS 证书口径配置 DNS challenge 或标准 80/443 后再启用公网证书。
- **登录后立即掉登录态**：多为 Cookie 未带 `Secure`/`SameSite` 不匹配或 Origin 不在白名单；核对 `CAMPUS_RESALE_COOKIE_SECURE` 与 `CAMPUS_RESALE_CORS_ALLOWED_ORIGINS`。
- **迁移失败**：查看 backend 启动日志中的 Flyway 输出；生产库不要手工改表，统一走新增 migration。
