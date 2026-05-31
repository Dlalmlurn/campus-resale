# M1 B 成员文件与校园认证实施计划

记录日期：2026-05-31

## 阅读结论

本计划基于以下文档和当前代码：

- `docs/阶段契约/m1_contracts.md`
- `docs/02_domain_contracts.md`
- `docs/05_database_design.md`
- `短期并行推进计划.md`
- `docs/项目进程记录/M1_A成员身份会话实现记录.md`
- `backend/src/main/resources/db/migration/V1__foundation_schema.sql` 到 `V5__tighten_username_rules.sql`
- `backend/src/main/java/com/campusresale/identity` 与 `backend/src/main/java/com/campusresale/platform`

B 成员负责 M1 的两条主线：

- 文件能力：`/api/files/*`，包含 MinIO 上传、`stored_files` 元数据、文件权限判断和认证材料敏感访问日志。
- 校园认证：`/api/verifications/*` 与 `/api/admin/verifications/*`，包含认证资料草稿、可信度分数、提交审核、管理员审核、授予 `VERIFIED_STUDENT`。

当前仓库已有的基础：

- `stored_files` 已在 `V1` 创建，但还没有 Java 文件服务实现。
- `operation_logs` 和 `sensitive_access_logs` 已在 `V1` 创建，可以直接用于 M1 审核和敏感材料访问留痕。
- `campus_auths` 和 `campus_auth_factors` 还不存在，按契约由 B 成员在 `V6` 创建。
- A 成员已经实现 Cookie session、`@RequireLogin`、`@RequireRole`、统一错误响应和演示账号。
- A 成员当前在 `CurrentUserMapper` 中用 `VERIFIED_STUDENT` 角色临时推导 `verificationStatus = APPROVED` 与 `canTrade = true`。B 合并认证表后，需要恢复完整规则。

## 已确认决策

本节记录 2026-05-31 已拍板口径，后续实现按这里执行。

| 编号 | 决策结果 |
| --- | --- |
| D1 | 对象存储客户端使用 MinIO Java SDK，但业务代码只依赖 `ObjectStorageClient` 接口。 |
| D2 | `documentFileIds` 使用独立关联表 `campus_auth_factor_files`，支持学生证或校园卡多张材料。 |
| D3 | `PUT /api/verifications/me` 只保存草稿或积累状态，`POST /api/verifications/me/submit` 才进入审核队列。 |
| D4 | 提交审核必须已有 `STUDENT_CARD` 或 `CAMPUS_CARD` 证件材料。 |
| D5 | 校园邮箱后缀放在 `system_configs`，M1 默认配置为 `["example.edu"]`。 |
| D6 | 文件内容由后端鉴权后代理返回 bytes，不使用预签名 URL 或直接暴露对象存储地址。 |
| D7 | 认证材料本人只能看元数据和脱敏预览，不能读取原始材料；管理员读取原始材料必须写敏感访问日志。 |
| D8 | 驳回不降级既有成功认证：原来已认证成功的用户仍保持成功；原来未认证成功的用户仍保持未成功，不授予交易权限。M1 不支持已通过认证的原地重审降级。 |
| D9 | 头像默认可见性为 `PUBLIC`。 |
| D10 | 包结构采用 `platform.storage`、`files`、`identity.verification` 三段。 |
| D11 | MIME 校验采用声明 `Content-Type` + 图片魔数校验，不引入 Apache Tika。 |
| D12 | 数据库强制约束 `CAMPUS_AUTH_MATERIAL` 必须是 `ADMIN_ONLY`。 |
| D13 | 管理员读取认证材料原件时，敏感日志 `target_type = CAMPUS_AUTH_MATERIAL`。 |
| D14 | 普通用户提交认证不写 `operation_logs`，管理员审核才写。 |

## B 成员交付边界

### 必须交付

- `POST /api/files`
- `GET /api/files/{id}`
- `GET /api/files/{id}/content`
- `GET /api/verifications/me`
- `PUT /api/verifications/me`
- `POST /api/verifications/me/submit`
- `GET /api/admin/verifications`
- `POST /api/admin/verifications/{id}/approve`
- `POST /api/admin/verifications/{id}/reject`
- `campus_auths`、`campus_auth_factors` 相关 migration。
- 文件相关索引、认证材料约束、敏感访问日志索引补充 migration。
- 认证相关种子配置或演示认证数据 migration。
- 审核通过后授予 `VERIFIED_STUDENT`。
- `GET /api/auth/me` 返回真实 `verificationStatus` 和 `canTrade`。

