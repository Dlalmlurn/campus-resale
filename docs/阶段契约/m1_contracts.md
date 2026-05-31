# M1 开发契约

本文件定义 M1「认证与商品 MVP」的短期开发契约。它不是长期产品目标文档，也不是最终 OpenAPI 说明；它用于四人并行开发时固定接口路径、数据形状、状态枚举、迁移编号和验收边界。

正式业务口径仍以 `00_project_baseline.md`、`02_domain_contracts.md` 和 `05_database_design.md` 为准。如果本文件与正式编号文档冲突，以正式编号文档为准。

## M1 范围

M1 只完成以下闭环：

```text
注册登录
  -> 提交校园认证
  -> 管理员审核认证通过
  -> 认证学生发布商品
  -> 管理员审核商品通过
  -> 商品进入公开列表、搜索和详情页
```

M1 不实现：

- WebSocket 实时聊天。
- 订单、支付、退款、结算。
- AI 发布辅助。
- 复杂推荐。
- 评价信用。
- 举报申诉治理闭环。
- 公网 HTTPS 部署。

## 并行边界

| 成员 | 负责模块 | 主要包/目录 | 主要 API |
| --- | --- | --- | --- |
| A | 身份、会话、权限 | `backend/src/main/java/com/campusresale/identity` | `/api/auth/*` |
| B | 文件、校园认证 | `identity` 与平台文件服务 | `/api/files/*`, `/api/verifications/*`, `/api/admin/verifications/*` |
| C | 商品、审核、搜索 | `backend/src/main/java/com/campusresale/goods` | `/api/goods/*`, `/api/admin/goods/*` |
| D | 前端、联调验收 | `frontend/src` | 页面路由、API client、路由守卫 |

## 通用 API 约定

### 路径

所有业务接口以 `/api` 开头。

| 接口族 | 路径前缀 |
| --- | --- |
| Auth | `/api/auth/*` |
| File | `/api/files/*` |
| Campus Verification | `/api/verifications/*` |
| Goods | `/api/goods/*` |
| Admin | `/api/admin/*` |

### 成功响应

成功响应直接返回资源对象，不强制包一层 `data`。

分页响应统一使用：

```json
{
  "items": [],
  "page": 1,
  "pageSize": 20,
  "total": 0
}
```

分页参数：

- `page` 从 `1` 开始。
- `pageSize` 默认 `20`，最大 `50`。

### 错误响应

所有业务错误使用统一 JSON 结构：

```json
{
  "code": "AUTH_REQUIRED",
  "message": "请先登录",
  "details": {
    "field": "username"
  },
  "traceId": "optional-request-trace-id"
}
```

`details` 可以为空对象。前端展示优先使用 `message`，调试和测试优先断言 `code`。

常用错误码：

| code | HTTP | 含义 |
| --- | ---: | --- |
| `VALIDATION_FAILED` | 400 | 请求字段格式或业务校验失败。 |
| `AUTH_REQUIRED` | 401 | 未登录或 session 失效。 |
| `CSRF_REQUIRED` | 403 | 非安全方法缺少 CSRF 防护凭据。 |
| `FORBIDDEN` | 403 | 登录用户无权限执行该动作。 |
| `NOT_FOUND` | 404 | 目标资源不存在或不可见。 |
| `CONFLICT` | 409 | 唯一约束、状态机或并发冲突。 |
| `PAYLOAD_TOO_LARGE` | 413 | 上传文件过大。 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 文件类型不支持。 |
| `RATE_LIMITED` | 429 | 触发限流或 24 小时提交次数限制。 |
| `INTERNAL_ERROR` | 500 | 未预期服务端错误。 |

### 登录态与 CSRF

- 登录态通过 HttpOnly Cookie 承载，建议 Cookie 名为 `CR_SESSION`。
- 服务端只保存 session token hash，不保存明文 token。
- Cookie 设置建议：`HttpOnly`, `SameSite=Lax`, `Path=/`。
- M1 最低 CSRF 防护：所有非 `GET/HEAD/OPTIONS` 请求校验 `Origin` 或 `Referer` 为本站来源。
- 如果实现 token 方案，使用可读 Cookie `CR_CSRF` + 请求头 `X-CSRF-Token`，但不要让前端读取 `CR_SESSION`。

