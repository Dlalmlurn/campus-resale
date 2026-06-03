# M1 C 成员商品目录实施计划

记录日期：2026-05-31

## 分支建议

建议分支名：`feature/member-goods-catalog`

采用这个名字的原因：

- 与 `短期并行推进计划.md` 和 `docs/阶段契约/m1_contracts.md` 中 C 成员建议分支保持一致。
- 表达范围足够明确：商品、分类、审核、搜索都属于 goods catalog 主线。
- 不绑定具体人名，后续如果需要多人接手 C 模块，分支语义仍然清楚。

当前实际构建分支：`feature/zike-goods-catalog`。

## 阅读结论

本计划基于以下文档和当前 `main` 分支代码：

- `docs/阶段契约/m1_contracts.md`
- `docs/02_domain_contracts.md`
- `docs/05_database_design.md`
- `短期并行推进计划.md`
- `docs/项目进程记录/M1_A成员身份会话实现记录.md`
- `docs/项目进程记录/M1_B成员文件校园认证实施计划.md`
- `backend/src/main/resources/db/migration/V1__foundation_schema.sql` 到 `V8__campus_auth_configs.sql`
- `backend/src/main/java/com/campusresale/identity`
- `backend/src/main/java/com/campusresale/files`
- `backend/src/main/java/com/campusresale/platform`

C 成员负责 M1 后半段闭环：

- 商品草稿、编辑、提交审核。
- 商品图片关联和审核通过后的公开读取。
- 管理员商品审核。
- 商品公开列表、详情和关键词搜索。
- 分类、标签、校园地点的读取接口与商品种子数据。

当前仓库已有的基础：

- A 成员已经提供 Cookie session、`@RequireLogin`、`@RequireRole`、统一错误响应和演示账号。
- B 成员已经提供文件上传、`GOODS_IMAGE` 文件元数据、文件业务绑定、认证学生交易权限 `canTrade` 推导。
- `categories`、`tags`、`campus_places` 已在 `V1` 创建，并在 `V2` 种入基础数据。
- `goods`、`goods_images`、`goods_tags`、`audit_records`、`rule_hit_records`、`forbidden_terms` 尚未创建。
- `backend/src/main/java/com/campusresale/goods` 目前只有包说明，适合按 C 模块边界新增实现。

## 已确认决策

本节记录 2026-05-31 已拍板口径，后续实现按这里执行。

| 编号 | 决策结果 |
| --- | --- |
| C-D1 | 商品发布和提交审核同时要求用户拥有 `VERIFIED_STUDENT` 角色，并且按 B 的完整交易资格解析结果满足 `canTrade = true`。 |
| C-D2 | 商品审核驳回后设置 `status = DRAFT`, `auditStatus = REJECTED`，允许卖家修改后重新提交。 |
| C-D3 | 商品审核通过时将图片公开，但公开和收回必须由商品状态流转统一联动，不能依赖人工记忆。`ON_SALE` / `APPROVED` 时图片可公开；进入 `DRAFT`, `PENDING_REVIEW`, `OFF_SHELF`, `DELETED` 或驳回状态时必须自动收回公开读取能力。 |
| C-D4 | 搜索使用 PostgreSQL `to_tsvector` + GIN，同时保留 `ILIKE` / `pg_trgm` 兜底。 |
| C-D5 | M1 建表并实现最小禁售关键词扫描，命中硬规则时拒绝提交或写入规则命中记录。 |
| C-D6 | 商品演示数据采用“真实接口链路为主 + 少量带可读占位图的种子数据”。现场仍保留从上传、创建、审核到公开的真实演示路径；同时准备少量可直接浏览的上架商品，避免演示冷启动空列表。 |
| C-D7 | Repository 沿用当前项目 `JdbcTemplate` / `NamedParameterJdbcTemplate` 风格。 |

## C 成员交付边界

### 必须交付