### 暂不交付

- 真实校园邮箱链接验证。
- 完整文件审核后台。
- 文件删除、批量上传、断点续传。
- 生产级病毒扫描。
- 独立 `audit_records` 表。M1 可先写 `operation_logs`，后续 C 成员若引入 `audit_records` 再统一。

## 与 A 成员实现的衔接点

A 成员已经提供：

- `CurrentPrincipal`：当前登录用户 id、用户名、昵称、角色、session 信息。
- `@RequireLogin`：保护普通登录用户接口。
- `@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})`：保护管理员接口。
- `UserAccountRepository.assignRole(...)`：可用于认证审核通过后授予 `VERIFIED_STUDENT`。
- `CurrentUserMapper`：当前需要从临时角色推导改成真实认证状态推导。

B 成员需要改动 A 相关代码的最小范围：

- 给 `CurrentUserMapper` 接入一个认证状态查询组件，例如 `CampusTradeEligibilityResolver`。
- 保留角色排序和响应结构，只替换 `verificationStatus`、`canTrade` 的来源。
- 审核通过时通过 `UserAccountRepository.assignRole(userId, "VERIFIED_STUDENT", adminId)` 授权。
- 不改 A 的 session token、Cookie、CSRF 逻辑。

## 包结构最终口径

曾比较的候选方案：

| 方案 | 结构 | 优点 | 风险 |
| --- | --- | --- | --- |
| A | 文件和认证都放 `identity` | 改动集中，符合“B 在 identity 与平台文件服务之间工作”的短期描述。 | 文件服务以后会被商品、私信、举报复用，放进 identity 会变成边界不清。 |
| B | `com.campusresale.files` 做文件 API，`identity` 做认证 API | 文件独立成业务模块，商品模块后续容易复用。 | 需要新增一个模块包，当前文档没有单独列 `files` 模块。 |
| C | `platform.storage` 做对象存储适配，`files` 做文件 API，`identity` 做认证 | 存储适配是平台能力，文件元数据是业务能力，认证保持身份领域内，长期边界最清楚。 | 文件数量稍多，第一步实现时需要小心别过度抽象。 |

最终采用方案 C。

建议落地为：

- `backend/src/main/java/com/campusresale/platform/storage`
  - 对象存储客户端、bucket 初始化、上传、下载流。
- `backend/src/main/java/com/campusresale/files`
  - 文件 Controller、Service、Repository、DTO、枚举。
- `backend/src/main/java/com/campusresale/identity/verification`
  - 校园认证 Controller、Service、Repository、DTO、分数计算。

这样做的原因是：对象存储以后不只服务文件 API，商品、私信、举报也都会复用；校园认证则属于身份权限域，继续放 identity 更符合正式领域契约。

## Migration 计划

### V6：认证表

创建 `campus_auths`：

- `id`
- `user_id`，唯一，一名用户一条主认证记录。
- `real_name`
- `student_no`
- `department`
- `campus_email`
- `score`，0 到 100。
- `status`，取值 `DRAFT`, `ACCUMULATING`, `PENDING_REVIEW`, `APPROVED`, `REJECTED`, `INVALID`。
- `reviewed_by_admin_id`
- `reviewed_at`
- `failure_reason`
- `identity_claim_key`
- `created_at`
- `updated_at`

创建 `campus_auth_factors`：

- `id`
- `campus_auth_id`
- `factor_type`，取值 `NAME_STUDENT_NO`, `DEPARTMENT`, `CAMPUS_EMAIL`, `STUDENT_CARD`, `CAMPUS_CARD`。
- `score_value`
- `status`，取值 `PENDING`, `VERIFIED`, `REJECTED`, `EXPIRED`。
- `submitted_value`
- `stored_file_id`
- `email_token_hash`
- `email_token_expires_at`
- `reviewed_by_admin_id`
- `reviewed_at`
- `rejected_reason`
- `submit_count_24h`
- `submit_window_started_at`
- `created_at`
- `updated_at`

