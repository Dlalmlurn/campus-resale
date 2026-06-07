# N3/N4 治理闭环、AI 发现增强和收尾说明

本文记录 `feature/chenshengzhang-n3-n4-governance-ai` 分支在 N3/N4 阶段补齐的功能、演示路径和验证方式，方便 PR 审查、答辩演示和后续部署交接。

## N3 治理闭环

- 处罚与交易权限联动：`TRADE_RESTRICT` 和 `ACCOUNT_LOCK` 会进入交易资格判断，限制发布、下单等主链路动作。
- 管理员裁决边界：管理员可以处理举报、申诉、退款和处罚解除；普通用户只能提交本人相关的举报、申诉和证据。
- 证据权限和留痕：举报、申诉、订单证据文件保持私有；管理员查看敏感证据会写入 `sensitive_access_logs`。
- 治理通知：举报提交、举报处理、申诉审核、退款状态和处罚产生都会写入站内通知。
- 举报成立联动：举报成立后可联动商品下架、订单关闭、信用扣减和处罚记录。
- 申诉通过反向修正：申诉通过后可撤销处罚影响，并写入信用修正记录。
- 信用摘要聚合：信用摘要从订单履约、评价、举报处罚和申诉修正中形成对外标签和内部等级。
- 前端入口：主导航“治理”进入举报、申诉、处罚、信用和后台治理队列统一页面。

## N4 AI 发布辅助和发现增强

- AI 权限与频率：`/api/intelligence/goods-assist` 按用户、场景、当天次数限制调用，默认每日 5 次。
- AI 审计：每次 AI 发布辅助都会记录后台操作日志，关联 `INTELLIGENCE_RECORD`。
- AI 生成留存：标题、描述、价格、优化建议、分类、标签、风险原因和提醒会写入 `ai_assist_records`。
- AI 双引擎：`campus-resale.ai` 配置 DeepSeek（OpenAI 兼容）后由 LLM 生成文案，未配置或调用失败时自动退回稳健规则引擎；响应 `assistSource` 标识来源（`LLM` / `RULES`），前端展示来源徽章。
- AI 风险兜底：风险判定以规则引擎为下限。即使 LLM 判低风险，命中高风险词仍强制按更高风险提示，LLM 不能把风险降级。
- AI 裁决边界：AI 只给建议和风险提醒，不会自动审核、下架、封禁或处罚。
- 高风险提醒：命中高风险词时，会给用户发送 `AI_REVIEW_REMINDER` 站内通知，提示人工修改后再提交审核。
- 发布页接入：卖家发布商品页提供“AI 发布辅助”，可生成标题、描述、分类、标签和风险说明，并支持一键应用文案。
- 发现页推荐：商品发现页请求后端 `RECOMMENDED` 排序，商品卡片展示后端返回的推荐理由。
- 发现页互动：商品卡片支持直接收藏商品和关注卖家，并保留点击主卡片进入详情的路径。

## 演示路径

1. 使用演示账号登录（统一密码 `520zikejiang`）。
   - 卖家（已认证，名下有在售商品）：`seller_demo`
   - 买家（已认证）：`buyer_demo`
   - 普通已认证学生：`student_demo`
   - 内容管理员：`content_admin`
   - 超级管理员：`super_admin`
   - 完整演示路径见 [`docs/答辩/演示台本.md`](../答辩/演示台本.md)。
2. 进入“商品”页，查看推荐理由，点击商品卡片底部的“收藏”和“关注卖家”。
3. 进入“发布”页，填写商品标题、描述、价格，点击“生成优化建议”，展示 AI 分类、标签、风险和审计提醒。
4. 用包含“代写”等高风险词的商品文案触发 AI 风险提醒，再进入“通知”页查看 AI 风险通知。
5. 进入“治理”页，演示举报、申诉、处罚处理、信用摘要和后台治理队列。
6. 管理员进入“后台”，查看统计看板、审核队列、审计日志和资金管理入口。

## Docker 空库启动验证

推荐验证步骤：

```bash
docker compose down -v
docker compose up --build
```

如果本机 `3000` 端口已被占用，可以临时改前端端口：

```bash
FRONTEND_PORT=3300 docker compose up --build
```

启动完成后检查：

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/actuator/health
```

本分支新增 Flyway 迁移：

- `V20__n4_ai_review_notifications.sql`：补齐 `AI_REVIEW_REMINDER` 通知类型约束。
- `V21__seed_demo_buyer_seller.sql`：补齐 `seller_demo` / `buyer_demo` 已认证学生账号与 seller_demo 在售商品，使买卖双方闭环可直接演示。

如果本机 Docker Desktop 未启动，可以先运行：

```bash
docker compose config
```

该命令只校验 Compose 配置是否可解析，不会验证 PostgreSQL、MinIO 和 Flyway 真实启动。

## 部署收口提醒

- 公网 HTTPS 一键部署：`docker compose --env-file .env.prod -f compose.prod.yaml up -d --build`，详见 [`docs/部署/HTTPS部署与运维说明.md`](../部署/HTTPS部署与运维说明.md)。
- 该编排前置 Caddy 自动申请/续期证书，并已固定 `CAMPUS_RESALE_COOKIE_SECURE=true`、`CAMPUS_RESALE_CORS_ALLOWED_ORIGINS=https://<域名>`，登录 Cookie 追加 `Secure`。
- 后端新增 `campus-resale.security.cookie-secure` 开关（环境变量 `CAMPUS_RESALE_COOKIE_SECURE`），本地 HTTP 默认 false，HTTPS 部署置 true。
- PostgreSQL、MinIO、DeepSeek 密钥均来自 `.env.prod`（被 `.gitignore` 排除），仓库只保留 `.env.prod.example` 模板，不要使用开发默认值。
- 如需启用真实 AI：填 `CAMPUS_RESALE_AI_API_KEY` 并设 `CAMPUS_RESALE_AI_ENABLED=true`；留空则自动走规则引擎，演示不依赖外网。
- 答辩材料见 [`docs/答辩/`](../答辩/)：演示台本、验收清单、截图清单。