- `GET /api/categories`
- `GET /api/tags`
- `GET /api/campus-places`
- `POST /api/goods/drafts`
- `PATCH /api/goods/{id}`
- `POST /api/goods/{id}/submit`
- `GET /api/goods/mine`
- `GET /api/goods`
- `GET /api/goods/{id}`
- `GET /api/admin/goods`
- `POST /api/admin/goods/{id}/approve`
- `POST /api/admin/goods/{id}/reject`
- `V9__goods_catalog_schema.sql`
- `V10__goods_audit_rules_schema.sql`
- `V11__goods_search_seed_data.sql`
- 少量可读占位图商品种子数据，具体对象存储写入方式在构建时按本地工程约束落地。
- 商品模块基础单元测试和 repository/service 状态流转测试。
- 商品接口遵守 `docs/阶段契约/m1_contracts.md` 的响应结构、分页结构和错误码。

### 暂不交付

- AI 发布辅助真实调用。
- 复杂推荐、收藏、关注和公开留言。
- 订单占用、支付、结算和评价。
- 禁售规则完整后台配置页面。
- 商品上下架后的通知系统。
- Elasticsearch 或外部搜索服务。
- 商品图片裁剪、压缩、水印和 CDN。

## 与 A/B 成员实现的衔接点

A 成员已经提供：

- `CurrentPrincipal`：当前登录用户 id、用户名、昵称、角色、session 信息。
- `@RequireLogin`：保护卖家本人接口。
- `@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})`：保护管理员接口。
- `CurrentUser.canTrade`：B 合并后按完整校园认证规则推导交易权限。

B 成员已经提供：

- `FileRepository.findAllByIds(...)`：校验商品图片文件是否存在。
- `FileRepository.attachToBusiness(...)`：把上传文件绑定到业务对象。
- `FileRepository.updateAuditStatus(...)`：审核通过或驳回后同步图片审核状态。
- `StoredFileRecord`：可检查 `fileKind`、`visibilityScope`、`ownerUserId` 和 `auditStatus`。

C 成员实现时应尽量不改 A/B 的核心逻辑：

- 不改 session、Cookie、CSRF、角色注解和登录态过滤链。
- 不改校园认证分数和 `canTrade` 规则，只消费该结果或角色。
- 不重复实现文件上传，只校验 `GOODS_IMAGE` 文件归属并建立商品图片关联。
- 如果需要文件公开读取规则配合商品上架，优先在 `FileService` 中增加“公开商品图片可读”判断，避免绕过文件服务。

## 包结构建议

建议全部放在 `backend/src/main/java/com/campusresale/goods` 下，按当前项目轻量分层风格组织：

- `GoodsController`
- `AdminGoodsController`
- `CatalogController`
- `GoodsService`
- `GoodsRepository`
- `CatalogRepository`
- `GoodsRecord`
- `GoodsSummary`
- `GoodsRequests`
- `CategorySummary`
- `TagSummary`
- `CampusPlaceSummary`
- `ConditionLevel`
- `GoodsStatus`
- `GoodsAuditStatus`
- `AuditResult`

理由：

- 项目当前 A/B 模块以模块包内 Controller、Service、Repository、DTO/record 为主，没有复杂多层目录。
- C 模块当前业务集中在商品和目录查询，先保持低层级便于审查。
- 后续 N1 引入订单、商品占用和模拟支付时，商品模块边界仍然清楚。

## Migration 计划

### V9：商品主表与关联表

创建 `goods`：

- `id`
- `seller_id`
- `category_id`
- `title`
- `description`
- `condition_level`
- `list_price`
- `trade_place_id`
- `trade_place_detail`
- `available_time_text`
- `status`
- `audit_status`
- `current_occupied_order_id`
- `search_vector`
- `is_deleted`
- `published_at`
- `created_at`
- `updated_at`

创建 `goods_images`：

- `id`
- `goods_id`
- `file_id`
- `sort_order`
- `is_primary`
- `created_at`

创建 `goods_tags`：

