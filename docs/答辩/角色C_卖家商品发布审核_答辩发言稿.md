# 卖家 / 商品发布审核 —— 2分钟答辩发言稿（角色 C：珂珂）

> 版本演进：V9～V11 → V19～V21  
> 涉及文件：`V9__goods_catalog_schema.sql`，`V10__goods_audit_rules_schema.sql`，`V11__goods_search_seed_data.sql`，`V19__n4_intelligence_assist.sql`，`V20__n4_ai_review_notifications.sql`，`V21__seed_demo_buyer_seller.sql`

---

各位老师好，我是角色 C 珂珂，负责模块是**卖家端商品发布与内容审核**。接下来我用约两分钟时间，从版本演进、架构设计、数据库触发器亮点、以及风控治理闭环四个维度拆解我的核心工作。

---

## 一、版本演进

整个卖家侧能力分两波交付。第一波是 **V9～V11**，在 V9 落地商品主表 `goods`、图片表 `goods_images` 和标签关联表 `goods_tags`，同时在 V10 建立审核规则体系——`audit_records`、`rule_hit_records` 和 `forbidden_terms` 三张表，构成自动化命中与人工审核双轨。V11 补齐全文搜索索引 GIN + pg_trgm，并注入种子数据用于前端联调。第二波是 **V19～V21**：V19 引入大模型发布辅助能力，V20 扩展通知体系接入 AI 审核提醒，V21 补全 buyer/seller 演示账号实现买卖闭环可跑通。

---

## 二、核心架构：为什么将 goods / goods_images / goods_tags 解耦

商品主表 `goods`（见 V9 第 1-26 行）仅存储标题、描述、成色、价格等核心业务属性，**图片和标签不冗余在主表上**。拆出 `goods_images`（V9 第 28-37 行）后，每件商品支持多图排序、主图唯一约束——通过 `uq_goods_images_primary` 部分唯一索引（第 39-41 行）保证每个 goods 仅一条 `is_primary=TRUE` 记录。拆出 `goods_tags`（第 46-50 行）则实现标签的 M:N 关联，后续可按标签聚类推荐而不污染主表 schema。

**状态机双维度协同控制公网可见性**是这块的核心设计。`goods` 同时拥有 `status`（商品状态）和 `audit_status`（审核状态）两个字段——第 12-13 行定义，第 21-22 行通过 CHECK 约束强制枚举：

- `status`：DRAFT → PENDING_REVIEW → ON_SALE / RESERVED / SOLD / OFF_SHELF / DELETED  
- `audit_status`：NOT_SUBMITTED → PENDING → APPROVED / REJECTED

公网列表查询在 V11 第 1-2 行的 `idx_goods_public_list` 索引上复合过滤 `(status, audit_status, is_deleted)`，只有 `status='ON_SALE' AND audit_status='APPROVED' AND is_deleted=FALSE` 的商品才暴露给浏览端。这意味着哪怕卖家点了发布，只要审核未通过，商品对外不可见——维度分离让业务状态和合规状态互不干扰，也让管理员可以单独对合规侧做批量操作。

---

## 三、技术亮点：search_vector 由数据库触发器自动维护

商品全文搜索我们没有引入 Elasticsearch 等额外中间件，而是基于 PostgreSQL 原生 `tsvector` 实现。关键在于 V9 第 52-66 行的 `refresh_goods_search_vector()` 触发器函数与 `trg_goods_refresh_search_vector` 触发器：

```sql
BEFORE INSERT OR UPDATE OF title, description
ON goods
FOR EACH ROW
EXECUTE FUNCTION refresh_goods_search_vector();
```

每当 `title` 或 `description` 发生 INSERT/UPDATE，触发器自动将两字段拼接后生成 `to_tsvector('simple', ...)` 写入 `search_vector` 列。应用层完全不用关心向量同步——**没有 Java 代码显式维护这个字段**，避免了"代码漏更新导致搜索不一致"的经典 bug。配合 V11 第 16-17 行的 GIN 索引和 pg_trgm 模糊匹配索引（第 19-22 行），搜索请求直接走数据库索引覆盖，延迟可控且无外部依赖，这对校园这种低运维预算场景非常友好。

---

## 四、风控治理闭环：自动化命中 + 人工审核 + AI 前置辅助

V10 落地了三表闭环（见 V10 第 1-51 行）：

- **`forbidden_terms`**（第 36-48 行）：管理员可动态维护敏感词库，支持 KEYWORD 和 CATEGORY 两类，severity 从 LOW 到 BLOCK 四级，`enabled` 开关可即时生效无需重启。
- **`rule_hit_records`**（第 19-31 行）：当用户提交商品时，后端对标题和描述做关键词匹配，每条命中记录持久化 `rule_code`、`severity` 和 `decision_hint`（ALLOW/REVIEW/REJECT），**形成可审计的自动化裁决轨迹**。
- **`audit_records`**（第 1-11 行）：管理员逐条审核，记录 `result` 和 `reason`，与 `rule_hit_records` 配合形成"机审初筛 → 人审定论"的双保险。

在此基础上，V19 引入 `ai_assist_records` 表（V19 第 1-19 行）。卖家填写原始标题和描述后，后端调用 DeepSeek 大模型做三件事：①优化标题与描述使其更规范；②推荐分类与标签；③做内容风险扫描返回 `risk_level` 和 `risk_reasons_json`。这里有一个关键设计约束——第 15 行的 `audit_reminder` 字段硬编码为"AI 仅提供辅助建议，不会自动审核、下架或处罚"，且第 25 行通过 `system_configs` 限制每用户每日调用 5 次。**AI 始终是辅助角色，不替代审核员决策。**

V20 进一步在 `notifications` 表新增 `AI_REVIEW_REMINDER` 消息类型（V20 第 17 行），当内容命中 HIGH 或 BLOCK 级风险时，主动推送通知提醒卖家自查修改，形成"发布 → AI 扫描 → 合规提醒 → 人工审核 → 通知反馈"的完整闭环。

---

**总结**：我的模块以 `goods` 表为中心，通过图片、标签解耦保证扩展性，通过 `status` + `audit_status` 双状态机精确控制可见性，通过数据库触发器零成本维护全文搜索向量，再通过 forbidden_terms → rule_hit_records → audit_records 三表闭环与 V19/V20 AI 辅助能力，构建了一套"发布前 AI 辅助优化 + 提交后规则自动初筛 + 人工兜底审核 + 即时合规提醒反馈"的全链路内容治理体系。以上就是我的答辩，请各位老师指正。