### 时间与金额

- 时间字段使用 ISO-8601 字符串，后端以 `TIMESTAMPTZ` 保存。
- 金额字段使用字符串或定点小数，禁止前端和后端使用浮点误差参与金额判断。
- M1 商品价格字段为 `listPrice`，对应数据库 `NUMERIC(12,2)`。

## 共享数据结构

### CurrentUser

`GET /api/auth/me` 和登录注册成功后返回：

```json
{
  "id": 1,
  "username": "alice",
  "nickname": "Alice",
  "roles": ["REGISTERED_USER"],
  "verificationStatus": "PENDING_REVIEW",
  "canTrade": false
}
```

字段约定：

| 字段 | 说明 |
| --- | --- |
| `id` | 用户 id。 |
| `username` | 登录用户名。 |
| `nickname` | 前端展示昵称。 |
| `roles` | 角色 code 数组。 |
| `verificationStatus` | 当前校园认证状态。没有认证记录时为 `NONE`。 |
| `canTrade` | 是否具备完整交易权限。M1 中只有认证审核通过后为 `true`。 |

并行开发过渡说明：

- A 成员身份会话分支在 B 成员 `campus_auths` 与 `campus_auth_factors` 合并前，暂时按 `VERIFIED_STUDENT` 角色推导 `verificationStatus = APPROVED` 与 `canTrade = true`。
- B 成员认证表和认证审核逻辑合并后，应恢复为完整规则：`score >= 60`、至少一项 `STUDENT_CARD` 或 `CAMPUS_CARD` 因子通过、`CampusVerificationStatus = APPROVED`、用户拥有 `VERIFIED_STUDENT` 角色。
- 该过渡逻辑只用于 M1 并行联调和演示账号，不改变正式认证口径。

### StoredFileSummary

```json
{
  "id": 10,
  "originalName": "student-card.jpg",
  "contentType": "image/jpeg",
  "byteSize": 245760,
  "fileKind": "CAMPUS_AUTH_MATERIAL",
  "visibilityScope": "ADMIN_ONLY",
  "auditStatus": "PENDING",
  "createdAt": "2026-05-26T09:00:00Z"
}
```

### CampusVerification

```json
{
  "id": 1,
  "realName": "张三",
  "studentNo": "20260001",
  "department": "计算机学院",
  "campusEmail": "zhangsan@example.edu",
  "score": 90,
  "status": "PENDING_REVIEW",
  "factors": [
    {
      "factorType": "NAME_STUDENT_NO",
      "status": "VERIFIED",
      "scoreValue": 40
    }
  ],
  "failureReason": null,
  "updatedAt": "2026-05-26T09:00:00Z"
}
```

### GoodsSummary

```json
{
  "id": 100,
  "title": "九成新显示器",
  "description": "自用显示器，配件齐全。",
  "conditionLevel": "LIKE_NEW",
  "listPrice": "399.00",
  "status": "ON_SALE",
  "auditStatus": "APPROVED",
  "seller": {
    "id": 1,
    "nickname": "Alice"
  },
  "category": {
    "id": 1,
    "code": "DIGITAL",
    "name": "数码电子"
  },
  "primaryImage": {
    "id": 10,
    "url": "/api/files/10/content"
  },
  "publishedAt": "2026-05-26T09:00:00Z"
}
```

## 状态枚举

### 角色

沿用 M0 种子数据：

- `REGISTERED_USER`
- `VERIFIED_STUDENT`
- `CONTENT_ADMIN`
- `SUPER_ADMIN`

### 用户与认证