- `goods_id`
- `tag_id`

建议约束：

- `condition_level` 只能为 `NEW`, `LIKE_NEW`, `LIGHTLY_USED`, `NOTICEABLY_USED`。
- `status` 只能为 `DRAFT`, `PENDING_REVIEW`, `ON_SALE`, `RESERVED`, `SOLD`, `OFF_SHELF`, `DELETED`。
- `audit_status` 只能为 `NOT_SUBMITTED`, `PENDING`, `APPROVED`, `REJECTED`。
- `list_price >= 0.01`。
- `title` 非空，长度由应用层和数据库共同限制。
- `goods_images(goods_id, file_id)` 唯一。
- 每个商品只能有一张主图，建议用部分唯一索引约束 `is_primary = TRUE`。

### V10：审核和规则表

创建 `audit_records`：

- `id`
- `target_type`
- `target_id`
- `admin_id`
- `result`
- `reason`
- `rule_summary`
- `created_at`

创建 `rule_hit_records`：

- `id`
- `target_type`
- `target_id`
- `rule_type`
- `rule_code`
- `matched_text_hash`
- `severity`
- `decision_hint`
- `created_at`

创建 `forbidden_terms`：

- `id`
- `term`
- `term_type`
- `severity`
- `enabled`
- `created_by_admin_id`
- `created_at`
- `updated_at`

M1 商品审核通过、驳回必须写 `audit_records`。禁售词和规则命中可以先只建表并预留最小扫描逻辑。

### V11：搜索索引与种子补充

建议内容：

- 为 `goods(status, audit_status, is_deleted, published_at DESC)` 建公开列表索引。
- 为 `goods(seller_id, status, audit_status, created_at DESC)` 建卖家商品列表索引。
- 为 `goods(category_id, status, audit_status, is_deleted)` 建分类筛选索引。
- 为 `goods(list_price)` 建价格筛选索引。
- 为 `goods(search_vector)` 建 GIN 索引。
- 为 `goods.title`、`goods.description` 建 `pg_trgm` GIN 索引。
- 补充分类、标签或禁售词种子数据，使用 `ON CONFLICT` 保持幂等。

V11 已按 `C-D6` 决策补少量演示商品种子。为了避免只有数据库元数据而图片不可读，种子商品图片使用 `seed/goods-placeholder/` 前缀，并由文件服务为这些占位图生成稳定 PNG 预览；真实用户上传的商品图仍走 MinIO 对象存储。

### V12：商品修正迁移预留

暂不主动占用。只有 C 实现或联调过程中发现已合并 migration 需要修正时，再新增 `V12`。

## API 实施口径

### 目录查询

`GET /api/categories`、`GET /api/tags`、`GET /api/campus-places` 都可以游客访问，只返回启用数据。

排序建议：

- 分类：`sort_order ASC, id ASC`
- 标签：`id ASC`
- 校园地点：`sort_order ASC, id ASC`

### 商品创建与编辑

`POST /api/goods/drafts` 权限建议采用 `@RequireLogin` 加业务层 `canTrade` 或 `VERIFIED_STUDENT` 校验。

创建时校验：

- 标题 2 到 80 字符。
- 描述 10 到 2000 字符。
- 图片 1 到 15 张。
- 图片必须存在、属于当前用户、`fileKind = GOODS_IMAGE`。
- 分类存在且启用，且不是禁售分类。
- 标签存在且启用。
- 校园地点存在且启用。
- 价格使用 `BigDecimal`，小数位不超过 2 位。

创建后状态：

- `status = DRAFT`
- `auditStatus = NOT_SUBMITTED`

`PATCH /api/goods/{id}` 只允许卖家修改自己的 `DRAFT` 或 `auditStatus = REJECTED` 商品。M1 默认不允许直接修改 `PENDING_REVIEW` 和 `ON_SALE` 商品核心字段。

### 商品提交审核

`POST /api/goods/{id}/submit` 只允许卖家本人提交。