已确认：`documentFileIds` 使用独立关联表，不把多张材料塞进单个因子行。

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A | `campus_auth_factors.stored_file_id` 只保存一个文件，M1 限制 `documentFileIds` 只能传 1 个。 | 最贴近正式数据库文档，最省时间。 | API 契约写的是数组，学生证正反面或多材料会被削弱。 |
| B | 同一证件因子允许多行，每行一个 `stored_file_id`，响应时按 `factor_type` 聚合。 | 不新增表，能接住数组。 | `submit_count_24h` 和审核状态容易因为多行而变复杂。 |
| C | 增加 `campus_auth_factor_files(campus_auth_factor_id, stored_file_id)`。 | 数据模型最清楚，契合数组，后续扩展最好。 | 比 M1 契约多一张表，需要在过程文档里说明。 |

最终采用方案 C。

建议索引：

- `campus_auths(user_id)` 唯一。
- `campus_auths(status, updated_at DESC)` 用于管理员审核队列。
- `campus_auths(campus_email)` 部分唯一，非空时唯一。
- `campus_auths(identity_claim_key)` 部分唯一，建议只在 `identity_claim_key IS NOT NULL` 时生效。
- `campus_auth_factors(campus_auth_id, factor_type)`。
- `campus_auth_factors(stored_file_id)`。
- `campus_auth_factor_files(campus_auth_factor_id, stored_file_id)` 唯一。
- `campus_auth_factor_files(stored_file_id)`。

### V7：文件补充

已有 `stored_files` 表，不重复创建，只补索引和约束：

- `stored_files(owner_user_id, created_at DESC)`
- `stored_files(file_kind, visibility_scope, created_at DESC)`
- `stored_files(business_type, business_id)`
- `stored_files(checksum)`
- 可选约束：`file_kind` 只能是 `AVATAR`, `GOODS_IMAGE`, `CAMPUS_AUTH_MATERIAL`。

认证材料约束候选：

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A | 数据库 `CHECK` 约束：`file_kind = 'CAMPUS_AUTH_MATERIAL'` 时 `visibility_scope = 'ADMIN_ONLY'`。 | 最硬，无法被业务代码误写坏。 | 如果后续允许本人查看认证材料，需要新增 migration 调整。 |
| B | 应用层强制推导，数据库只加索引。 | 灵活，M1 以后调整容易。 | 需要保证所有写入口都走文件服务。 |

最终采用方案 A。M1 契约明确校园认证材料必须 `ADMIN_ONLY`，先用强约束保护隐私。

敏感日志索引已有基础索引，V7 只在确实需要时补：

- `sensitive_access_logs(admin_id, target_type, created_at DESC)`
- `sensitive_access_logs(target_type, target_id, result, created_at DESC)`

### V8：认证配置和种子

建议写入以下配置：

- `campus.auth.email_suffixes`，建议 JSON：`["example.edu"]`。
- `campus.auth.document_max_count`，建议 `2`。
- `campus.auth.material_max_mb`，M1 契约建议 `5`。
- `campus.auth.factor_resubmit_limit_24h`，建议 `3`。

是否创建演示认证记录的候选方案：

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A | 给 `user_demo` 种一条 `PENDING_REVIEW` 认证记录。 | 管理员审核页立刻有数据，方便演示。 | 需要同时种认证材料文件元数据，若 MinIO 无真实对象，点开内容会失败。 |
| B | 只种配置，不种认证记录，由前端或 curl 现场提交。 | 数据真实，不会出现无对象文件。 | 演示准备多一步。 |
| C | 种认证记录和一个文本占位对象，应用启动后也能读。 | 审核演示最顺滑。 | 需要 migration 之外的对象存储种子机制，M1 成本偏高。 |

最终采用方案 B。先保证主流程真实可走，演示数据可以在集成阶段 `V14` 统一处理。

## 文件 API 计划

### POST `/api/files`

权限：登录用户。

请求：`multipart/form-data`。

字段：

- `file`
- `fileKind`
- `visibilityScope` 可选。

业务规则：