| 枚举 | 值 |
| --- | --- |
| `AccountStatus` | `ACTIVE`, `LOCKED`, `DISABLED` |
| `CampusVerificationStatus` | `NONE`, `DRAFT`, `ACCUMULATING`, `PENDING_REVIEW`, `APPROVED`, `REJECTED`, `INVALID` |
| `CampusFactorType` | `NAME_STUDENT_NO`, `DEPARTMENT`, `CAMPUS_EMAIL`, `STUDENT_CARD`, `CAMPUS_CARD` |
| `CampusFactorStatus` | `PENDING`, `VERIFIED`, `REJECTED`, `EXPIRED` |

认证分值：

| 因子 | 分值 |
| --- | ---: |
| `NAME_STUDENT_NO` | 40 |
| `DEPARTMENT` | 10 |
| `CAMPUS_EMAIL` | 10 |
| `STUDENT_CARD` 或 `CAMPUS_CARD` | 40 |

M1 交易权限规则：

- `score >= 60`
- 至少一项 `STUDENT_CARD` 或 `CAMPUS_CARD` 因子通过。
- `CampusVerificationStatus = APPROVED`
- 用户拥有 `VERIFIED_STUDENT` 角色。

### 文件

| 枚举 | 值 |
| --- | --- |
| `FileKind` | `AVATAR`, `GOODS_IMAGE`, `CAMPUS_AUTH_MATERIAL` |
| `VisibilityScope` | `PUBLIC`, `PRIVATE`, `PARTICIPANTS`, `ADMIN_ONLY` |
| `FileAuditStatus` | `PENDING`, `APPROVED`, `REJECTED` |

M1 文件可见性：

- 商品图片上传时先保存为 `PRIVATE`，商品审核通过后可转为 `PUBLIC` 或通过公开读取规则开放。
- 校园认证材料必须为 `ADMIN_ONLY`。
- 管理员查看认证材料时必须写入 `sensitive_access_logs`。

### 商品

| 枚举 | 值 |
| --- | --- |
| `ConditionLevel` | `NEW`, `LIKE_NEW`, `LIGHTLY_USED`, `NOTICEABLY_USED` |
| `GoodsStatus` | `DRAFT`, `PENDING_REVIEW`, `ON_SALE`, `RESERVED`, `SOLD`, `OFF_SHELF`, `DELETED` |
| `GoodsAuditStatus` | `NOT_SUBMITTED`, `PENDING`, `APPROVED`, `REJECTED` |
| `AuditResult` | `APPROVED`, `REJECTED` |

M1 只需要走通：

```text
DRAFT -> PENDING_REVIEW -> ON_SALE
                  \-> DRAFT 或 OFF_SHELF（审核驳回后的实现可二选一，但必须写明）
```

建议驳回后商品保持 `DRAFT`，`auditStatus = REJECTED`，允许卖家修改后重新提交。

## Auth API

### POST `/api/auth/register`

注册普通用户并创建登录 session。

请求：

```json
{
  "username": "alice",
  "password": "Passw0rd!",
  "nickname": "Alice",
  "personalEmail": "alice@example.com"
}
```

响应：`CurrentUser`

规则：

- `username` 唯一，长度 3-20，只允许字母、数字和下划线；服务端统一转小写入库，不允许空格和其他符号。
- `password` 最少 8 位。
- 密码必须使用 bcrypt 或 Argon2id 存储。
- 注册成功后默认授予 `REGISTERED_USER`。

### POST `/api/auth/login`

请求：

```json
{
  "username": "alice",
  "password": "Passw0rd!"
}
```

响应：`CurrentUser`

规则：

- 登录成功创建 `user_sessions` 记录。
- 管理员账号如果实现单端登录，应在登录时撤销同管理员旧 session。

### POST `/api/auth/logout`

撤销当前 session。

响应：

```json
{
  "ok": true
}
```

### GET `/api/auth/me`

返回当前登录用户。未登录返回 `401 AUTH_REQUIRED`。

响应：`CurrentUser`

## File API

### POST `/api/files`

上传文件。使用 `multipart/form-data`。

字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `file` | 是 | 文件二进制。 |
| `fileKind` | 是 | `AVATAR`, `GOODS_IMAGE`, `CAMPUS_AUTH_MATERIAL`。 |
| `visibilityScope` | 否 | 不传时由 `fileKind` 推导。 |