提交前校验：

- 当前用户仍具备完整交易权限。
- 商品状态允许提交。
- 至少 1 张图片，最多 15 张。
- 商品核心字段完整。
- 没有命中硬性禁售分类或禁售词。

提交后状态：

- `status = PENDING_REVIEW`
- `auditStatus = PENDING`

### 商品审核

管理员审核接口使用 `@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})`。

审核通过：

- 只允许审核 `status = PENDING_REVIEW` 且 `auditStatus = PENDING` 的商品。
- 设置 `status = ON_SALE`。
- 设置 `auditStatus = APPROVED`。
- 设置 `publishedAt = now()`。
- 商品图片审核状态更新为 `APPROVED`，并通过商品状态联动机制开放公开读取。
- 写入 `audit_records`。

审核驳回：

- 设置 `status = DRAFT`。
- 设置 `auditStatus = REJECTED`。
- 商品图片公开读取能力必须通过商品状态联动机制收回；M1 可保持图片审核状态为 `PENDING`，便于卖家修改后重新提交。
- 写入 `audit_records`。

商品图片公开状态必须封装为领域服务动作，例如 `syncGoodsImageVisibility(goodsId)` 或等价方法，由商品审核通过、驳回、下架、删除和重新提交等状态流转统一调用。实现和测试都应断言“不存在需要人工记忆的单独收回步骤”。

### 商品公开列表与详情

公开列表只返回：

- `status = ON_SALE`
- `auditStatus = APPROVED`
- `is_deleted = false`

公开详情对游客开放，但只开放公开在售商品。卖家本人可以查看自己的草稿、审核中和驳回商品；管理员可以查看审核职责范围内商品。

搜索参数按契约支持：

- `keyword`
- `categoryId`
- `minPrice`
- `maxPrice`
- `conditionLevel`
- `placeId`
- `sort`
- `page`
- `pageSize`

分页统一返回 `PageResponse` 结构：`items`, `page`, `pageSize`, `total`。

## 技术决策候选记录

以下保留决策候选和批准结果，便于后续复盘。最终执行口径以“已确认决策”为准。

### C-D1：商品交易权限校验方式

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A | 只校验 `VERIFIED_STUDENT` 角色。 | 实现简单，与权限注解天然匹配。 | 如果角色和认证状态短暂不一致，可能放过异常账号。 |
| B | 调用 B 的 `CampusTradeEligibilityResolver` 校验完整 `canTrade`。 | 与 `/api/auth/me` 的交易权限口径一致。 | C 模块需要依赖 identity verification 组件。 |
| C | 同时要求 `VERIFIED_STUDENT` 角色和 `canTrade = true`。 | 最稳，角色和认证事实双保险。 | 依赖稍多，测试要覆盖更多组合。 |

批准：方案 C。

### C-D2：审核驳回后的商品状态

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A | `status = DRAFT`, `auditStatus = REJECTED`。 | 契约推荐，卖家可直接修改后重提。 | 需要前端区分“草稿”和“被驳回草稿”。 |
| B | `status = OFF_SHELF`, `auditStatus = REJECTED`。 | 语义上更像被平台拿下。 | 新发布审核驳回不一定已经上架，OFF_SHELF 语义偏重。 |

批准：方案 A。

### C-D3：商品图片公开方式

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A | 审核通过时把图片 `visibility_scope` 改为 `PUBLIC`，并把收回动作纳入商品状态流转。 | 文件服务现有公开读取规则可直接复用，且下架、删除、驳回不会遗留公开图片。 | 需要为状态流转补集中同步方法和测试。 |
| B | 图片仍保持 `PRIVATE`，文件读取时根据商品 `ON_SALE` 动态放行。 | 更符合“商品公开才公开图片”的业务语义。 | 文件服务要反向查商品，模块耦合增加。 |
| C | 新增 `business_type = GOODS` 读取策略，`PUBLIC` 与商品状态共同校验。 | 长期语义最清楚。 | M1 实现成本最高。 |