- `CAMPUS_AUTH_MATERIAL` 固定推导 `ADMIN_ONLY`，忽略或拒绝前端传入的其他可见性。
- `GOODS_IMAGE` 默认 `PRIVATE`。
- `AVATAR` 默认 `PUBLIC`。
- 只允许 `image/jpeg`, `image/png`, `image/webp`。
- M1 单文件上限建议 5 MB。
- 计算 SHA-256 checksum。
- 上传对象存储成功后写入 `stored_files`。
- 如果数据库写入失败，尽力删除已上传对象，避免对象存储残留。

对象存储客户端候选：

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A | 引入 MinIO Java SDK。 | 与 compose 中 MinIO 完全匹配，API 简单。 | 绑定 MinIO SDK，虽然兼容 S3，但供应商味道更重。 |
| B | 引入 AWS SDK S3 Client，连接 MinIO 的 S3 兼容端点。 | 长期更像“S3 兼容存储层”。 | 依赖更重，配置也更复杂。 |
| C | M1 先做本地文件系统或内存存储。 | 最快通过单测。 | 不满足文档“文件二进制进入 MinIO”，演示风险大。 |

最终采用方案 A。原因是当前 compose 已经明确使用 MinIO，M1 首要目标是把链路跑通。为了保留长期抽换空间，代码不要把 MinIO 类型暴露到业务服务，只通过 `ObjectStorageClient` 接口访问。

MIME 校验候选：

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A | 只相信 `MultipartFile.getContentType()`。 | 最省事。 | 不能防伪造 MIME，不满足契约。 |
| B | 用最小魔数校验 JPEG/PNG/WebP，再比对声明 MIME。 | 依赖少，满足 M1 安全底线。 | 识别范围有限，但 M1 只收三类图片。 |
| C | 引入 Apache Tika 做内容识别。 | 专业、可扩展。 | 依赖更重，M1 可能过度。 |

最终采用方案 B。M1 不引入 Apache Tika，避免依赖过重。

### GET `/api/files/{id}`

权限规则：

- `PUBLIC`：游客可看元数据。
- `PRIVATE`：文件拥有者或管理员可看。
- `ADMIN_ONLY`：管理员可看原始元数据；认证材料本人只能看元数据中的安全字段。
- `PARTICIPANTS`：M1 暂不使用，先返回 `FORBIDDEN` 或只允许拥有者和管理员。

最终口径：认证材料的元数据允许本人和管理员看，但对本人只返回安全字段，例如 `id`、`originalName`、`contentType`、`byteSize`、`fileKind`、`visibilityScope`、`auditStatus`、`createdAt`。对象存储 key、checksum、business 关联和任何原始下载地址不返回给前端。

### GET `/api/files/{id}/content`

内容返回候选：

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A | 后端鉴权后代理读取对象流并返回 bytes。 | 权限最可控，敏感访问日志最容易保证，前端最简单。 | 后端承担文件流量。M1 文件上限 5 MB，可以接受。 |
| B | 后端鉴权后返回 302 到预签名 URL。 | 后端流量小。 | 敏感文件 URL 生命周期和日志更难解释，测试也更复杂。 |
| C | API 只返回 `{url}`。 | 适合前端控制预览。 | 与当前契约“返回文件内容或临时重定向”相比偏离较大。 |

最终采用方案 A，但认证材料要区分访问者身份：

- 管理员读取 `CAMPUS_AUTH_MATERIAL`：返回原始文件 bytes，并写入 `sensitive_access_logs`。
- 文件拥有者读取自己的 `CAMPUS_AUTH_MATERIAL`：返回脱敏预览 bytes，不返回原始文件。
- 其他用户读取 `CAMPUS_AUTH_MATERIAL`：返回 `404 NOT_FOUND` 或 `403 FORBIDDEN`，实现时优先用 `404` 降低资源枚举风险。

M1 脱敏预览不做 OCR 和智能遮挡，避免引入额外依赖和误遮漏遮风险。建议由后端生成一张安全 PNG 预览图，内容只包含“认证材料已上传”、文件类型、大小、上传时间、审核状态等非敏感信息，不展示原始证件图片像素。后续若要做真实图片遮挡，再引入 OCR 或人工标注式脱敏流程。

敏感访问日志规则：