响应：`StoredFileSummary`

规则：

- 图片只允许 `image/jpeg`, `image/png`, `image/webp`。
- 商品图片和认证材料均拒绝可执行文件和伪造 MIME 类型。
- M1 建议单文件上限 5 MB。
- 文件二进制进入 MinIO，数据库只保存 `stored_files` 元数据。

### GET `/api/files/{id}`

返回文件元数据。私有文件只有拥有者或管理员可见。

响应：`StoredFileSummary`

### GET `/api/files/{id}/content`

返回文件内容或临时重定向。

规则：

- `PUBLIC` 文件允许游客读取。
- `PRIVATE` 文件只允许拥有者读取。
- `ADMIN_ONLY` 文件只允许管理员读取，并且如果目标是认证材料，必须写入 `sensitive_access_logs`。

## Campus Verification API

### GET `/api/verifications/me`

返回当前用户认证记录。没有记录时返回：

```json
{
  "status": "NONE",
  "score": 0,
  "factors": []
}
```

### PUT `/api/verifications/me`

创建或更新当前用户认证草稿。

请求：

```json
{
  "realName": "张三",
  "studentNo": "20260001",
  "department": "计算机学院",
  "campusEmail": "zhangsan@example.edu",
  "documentType": "STUDENT_CARD",
  "documentFileIds": [10]
}
```

响应：`CampusVerification`

规则：

- 姓名和学号为基础因子，提交后计入 40 分。
- 院系填写后计入 10 分。
- 校园邮箱 M1 先只校验后缀；后缀匹配则标为 `VERIFIED` 并计入 10 分，真实邮件链接验证作为 M1 polish。
- `documentType` 只能是 `STUDENT_CARD` 或 `CAMPUS_CARD`。
- 学生证或校园卡材料由管理员审核通过后标为 `VERIFIED` 并计入 40 分。
- 可信度达到 50 分后进入 `PENDING_REVIEW`。

### POST `/api/verifications/me/submit`

提交认证进入审核。

响应：`CampusVerification`

规则：

- 分数低于 50 返回 `409 CONFLICT`。
- 同一因子 24 小时内重提超过 3 次返回 `429 RATE_LIMITED`。

### GET `/api/admin/verifications`

管理员查询认证审核队列。

查询参数：

- `status`
- `page`
- `pageSize`

响应：分页 `CampusVerification`。

权限：`CONTENT_ADMIN` 或 `SUPER_ADMIN`。

### POST `/api/admin/verifications/{id}/approve`

管理员审核通过。

请求：

```json
{
  "reason": "材料清晰，信息一致"
}
```

响应：`CampusVerification`

规则：

- 审核通过后设置 `CampusVerificationStatus = APPROVED`。
- 满足完整交易权限时授予 `VERIFIED_STUDENT`。
- 写入 `audit_records` 或 `operation_logs`。如果 M1 暂不建 `audit_records`，必须写入 `operation_logs`。

### POST `/api/admin/verifications/{id}/reject`

请求：

```json
{
  "reason": "证件照片不清晰"
}
```

响应：`CampusVerification`

规则：

- 设置 `CampusVerificationStatus = REJECTED`。
- 不授予 `VERIFIED_STUDENT`。
- 已有 `VERIFIED_STUDENT` 角色时，M1 可暂不自动移除，但后续必须补齐失效规则。

## Goods API

### GET `/api/categories`

返回启用分类。

响应：

```json
[
  {
    "id": 1,
    "code": "DIGITAL",
    "name": "数码电子",
    "parentId": null
  }
]
```

### GET `/api/tags`

返回启用标签。

### GET `/api/campus-places`

返回启用校园地点。

响应：

```json
[
  {
    "id": 1,
    "campus": "主校区",
    "name": "图书馆门口",
    "detail": "适合白天面交"
  }
]
```

### POST `/api/goods/drafts`

认证学生创建商品草稿。

请求：