批准：方案 A，但必须实现状态联动收回，不能依赖人工记忆。

### C-D4：搜索实现

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A | M1 先用 `ILIKE`，V11 预留搜索索引。 | 最快，容易测。 | 不完全达到正式文档对全文检索的目标。 |
| B | 使用 PostgreSQL `to_tsvector` + GIN，同时保留 `ILIKE` 兜底。 | 契合契约和数据库设计。 | 中文分词效果有限，需要接受 M1 只是基础搜索。 |
| C | 引入 Elasticsearch 或外部搜索。 | 搜索能力强。 | 明显超出 M1。 |

批准：方案 B。

### C-D5：禁售规则 M1 范围

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A | 只建表，商品提交审核时不扫描。 | 最省时间。 | 禁售规则链路不可演示。 |
| B | 建表并实现最小关键词扫描，命中硬规则则拒绝提交或进入规则命中记录。 | 能体现审核前置和数据库设计价值。 | 需要定义默认禁售词和错误提示。 |
| C | 实现完整规则后台维护。 | 功能完整。 | 超出 M1。 |

批准：方案 B。

### C-D6：演示商品种子数据

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A | 不种商品，由接口现场创建、审核。 | 数据真实，避免无对象图片。 | 演示准备步骤较多。 |
| B | 只种无图或占位图元数据商品。 | 列表马上有数据。 | 不满足商品图片 1-15 张，也可能图片内容不可读。 |
| C | 在集成迁移或脚本中种商品、文件元数据和真实对象。 | 演示最顺滑。 | 需要对象存储种子机制，可能放到 `V14` 更合适。 |
| D | 真实接口链路为主，同时补少量带可读占位图的上架商品种子。 | 既能演示完整创建审核链路，又避免公开列表冷启动为空。 | 需要在构建时处理占位图对象写入，避免只有数据库元数据。 |

批准：方案 D。

### C-D7：Repository 技术风格

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A | 沿用当前项目 `JdbcTemplate` / `NamedParameterJdbcTemplate`。 | 与 A/B 一致，SQL 可控。 | 手写 SQL 较多。 |
| B | 引入 Spring Data JPA。 | Repository 代码少。 | 与当前代码风格不一致，引入额外映射复杂度。 |
| C | 引入 MyBatis。 | SQL 和映射分离。 | 新增技术栈，不适合 M1。 |

批准：方案 A。

## 验收建议

后端单元测试建议覆盖：

- 认证学生可以创建商品草稿。
- 未登录用户不能创建商品。
- 未认证用户不能创建商品。
- 商品图片必须属于当前用户且为 `GOODS_IMAGE`。
- 卖家只能修改自己的草稿或驳回商品。
- 提交审核后状态变为 `PENDING_REVIEW` / `PENDING`。
- 管理员审核通过后商品变为 `ON_SALE` / `APPROVED`，并写入 `audit_records`。
- 管理员驳回后商品变为 `DRAFT` / `REJECTED`。
- 公开列表只返回已审核上架商品。
- 关键词、分类、价格、成色、地点筛选可用。
- 非管理员不能访问 `/api/admin/goods`。

手工联调建议：

```powershell
./scripts/check.sh
```

空库迁移建议验证：

```powershell
docker compose down -v
docker compose up --build -d
```

接口链路建议演示：

1. `student_demo` 登录。
2. 上传 `GOODS_IMAGE`。
3. 创建商品草稿。
4. 提交商品审核。
5. `content_admin` 登录。
6. 管理员审核商品通过。
7. 游客访问公开商品列表、搜索和详情。

## 后续交接说明

本记录已完成 C 成员实现方案和 `C-D1` 到 `C-D7` 决策确认。当前实现已在 `feature/zike-goods-catalog` 开始落地：包含 `V9-V11` migration、商品目录 API、商品审核 API、搜索、最小禁售词扫描、状态联动图片可见性同步和基础测试。