- 当管理员读取 `CAMPUS_AUTH_MATERIAL` 内容时，必须写入 `sensitive_access_logs`。
- `admin_id` 来自 `CurrentPrincipal.id()`。
- `target_type` 固定为 `CAMPUS_AUTH_MATERIAL`。
- `target_id` 使用 `stored_files.id`。
- `reason` 候选：
  - 默认固定为 `审核认证材料`。
  - 支持 query 参数 `reason`，不传时使用默认值。
- `result` 成功写 `ALLOWED`，对象存储读取失败写 `FAILED`。

最终口径：`target_type = 'CAMPUS_AUTH_MATERIAL'`，`target_id = stored_files.id`，支持可选 `reason`，默认 `审核认证材料`。本人读取脱敏预览不写敏感访问日志；只有管理员读取原始材料时写。

## 校园认证 API 计划

### GET `/api/verifications/me`

权限：登录用户。

无记录时返回：

```json
{
  "status": "NONE",
  "score": 0,
  "factors": []
}
```

有记录时返回契约中的 `CampusVerification`。

### PUT `/api/verifications/me`

权限：登录用户。

职责：创建或更新当前用户的认证资料。

请求字段：

- `realName`
- `studentNo`
- `department`
- `campusEmail`
- `documentType`
- `documentFileIds`

状态推进候选：

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A | `PUT` 只保存草稿或积累状态，`POST /submit` 才进入 `PENDING_REVIEW`。 | 前端流程清晰，用户可保存后再提交。 | 契约中“可信度达到 50 分后进入 PENDING_REVIEW”需要解释为 submit 时进入。 |
| B | `PUT` 只要分数达到 50 就自动进入 `PENDING_REVIEW`。 | 字面贴合契约。 | 用户一填写就进审核，不利于修改，也让 submit 接口价值变低。 |
| C | `PUT` 达到 50 后状态为 `ACCUMULATING`，响应提示可提交。 | 折中，状态表达更细。 | 前端需要理解更多状态。 |

最终采用方案 A。保留显式 submit，让用户和前端更容易理解。

分数规则：

- `NAME_STUDENT_NO`：姓名和学号都非空，计 `40`，状态 `VERIFIED`。
- `DEPARTMENT`：院系非空，计 `10`，状态 `VERIFIED`。
- `CAMPUS_EMAIL`：邮箱后缀匹配配置，计 `10`，状态 `VERIFIED`。M1 不发邮件链接。
- `STUDENT_CARD` 或 `CAMPUS_CARD`：上传材料后先是 `PENDING`，管理员审核通过后才计 `40`。

`PUT` 后主表状态建议：

- 无有效基础信息：`DRAFT`。
- 有基础信息但未提交：`ACCUMULATING`。
- 已被驳回后重新编辑：回到 `ACCUMULATING`，清空 `failure_reason`，因为失败原因属于上一轮审核结论。
- 已经 `APPROVED` 的认证记录：M1 不允许通过 `PUT /api/verifications/me` 直接覆盖核心认证资料，返回 `409 CONFLICT`。这样可以保证已认证成功用户不会被一次新编辑或新驳回降级。

后续如果要支持已认证用户重新提交材料，需要新增“认证变更申请”或“认证 attempt/version”模型，而不是直接改写当前成功认证。

### POST `/api/verifications/me/submit`

权限：登录用户。

规则：

- 当前分数低于 `50` 返回 `409 CONFLICT`。
- 同一因子 24 小时内重提超过 3 次返回 `429 RATE_LIMITED`。
- 提交成功设置 `status = PENDING_REVIEW`。
- 没有证件材料时直接返回 `409 CONFLICT`。
- 当前认证已经 `APPROVED` 时返回 `409 CONFLICT`，M1 不允许已通过认证被原地改成待审。

候选：

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A | 分数达到 50 即可提交，即使证件材料还未上传。 | 贴合契约“提交阈值 50”。 | 管理员审核时可能看到无法完成交易权限的记录。 |
| B | 提交时必须有 `STUDENT_CARD` 或 `CAMPUS_CARD` 待审材料。 | 后台队列质量高，审核通过一定能形成完整权限。 | 比契约更严格，纯邮箱加院系 60 分也不能提交。 |

最终采用方案 B。M1 的主线是“提交材料，管理员审核，通过后能交易”，没有证件材料的审核价值不大。

### GET `/api/admin/verifications`