```json
{
  "title": "九成新显示器",
  "description": "自用显示器，配件齐全。",
  "categoryId": 1,
  "conditionLevel": "LIKE_NEW",
  "listPrice": "399.00",
  "tradePlaceId": 1,
  "tradePlaceDetail": "图书馆门口",
  "availableTimeText": "工作日晚上",
  "imageFileIds": [10],
  "tagIds": [1, 2]
}
```

响应：`GoodsSummary`

权限：`VERIFIED_STUDENT`

规则：

- 标题 2-80 字符。
- 描述 10-2000 字符。
- 图片 1-15 张。
- 商品创建后 `status = DRAFT`, `auditStatus = NOT_SUBMITTED`。

### PATCH `/api/goods/{id}`

卖家修改自己的草稿或被驳回商品。

规则：

- 只有卖家本人可改。
- `PENDING_REVIEW` 和 `ON_SALE` 默认不可直接改核心字段。M1 可要求先下架或驳回后再改。

### POST `/api/goods/{id}/submit`

提交商品审核。

响应：`GoodsSummary`

规则：

- 只有卖家本人可提交。
- 卖家必须是 `VERIFIED_STUDENT`。
- 至少 1 张图片，最多 15 张。
- 设置 `status = PENDING_REVIEW`, `auditStatus = PENDING`。

### GET `/api/goods/mine`

卖家查看自己的商品。

查询参数：

- `status`
- `auditStatus`
- `page`
- `pageSize`

响应：分页 `GoodsSummary`。

### GET `/api/goods`

公开商品列表与搜索。

查询参数：

| 参数 | 说明 |
| --- | --- |
| `keyword` | 标题/描述关键词。 |
| `categoryId` | 分类 id。 |
| `minPrice` | 最低价格。 |
| `maxPrice` | 最高价格。 |
| `conditionLevel` | 成色。 |
| `placeId` | 校园地点。 |
| `sort` | `LATEST`, `PRICE_ASC`, `PRICE_DESC`。 |
| `page`, `pageSize` | 分页。 |

响应：分页 `GoodsSummary`。

规则：

- 只返回 `status = ON_SALE` 且 `auditStatus = APPROVED` 且未删除的商品。
- 关键词搜索优先使用 PostgreSQL 全文检索和 `pg_trgm`；如果当天来不及，可先用 `ILIKE`，但 migration 必须预留搜索索引或后续补齐。

### GET `/api/goods/{id}`

商品详情。

规则：

- 游客只能查看公开在售商品。
- 卖家本人可以查看自己的草稿、审核中和驳回商品。
- 管理员可以查看审核职责范围内商品。

### GET `/api/admin/goods`

管理员查询商品审核队列。

查询参数：

- `auditStatus`
- `status`
- `page`
- `pageSize`

权限：`CONTENT_ADMIN` 或 `SUPER_ADMIN`。

### POST `/api/admin/goods/{id}/approve`

请求：

```json
{
  "reason": "信息完整，图片清晰"
}
```

响应：`GoodsSummary`

规则：

- 设置 `status = ON_SALE`, `auditStatus = APPROVED`, `publishedAt = now()`。
- 商品图片开放公开读取。
- 写入审核/操作日志。

### POST `/api/admin/goods/{id}/reject`

请求：

```json
{
  "reason": "图片不清晰或描述不完整"
}
```

响应：`GoodsSummary`

规则：

- 建议设置 `status = DRAFT`, `auditStatus = REJECTED`。
- 写入审核/操作日志。

## 前端路由契约

| 页面 | 路径 | 权限 |
| --- | --- | --- |
| 登录 | `/login` | 游客 |
| 注册 | `/register` | 游客 |
| 商品列表 | `/goods` | 游客可访问 |
| 商品详情 | `/goods/:id` | 游客可访问公开商品 |
| 我的认证 | `/verification` | 注册用户 |
| 发布商品 | `/seller/goods/new` | 认证学生 |
| 我的商品 | `/seller/goods` | 认证学生 |
| 认证审核 | `/admin/verifications` | 管理员 |
| 商品审核 | `/admin/goods` | 管理员 |