权限：`CONTENT_ADMIN` 或 `SUPER_ADMIN`。

查询参数：

- `status`
- `page`
- `pageSize`

返回分页 `CampusVerification`。

分页规则：

- `page` 从 1 开始。
- `pageSize` 默认 20，最大 50。
- 排序建议：`updated_at DESC, id DESC`。

### POST `/api/admin/verifications/{id}/approve`

权限：`CONTENT_ADMIN` 或 `SUPER_ADMIN`。

请求：

```json
{
  "reason": "材料清晰，信息一致"
}
```

事务内步骤：

1. 锁定 `campus_auths` 当前记录。
2. 校验状态为 `PENDING_REVIEW`。
3. 将证件因子设置为 `VERIFIED`，`score_value = 40`。
4. 重新计算总分。
5. 设置 `campus_auths.status = APPROVED`。
6. 设置 `reviewed_by_admin_id`、`reviewed_at`、清空 `failure_reason`。
7. 如果满足完整交易权限，授予 `VERIFIED_STUDENT`。
8. 更新认证材料文件 `audit_status = APPROVED`。
9. 写入 `operation_logs`。
10. 返回最新 `CampusVerification`。

完整交易权限规则：

- `score >= 60`
- 至少一项 `STUDENT_CARD` 或 `CAMPUS_CARD` 因子 `VERIFIED`
- `CampusVerificationStatus = APPROVED`
- 用户拥有 `VERIFIED_STUDENT`

注意：步骤 7 授予角色后，步骤 10 返回的认证对象不直接包含角色；前端再调用 `/api/auth/me` 时应看到 `canTrade = true`。

### POST `/api/admin/verifications/{id}/reject`

权限：`CONTENT_ADMIN` 或 `SUPER_ADMIN`。

请求：

```json
{
  "reason": "证件照片不清晰"
}
```

事务内步骤：

1. 锁定 `campus_auths` 当前记录。
2. 校验状态为 `PENDING_REVIEW`，并且该记录不是既有 `APPROVED` 认证的原地降级操作。
3. 设置 `campus_auths.status = REJECTED`。
4. 写入 `failure_reason`、`reviewed_by_admin_id`、`reviewed_at`。
5. 将待审证件因子设置为 `REJECTED`。
6. 更新认证材料文件 `audit_status = REJECTED`。
7. 写入 `operation_logs`。
8. 返回最新 `CampusVerification`。

已确认的驳回语义：

- 如果用户原本没有成功认证，本次审核驳回后保持“未认证成功”，主状态可为 `REJECTED`，不授予 `VERIFIED_STUDENT`。
- 如果用户原本已经认证成功，M1 默认不做原地重审降级；已成功状态和 `VERIFIED_STUDENT` 角色都不动。
- 因此 M1 中 `APPROVED` 记录不允许直接进入新的 `PENDING_REVIEW`，避免一个主认证行同时表达“已成功”和“新材料待审”两个含义。
- 后续若要支持重新认证、认证失效或身份异常处罚，需要新增变更申请、版本记录或处罚流程，再决定是否撤销 `VERIFIED_STUDENT`。

## CurrentUser 真实交易权限计划

需要把 A 的临时逻辑替换为：

- 无 `campus_auths`：`verificationStatus = NONE`，`canTrade = false`。
- 有认证记录：`verificationStatus = campus_auths.status`。
- `canTrade = true` 只有在以下条件全部满足：
  - `campus_auths.score >= 60`
  - `campus_auths.status = APPROVED`
  - 至少一个 `STUDENT_CARD` 或 `CAMPUS_CARD` 因子为 `VERIFIED`
  - 当前用户角色包含 `VERIFIED_STUDENT`

接入候选：

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A | `CurrentUserMapper` 直接注入 `CampusVerificationRepository` 查询。 | 改动最少。 | Mapper 从纯转换组件变成数据库组件。 |
| B | `SessionLookupService` 加载 `CurrentPrincipal` 时附带认证状态。 | 每个请求只加载一次，`/me` 很直接。 | 会扩大 `CurrentPrincipal`，影响平台安全对象边界。 |
| C | 新增 `CampusTradeEligibilityResolver`，`CurrentUserMapper` 调用 resolver。 | 改动小，职责清楚，后续也可给商品模块复用。 | Mapper 仍然从纯转换变成依赖业务 resolver。 |

最终采用方案 C。

建议接口：

```java
public record CampusTradeEligibility(
        String verificationStatus,
        boolean canTrade
) {
}
```

resolver 输入：

- `userId`
- `roles`

resolver 输出：

- `verificationStatus`
- `canTrade`

这样 C 成员的商品发布权限如果需要完整交易规则，也可以复用同一个 resolver，而不是只靠角色判断。

## 关键错误码计划

需要补齐 `ApiExceptions` 工厂方法或直接构造 `ApiException`：

- 文件过大：`PAYLOAD_TOO_LARGE`，HTTP 413。
- 文件类型不支持：`UNSUPPORTED_MEDIA_TYPE`，HTTP 415。
- 未找到或不可见：`NOT_FOUND`，HTTP 404。
- 24 小时限制：`RATE_LIMITED`，HTTP 429。
- 状态冲突：`CONFLICT`，HTTP 409。

候选：

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A | 在业务代码中 `new ApiException(...)`。 | 快速。 | 错误码分散。 |
| B | 扩展 `ApiExceptions`，新增 `notFound`、`payloadTooLarge` 等方法。 | 与现有风格一致，后续模块复用。 | 需要改平台类。 |

最终采用方案 B。

## 操作日志计划

M1 暂用 `operation_logs`。

建议 action：

- `CAMPUS_VERIFICATION_APPROVE`
- `CAMPUS_VERIFICATION_REJECT`
- `FILE_UPLOAD`
- `FILE_VISIBILITY_CHANGE`

审核日志内容：

- `admin_id`
- `target_type = CAMPUS_VERIFICATION`
- `target_id = campus_auths.id`
- `before_json` 保存审核前状态、分数、因子摘要。
- `after_json` 保存审核后状态、分数、因子摘要、reason。
- `ip_address` 从请求中读取。

是否记录普通用户提交认证：

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A | 只记录管理员审核。 | 符合 operation_logs 的管理员操作定位。 | 用户提交历史只能从认证表 updated_at 看。 |
| B | 用户提交也写 operation_logs，`admin_id` 为空。 | 追踪更完整。 | operation_logs 语义被扩展。 |

最终采用方案 A。用户提交次数由因子表字段承担，管理员操作才进 operation_logs。

## 实施步骤

- [x] 第 1 步：核对已确认决策，锁定实现口径。
- [x] 第 2 步：新增 `V6__campus_auth_schema.sql`，创建认证表和索引。
- [x] 第 3 步：新增 `V7__file_verification_indexes.sql`，补文件索引和认证材料 `ADMIN_ONLY` 强约束。
- [x] 第 4 步：新增 `V8__campus_auth_configs.sql`，补邮箱后缀、材料数量、大小、限流配置，并补 `student_demo` 已认证种子。
- [x] 第 5 步：实现平台对象存储接口和 MinIO 适配。
- [x] 第 6 步：实现文件 Repository、Service、Controller，包括本人脱敏预览和管理员原件访问留痕。
- [x] 第 7 步：实现校园认证 Repository、分数计算器、Service、Controller。
- [x] 第 8 步：实现管理员认证审核 API 和操作日志。
- [x] 第 9 步：接入 `CurrentUserMapper` 的真实认证状态与交易权限。
- [x] 第 10 步：补测试。
- [x] 第 11 步：补 README、migration README 和本过程记录。
- [x] 第 12 步：运行 `mvn test`，必要时再跑完整 `./scripts/check.sh`。

## 测试计划

后端单元测试建议：

- 文件上传：
  - 未登录上传返回 `401 AUTH_REQUIRED`。
  - 非图片返回 `415 UNSUPPORTED_MEDIA_TYPE`。
  - 超过 5 MB 返回 `413 PAYLOAD_TOO_LARGE`。
  - `CAMPUS_AUTH_MATERIAL` 强制保存为 `ADMIN_ONLY`。
- 文件访问：
  - `PUBLIC` 文件游客可读。
  - `PRIVATE` 文件非拥有者不可读。
  - `ADMIN_ONLY` 原始文件普通用户不可读。
  - 认证材料本人可读元数据和脱敏预览，不可读原件。
  - 管理员读取认证材料原件会写 `sensitive_access_logs`。