路由守卫：

- 未登录访问受保护页面跳转 `/login`。
- 未认证用户访问卖家页面跳转 `/verification`。
- 非管理员访问 `/admin/*` 显示无权限页。

## Migration 分配

M0 已存在：

- `users`
- `stored_files`
- `roles`
- `user_roles`
- `user_sessions`
- `campus_places`
- `categories`
- `tags`
- `system_configs`
- `operation_logs`
- `sensitive_access_logs`

M1 不重复创建上述表，只允许 `ALTER TABLE` 补字段或补约束。

| 编号 | 负责人 | 内容 |
| --- | --- | --- |
| `V3` | A | Auth 必要补充：用户字段、session 索引、管理员种子账号所需约束。 |
| `V4` | A | Auth 种子数据：测试用户、测试管理员、角色绑定。 |
| `V5` | A | Auth 修正迁移：用户名收紧为 3-20 位，只允许字母、数字和下划线。 |
| `V6` | B | `campus_auths`, `campus_auth_factors`。 |
| `V7` | B | 文件相关补充索引、认证材料约束、敏感访问日志索引补充。 |
| `V8` | B | 认证相关种子配置和修正迁移预留。 |
| `V9` | C | `goods`, `goods_images`, `goods_tags`。 |
| `V10` | C | `audit_records`, `rule_hit_records`, `forbidden_terms`。 |
| `V11` | C | 商品搜索索引、分类/标签补充种子。 |
| `V12` | C | 商品修正迁移预留。 |
| `V13` | 集成 | M1 联调修正迁移。 |
| `V14` | 集成 | M1 演示数据种子。 |

迁移规则：

- 已合并的 migration 不允许改名、删除或重写，只能新增下一号迁移。
- 所有种子数据使用 `ON CONFLICT` 保持幂等。
- 空库执行 `docker compose up --build -d` 后必须自动完成所有 migration。

## M1 种子数据

必须具备：

- 一个内容管理员账号。
- 一个超级管理员账号。
- 一个普通注册用户账号。
- 一个已认证学生账号。
- 至少 5 个分类。
- 至少 4 个标签。
- 至少 4 个校园地点。
- 至少 3 个审核通过的演示商品。
- 至少 1 个待审核认证记录。
- 至少 1 个待审核商品。

种子账号密码必须只用于本地开发和课程演示，不得用于生产部署。

建议账号：

| 用户名 | 角色 | 用途 |
| --- | --- | --- |
| `admin` | `CONTENT_ADMIN`, `SUPER_ADMIN` | 后台审核。 |
| `student_demo` | `REGISTERED_USER`, `VERIFIED_STUDENT` | 发布商品。 |
| `user_demo` | `REGISTERED_USER` | 提交认证。 |

A 成员身份会话分支的实际演示账号：

| 用户名 | 角色 | 密码 | 用途 |
| --- | --- | --- | --- |
| `content_admin` | `CONTENT_ADMIN` | `520zikejiang` | 日常内容审核，允许多端登录，便于联调和课程演示。 |
| `super_admin` | `SUPER_ADMIN` | `520zikejiang` | 系统级管理员，只允许单端登录。 |
| `student_demo` | `REGISTERED_USER`, `VERIFIED_STUDENT` | `520zikejiang` | 已认证学生演示账号。 |
| `user_demo` | `REGISTERED_USER` | `520zikejiang` | 普通注册用户演示账号。 |

## 最小验收

M1 PR 合并前必须满足：

- `./scripts/check.sh` 通过。
- 空库 migration 成功。
- 注册、登录、退出、`/api/auth/me` 可用。
- 普通用户能提交认证。
- 管理员能审核认证。
- 审核通过后用户获得 `VERIFIED_STUDENT`。
- 认证学生能发布商品并提交审核。
- 管理员能审核商品。
- 审核通过商品能在 `/api/goods` 和商品详情中公开访问。
- 未登录用户不能访问受保护接口。
- 非管理员不能访问 `/api/admin/*`。
- 管理员查看认证材料时产生敏感访问日志。