- 认证提交：
  - 无记录时 `/api/verifications/me` 返回 `NONE`。
  - 姓名学号计 40 分。
  - 院系计 10 分。
  - 匹配校园邮箱后缀计 10 分。
  - 无证件材料提交返回 `409 CONFLICT`。
  - 分数不足 50 提交返回 `409 CONFLICT`。
  - 已 `APPROVED` 认证再次提交返回 `409 CONFLICT`。
- 管理员审核：
  - 非管理员访问 `/api/admin/verifications` 返回 `403 FORBIDDEN`。
  - 通过后状态为 `APPROVED`，证件因子为 `VERIFIED`，用户获得 `VERIFIED_STUDENT`。
  - 非成功认证被驳回后状态为 `REJECTED`，记录 `failureReason`。
  - 既有成功认证不会被驳回操作原地降级。
  - 审核操作写入 `operation_logs`。
- 当前用户：
  - 无认证记录时 `verificationStatus = NONE`，`canTrade = false`。
  - 认证通过但缺少角色时 `canTrade = false`。
  - 认证通过、分数足够、有证件因子、拥有角色时 `canTrade = true`。

验收命令：

```bash
cd backend
mvn test
```

完整联调前再运行：

```bash
./scripts/check.sh
```

## 已拍板决策表

以下问题已由用户确认，后续执行不再反复选择：

| 编号 | 决策点 | 确认结果 |
| --- | --- | --- |
| D1 | 对象存储客户端用 MinIO SDK、AWS S3 SDK，还是临时本地存储。 | MinIO SDK。 |
| D2 | 认证材料 `documentFileIds` 是否新增 `campus_auth_factor_files` 关联表。 | 新增关联表。 |
| D3 | `PUT /api/verifications/me` 是否自动入审核队列。 | 不自动，`POST /submit` 才入队。 |
| D4 | 提交审核是否必须有证件材料。 | 必须有。 |
| D5 | 校园邮箱后缀来源。 | `system_configs` 中的 JSON 配置。 |
| D6 | 文件内容返回方式。 | 后端代理 bytes。 |
| D7 | 认证材料本人能否读取内容。 | 本人只能看元数据和脱敏预览，管理员才能读原件并留痕。 |
| D8 | 驳回认证是否影响既有认证成功状态。 | 不原地降级；原来成功仍成功，原来未成功仍未成功。 |
| D9 | 头像默认可见性。 | `PUBLIC`。 |
| D10 | 包结构。 | `platform.storage`、`files`、`identity.verification`。 |
| D11 | MIME 校验。 | `Content-Type` + 图片魔数校验。 |
| D12 | 认证材料数据库约束。 | 数据库强制 `CAMPUS_AUTH_MATERIAL => ADMIN_ONLY`。 |
| D13 | 敏感日志 targetType。 | `CAMPUS_AUTH_MATERIAL`。 |
| D14 | 普通用户提交认证是否写操作日志。 | 不写，管理员审核才写。 |

## 最终默认方案

后续执行按以下组合推进：

- MinIO Java SDK。
- `ObjectStorageClient` 接口隔离具体存储。
- 后端代理返回文件 bytes。
- 图片格式使用最小魔数校验。
- 包结构采用 `platform.storage`、`files`、`identity.verification` 三段。
- `V6` 增加 `campus_auth_factor_files` 支持多张认证材料。
- `PUT` 保存草稿或积累状态，`POST /submit` 才进入 `PENDING_REVIEW`。
- 提交审核必须已有证件材料。
- 校园邮箱后缀放在 `system_configs`，M1 默认 `example.edu`。
- 本人查看认证材料只返回元数据和后端生成的脱敏预览 PNG，不返回原始证件图片。
- 管理员审核写 `operation_logs`。
- 管理员读取认证材料内容写 `sensitive_access_logs`。
- 驳回不原地降级既有成功认证；M1 禁止 `APPROVED` 记录直接重新进入待审。
- `CurrentUserMapper` 通过 `CampusTradeEligibilityResolver` 返回真实认证状态和交易权限。

这套方案的特点是：M1 能跑通真实链路，同时给后续商品、私信、举报复用文件服务留下清楚边界。
