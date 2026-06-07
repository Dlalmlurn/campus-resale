# 数据库设计

本文件定义校园二手交易系统的最新版数据库设计口径。它承接 `00_project_baseline.md`、`01_project_shape.md`、`02_domain_contracts.md` 和 `04_final_quality_criteria.md`，并参考 `../历史资料/source_materials/` 中早期实体清单与 SQL 草稿。

若本文件与 `../历史资料/source_materials/` 冲突，以本文件和编号正式文档为准。早期草稿中的单一信用分、旧订单状态、直接保存图片 URL、简单订单完成即结算等口径不再采用。

## 设计原则

- 数据库采用 PostgreSQL，保存业务事实、状态历史、审计事实、搜索索引支撑数据和统计视图。
- 表名和字段名采用 `snake_case`；主键统一为 `id BIGINT`，使用 identity 或等价自增策略。
- 业务金额使用 `NUMERIC(12,2)`，不使用浮点数。
- 时间字段统一使用 `TIMESTAMPTZ`，关键事实记录保留 `created_at`，可变对象保留 `updated_at`。
- 业务状态使用字符串枚举或字典表映射，数据库必须具备约束或应用层集中校验，不能使用无含义裸数字散落在代码里。
- 商品、订单、支付、结算、退款、评价、信用、举报、申诉、处罚、审核、管理员操作和敏感访问等事实记录长期保留，核心事实不做物理删除。
- 文件二进制不进入业务数据库；数据库只保存文件元数据、对象存储键、归属对象和可见范围。
- 允许用 JSONB 保存不可变快照、卡片载荷和 AI 输出摘要，但跨表查询、权限校验、金额计算和状态推进依赖的字段必须结构化。

## 表分组

| 分组 | 表 | 说明 |
| --- | --- | --- |
| 身份权限 | `users`, `roles`, `user_roles`, `user_sessions`, `campus_auths`, `campus_auth_factors` | 用户主体、角色、服务端会话、校园认证和认证因子。 |
| 文件材料 | `stored_files` | 商品图、私信图、认证材料、举报证据、申诉材料和支付异常材料的文件元数据。 |
| 商品发布 | `categories`, `tags`, `goods`, `goods_images`, `goods_tags`, `audit_records`, `rule_hit_records`, `forbidden_terms` | 商品、分类标签、审核和禁售规则命中。 |
| 发现互动 | `campus_places`, `favorites`, `follows`, `goods_comments`, `browse_records`, `search_records`, `recommendation_results` | 校园地点、收藏关注、留言、浏览搜索和推荐结果。 |
| 会话协商 | `conversations`, `messages`, `message_attachments`, `system_message_cards`, `message_read_states` | 商品相关会话、消息、附件、系统卡片和已读状态。 |
| 订单面交 | `trade_orders`, `order_state_records`, `completion_confirmation_requests` | 下单占用、冻结金额、面交信息、完成确认和订单状态历史。 |
| 支付资金 | `payment_orders`, `payment_transactions`, `payment_callback_logs`, `payout_accounts`, `settlement_records`, `settlement_attempts`, `refund_orders` | 支付单、渠道流水、回调、收款方式、结算记录、结算尝试和退款。 |
| 评价信用 | `reviews`, `review_tag_options`, `review_tags`, `credit_records`, `credit_summaries` | 双方评价、评价标签、信用变化记录和信用摘要。 |
| 治理责任 | `reports`, `report_evidence_files`, `appeals`, `appeal_evidence_files`, `dispute_responsibility_records`, `penalty_records` | 举报、申诉、责任认定和处罚。 |
| 通知审计统计 | `notifications`, `announcements`, `operation_logs`, `sensitive_access_logs`, `system_configs` | 站内通知、公告、操作日志、敏感访问日志和系统配置。 |
| 智能辅助 | `intelligence_tasks`, `intelligence_results` | AI 或规则服务任务、输出结果、理由、置信度、预检标签和采纳状态。 |

后台统计以 PostgreSQL 视图、物化视图或定时刷新表实现，不反向修改业务事实。统计视图建议命名为 `analytics_*`。

## 状态枚举

以下为稳定语义，最终可以用 PostgreSQL enum、字典表或 `TEXT CHECK` 实现。

| 对象 | 状态 |
| --- | --- |
| `campus_auths` | `DRAFT`, `ACCUMULATING`, `PENDING_REVIEW`, `APPROVED`, `REJECTED`, `INVALID` |
| `campus_auth_factors` | `PENDING`, `VERIFIED`, `REJECTED`, `EXPIRED` |
| `goods` | `DRAFT`, `PENDING_REVIEW`, `ON_SALE`, `RESERVED`, `SOLD`, `OFF_SHELF`, `DELETED` |
| `conversations` | `NORMAL`, `ARCHIVED`, `BLOCKED` |
| `messages` | `SENT`, `READ`, `RECALLED` |
| `trade_orders` | `PENDING_SELLER_CONFIRM`, `PENDING_PAYMENT`, `PAID_PENDING_MEETUP`, `COMPLETED_PENDING_SETTLEMENT`, `COMPLETED`, `CANCELLED`, `CLOSED`, `DISPUTE_PROCESSING`, `REFUND_PROCESSING` |
| `payment_orders` | `PENDING`, `PROCESSING`, `ESCROWED`, `FAILED`, `CLOSED` |
| `settlement_records` | `PENDING`, `PROCESSING`, `SETTLED`, `FAILED`, `CLOSED` |
| `settlement_attempts` | `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED` |
| `refund_orders` | `PENDING`, `PROCESSING`, `REFUNDED`, `FAILED`, `CLOSED` |
| `reviews` | `SUBMITTED`, `VISIBLE`, `HIDDEN`, `EXCLUDED` |
| `reports` | `PENDING`, `PROCESSING`, `UPHELD`, `REJECTED`, `CLOSED` |
| `appeals` | `PENDING_REVIEW`, `APPROVED`, `REJECTED`, `CLOSED` |
| `intelligence_tasks` | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, `CANCELLED` |
| `intelligence_results` | `GENERATED`, `ADOPTED`, `IGNORED`, `EXPIRED` |

订单不引入 `TRADING` 或“交易中”状态。面交出发、到达、开始等过程只进入会话卡片、通知或 `order_state_records` 事件。

## 核心表设计

### 身份与认证

| 表 | 关键字段 | 约束与说明 |
| --- | --- | --- |
| `users` | `id`, `username`, `password_hash`, `nickname`, `phone`, `personal_email`, `avatar_file_id`, `public_department_enabled`, `account_status`, `alumni_flag`, `disabled_at`, `created_at`, `updated_at` | 普通用户主体。`username` 最终规则为 3 到 20 位，只允许小写字母、数字和下划线；服务端接收大小写输入但统一转小写入库，不允许空格和其他符号。真实姓名、学号和认证材料不放在公开资料字段中。`avatar_file_id` 指向 `stored_files`。 |
| `roles` | `id`, `code`, `name`, `description` | 建议种子：`REGISTERED_USER`, `VERIFIED_STUDENT`, `CONTENT_ADMIN`, `SUPER_ADMIN`。 |
| `user_roles` | `user_id`, `role_id`, `assigned_at`, `assigned_by_admin_id` | 复合唯一 `(user_id, role_id)`。管理员也可复用 `users` 主体加角色，或在实现中拆后台账号，但权限语义必须一致。 |
| `user_sessions` | `id`, `user_id`, `session_token_hash`, `last_active_at`, `expires_at`, `absolute_expires_at`, `ip_address`, `user_agent`, `revoked_at`, `created_at` | 服务端 Cookie session。受保护请求刷新 `last_active_at` 和 `expires_at`，默认闲置 7 天、绝对 30 天。管理员单端登录通过同角色活跃会话唯一性或登录时撤销旧会话实现。 |
| `campus_auths` | `id`, `user_id`, `real_name`, `student_no`, `department`, `campus_email`, `score`, `status`, `reviewed_by_admin_id`, `reviewed_at`, `failure_reason`, `identity_claim_key`, `created_at`, `updated_at` | 一名用户一条主认证记录。完整交易权限必须满足 `score >= 60`、至少一项证件类因子通过、管理员审核通过。`identity_claim_key` 保存姓名学号组合的规范化哈希或规范化值，证件通过后占用。 |
| `campus_auth_factors` | `id`, `campus_auth_id`, `factor_type`, `score_value`, `status`, `submitted_value`, `stored_file_id`, `email_token_hash`, `email_token_expires_at`, `reviewed_by_admin_id`, `reviewed_at`, `rejected_reason`, `submit_count_24h`, `created_at`, `updated_at` | 因子类型：`NAME_STUDENT_NO`, `DEPARTMENT`, `CAMPUS_EMAIL`, `STUDENT_CARD`, `CAMPUS_CARD`。同一因子 24 小时最多重提 3 次；邮箱验证链接 30 分钟有效。 |

认证分值由因子表推导或在 `campus_auths.score` 缓存。缓存必须由领域服务统一更新，不允许前端传入。

### 文件材料

| 表 | 关键字段 | 约束与说明 |
| --- | --- | --- |
| `stored_files` | `id`, `storage_bucket`, `storage_key`, `original_name`, `content_type`, `byte_size`, `checksum`, `file_kind`, `visibility_scope`, `owner_user_id`, `business_type`, `business_id`, `audit_status`, `created_at`, `deleted_at` | 保存对象存储引用和可见范围。商品图公开访问只在商品审核通过并公开上架后开放；私信图、认证材料、举报证据、申诉材料、支付异常材料默认私有。 |

文件访问 URL 不长期落库。预签名 URL 由文件服务按权限临时生成。

### 商品、分类与审核

| 表 | 关键字段 | 约束与说明 |
| --- | --- | --- |
| `categories` | `id`, `parent_id`, `name`, `code`, `sort_order`, `enabled`, `prohibited_flag`, `created_at`, `updated_at` | 分类树。禁售分类可通过 `prohibited_flag` 或独立规则维护。 |
| `tags` | `id`, `name`, `description`, `enabled`, `created_at` | 标签字典。 |
| `goods` | `id`, `seller_id`, `category_id`, `title`, `description`, `condition_level`, `list_price`, `trade_place_id`, `trade_place_detail`, `available_time_text`, `status`, `audit_status`, `current_occupied_order_id`, `search_vector`, `is_deleted`, `published_at`, `created_at`, `updated_at` | `condition_level` 仅允许全新、几乎全新、轻度使用、明显使用等结构化枚举。`list_price` 是挂牌价，不覆盖既有订单冻结金额。`current_occupied_order_id` 与订单状态双向校验。 |
| `goods_images` | `id`, `goods_id`, `file_id`, `sort_order`, `is_primary`, `created_at` | 商品图片 1-15 张，第一张或 `is_primary` 为主图。 |
| `goods_tags` | `goods_id`, `tag_id` | 复合主键。 |
| `audit_records` | `id`, `target_type`, `target_id`, `admin_id`, `result`, `reason`, `rule_summary`, `created_at` | 认证、商品、申诉复核等审核留痕。 |
| `rule_hit_records` | `id`, `target_type`, `target_id`, `rule_type`, `rule_code`, `matched_text_hash`, `severity`, `decision_hint`, `created_at` | 禁售词、禁售分类和审核预检命中记录。AI 预检标签只作为审核参考。 |
| `forbidden_terms` | `id`, `term`, `term_type`, `severity`, `enabled`, `created_by_admin_id`, `created_at`, `updated_at` | 超级管理员维护禁售关键词字典，变更必须留痕。 |

建议索引：

- `goods(category_id, status, audit_status, is_deleted)`
- `goods(seller_id, status)`
- `goods USING GIN(search_vector)`
- `goods.title`, `goods.description` 使用 `pg_trgm` GIN 或 GiST 索引支持模糊匹配
- `goods(current_occupied_order_id)` 唯一或普通索引，配合订单约束校验

### 发现与互动

| 表 | 关键字段 | 约束与说明 |
| --- | --- | --- |
| `campus_places` | `id`, `campus`, `name`, `detail`, `enabled`, `sort_order` | 校园地点字典，不保存实时定位轨迹。 |
| `favorites` | `id`, `user_id`, `goods_id`, `created_at` | 唯一 `(user_id, goods_id)`。 |
| `follows` | `id`, `follower_id`, `followed_user_id`, `created_at` | 唯一 `(follower_id, followed_user_id)`，并约束二者不同。 |
| `goods_comments` | `id`, `goods_id`, `user_id`, `parent_id`, `content`, `status`, `created_at`, `updated_at` | 公开留言，单条不超过 500 字符。 |
| `browse_records` | `id`, `user_id`, `goods_id`, `source`, `created_at` | 明细保留 90 天后转聚合统计或删除。 |
| `search_records` | `id`, `user_id`, `keyword`, `filters_json`, `result_count`, `created_at` | 不保存敏感材料内容。 |
| `recommendation_results` | `id`, `user_id`, `goods_id`, `reason_code`, `reason_text`, `score_snapshot`, `generated_at` | 推荐理由必须可解释，不使用私信正文、举报材料、申诉材料、认证材料或身份隐私资料。 |

### 会话与系统卡片

| 表 | 关键字段 | 约束与说明 |
| --- | --- | --- |
| `conversations` | `id`, `goods_id`, `buyer_id`, `seller_id`, `status`, `last_message_id`, `last_message_at`, `buyer_archived_at`, `seller_archived_at`, `created_at` | 同一商品、买家、卖家建议唯一。会话归档仅是用户侧状态，不物理删除消息事实。 |
| `messages` | `id`, `conversation_id`, `sender_id`, `message_type`, `status`, `text_content`, `card_id`, `sent_at`, `recalled_at` | 消息先持久化再 WebSocket 推送。系统卡片不可撤回，普通消息发送后 2 分钟内可撤回。 |
| `message_attachments` | `id`, `message_id`, `file_id`, `attachment_type`, `created_at` | 私信图片按会话参与关系控制访问。 |
| `system_message_cards` | `id`, `conversation_id`, `card_type`, `payload_json`, `action_status`, `created_by_system`, `created_at`, `expires_at` | 后端生成并持久化，前端不能伪造。议价卡片的已接受价格必须在 `payload_json` 中结构化保存。 |
| `message_read_states` | `message_id`, `user_id`, `read_at` | 可用来计算已读未读和重连补偿。 |

系统卡片类型包括议价卡片、面交安排卡片、订单卡片、支付提示卡片、退款进度卡片、完成确认卡片和系统通知卡片。

### 订单、支付、结算与退款

| 表 | 关键字段 | 约束与说明 |
| --- | --- | --- |
| `trade_orders` | `id`, `order_no`, `goods_id`, `buyer_id`, `seller_id`, `conversation_id`, `accepted_bargain_card_id`, `frozen_amount`, `status`, `trade_place_id`, `trade_place_detail`, `meetup_time`, `seller_payout_account_snapshot_json`, `created_at`, `updated_at`, `closed_at` | 创建订单时原子确认商品在售、冻结金额并占用商品。`frozen_amount` 来自商品挂牌价或已接受议价卡片，不能由聊天文本或前端支付参数覆盖。买卖双方不能相同。订单级收款方式快照永久保留。 |
| `order_state_records` | `id`, `order_id`, `from_status`, `to_status`, `event_type`, `operator_user_id`, `operator_admin_id`, `reason`, `metadata_json`, `created_at` | 保存完整订单状态历史和面交过程事件。 |
| `completion_confirmation_requests` | `id`, `order_id`, `seller_id`, `buyer_id`, `status`, `window_started_at`, `window_ends_at`, `notified_at`, `reminded_at`, `buyer_responded_at`, `created_at` | 卖家只能在约定面交时间后发起。买家确认窗口 48 小时，创建即通知，24 小时提醒。 |
| `payment_orders` | `id`, `payment_no`, `order_id`, `amount`, `status`, `provider`, `provider_payload_json`, `created_at`, `paid_at`, `closed_at` | 支付单只能基于卖家已确认且仍占用商品的订单创建。金额必须等于订单冻结金额。同一订单同一时间只能有一个有效待支付或支付中支付单。 |
| `payment_transactions` | `id`, `payment_order_id`, `transaction_no`, `amount`, `status`, `provider`, `occurred_at`, `raw_summary_json` | 记录模拟或真实适配器流水。 |
| `payment_callback_logs` | `id`, `payment_order_id`, `provider`, `callback_no`, `payload_hash`, `processed_status`, `processed_at`, `created_at` | 唯一 `(provider, callback_no)`。重复回调直接返回成功且不重复处理。 |
| `payout_accounts` | `id`, `seller_id`, `account_type`, `account_name`, `account_no_masked`, `account_ref_hash`, `is_default`, `status`, `created_at`, `updated_at` | 同一卖家可有多个收款方式，同一时刻只能一个默认。提交商品审核前必须存在可用默认收款方式。 |
| `settlement_records` | `id`, `order_id`, `payment_order_id`, `settlement_no`, `settlement_amount`, `status`, `freeze_started_at`, `freeze_ends_at`, `settled_at`, `failure_reason`, `created_at`, `updated_at` | 订单进入已完成待结算时创建。冻结期 7 天，期间托管资金不划拨。 |
| `settlement_attempts` | `id`, `settlement_record_id`, `attempt_no`, `amount`, `status`, `payout_account_id`, `payout_account_snapshot_json`, `provider_attempt_no`, `failure_reason`, `started_at`, `finished_at`, `created_by_admin_id` | 每次结算执行或重试创建一条。卖家更新收款方式后重试必须生成新的尝试级快照，不能改写订单级快照。 |
| `refund_orders` | `id`, `refund_no`, `order_id`, `payment_order_id`, `requested_by_user_id`, `amount`, `refund_type`, `reason`, `status`, `seller_response_deadline`, `decision_by_admin_id`, `processed_at`, `created_at` | 支持全额和部分退款。同一支付单累计退款金额不得超过可退金额。卖家 48 小时未拒绝视为同意退款。 |

关键约束和索引：

- `trade_orders` 对占用性订单建立部分唯一索引，保证同一商品同一时间最多一个占用性订单。
- `goods.current_occupied_order_id` 与 `trade_orders.status` 双向校验，占用释放时同步清空。
- `payment_orders(order_id)` 对 `PENDING`, `PROCESSING` 状态建立部分唯一索引。
- `payout_accounts(seller_id)` 对 `is_default = true AND status = 'ACTIVE'` 建立部分唯一索引。
- `settlement_records(order_id)` 唯一，一笔订单只对应一条结算生命周期记录。
- `settlement_attempts(settlement_record_id, attempt_no)` 唯一。
- 结算冻结期到期任务必须幂等，重复执行不能重复创建结算尝试或重复推进资金状态。

### 评价与信用

| 表 | 关键字段 | 约束与说明 |
| --- | --- | --- |
| `reviews` | `id`, `order_id`, `reviewer_id`, `reviewed_user_id`, `rating`, `content`, `status`, `submitted_at`, `modified_until`, `visible_at`, `hidden_by_admin_id`, `hidden_reason` | 订单完成前不得评价。每个订单每个参与方最多评价一次。双方都提交或评价窗口关闭后公开。提交后 72 小时内可修改。 |
| `review_tag_options` | `id`, `code`, `label`, `enabled` | 预设评价标签字典。 |
| `review_tags` | `review_id`, `tag_option_id` | 复合主键。 |
| `credit_records` | `id`, `user_id`, `source_type`, `source_id`, `reason`, `internal_delta_value`, `internal_level_before`, `internal_level_after`, `public_label`, `created_by_admin_id`, `created_at` | 信用变化只能来自订单完成、结构化评价、履约事实、举报成立、处罚记录、纠纷责任、申诉结果和人工修正。评价文本、私信内容、AI 预检标签、退款金额和未成立举报不能直接改变信用。 |
| `credit_summaries` | `user_id`, `fulfillment_count`, `on_time_meetup_count`, `positive_review_count`, `negative_event_count`, `public_tags_json`, `internal_score`, `internal_level`, `updated_at` | 对外展示维度摘要和具名行为标签，不展示单一聚合信用分。内部数值或等级仅用于排序、风控和审计。 |

### 治理、申诉与处罚

| 表 | 关键字段 | 约束与说明 |
| --- | --- | --- |
| `reports` | `id`, `reporter_id`, `target_type`, `target_id`, `reason_type`, `description`, `status`, `priority`, `merged_into_report_id`, `handled_by_admin_id`, `handled_at`, `created_at` | 举报对象覆盖商品、留言、消息、订单和账号。同一举报人对同一对象 24 小时内重复提交应合并处理。 |
| `report_evidence_files` | `report_id`, `file_id` | 举报证据图最多 5 张。 |
| `appeals` | `id`, `report_id`, `appellant_id`, `description`, `status`, `reviewed_by_admin_id`, `reviewed_at`, `created_at` | 被处理用户可发起申诉复核。 |
| `appeal_evidence_files` | `appeal_id`, `file_id` | 申诉证据图最多 10 张。 |
| `dispute_responsibility_records` | `id`, `order_id`, `responsible_user_id`, `responsibility_type`, `conclusion`, `created_by_admin_id`, `created_at` | 记录纠纷责任，不直接由退款金额推导。 |
| `penalty_records` | `id`, `user_id`, `penalty_type`, `reason`, `source_type`, `source_id`, `started_at`, `ended_at`, `created_by_admin_id`, `created_at` | 处罚类型包括警告、商品下架、限制发布、限制交易、认证失效和账号禁用。 |

### 通知、审计与配置

| 表 | 关键字段 | 约束与说明 |
| --- | --- | --- |
| `notifications` | `id`, `receiver_user_id`, `type`, `title`, `content`, `related_type`, `related_id`, `read_at`, `created_at` | 站内通知是交易状态、审核结果和治理处理的正式通知渠道。 |
| `announcements` | `id`, `title`, `content`, `status`, `published_by_admin_id`, `published_at`, `created_at` | 后台公告。 |
| `operation_logs` | `id`, `admin_id`, `action`, `target_type`, `target_id`, `before_json`, `after_json`, `ip_address`, `created_at` | 管理员关键操作必须留痕。 |
| `sensitive_access_logs` | `id`, `admin_id`, `target_type`, `target_id`, `reason`, `result`, `ip_address`, `created_at` | 管理员查看认证材料、举报证据、申诉材料、私信内容等敏感材料时必须记录。 |
| `system_configs` | `id`, `config_key`, `config_value`, `value_type`, `description`, `updated_by_admin_id`, `updated_at` | 保存可信度阈值、时间窗口、容量限制、AI 开关等业务配置。配置变更通过 `operation_logs` 留痕。 |

### 智能辅助

| 表 | 关键字段 | 约束与说明 |
| --- | --- | --- |
| `intelligence_tasks` | `id`, `goods_id`, `task_type`, `provider_type`, `status`, `input_summary_json`, `retry_count`, `quota_key`, `started_at`, `finished_at`, `failure_reason`, `created_by_user_id`, `created_at` | AI 或规则任务先落库再执行。30 秒未返回视为失败，同一任务最多自动重试 2 次。单商品同类发布辅助每日上限 5 次。 |
| `intelligence_results` | `id`, `task_id`, `result_type`, `content_json`, `reason`, `confidence`, `precheck_labels_json`, `status`, `adopted_by_user_id`, `adopted_at`, `expires_at`, `created_at` | AI 输出只作为发布建议或审核预检标签，不直接改变商品、订单、举报、处罚或用户状态。24 小时未采纳即过期。 |

## 关键业务约束

### 商品占用

订单创建必须在同一事务内完成：

1. 锁定 `goods` 行。
2. 校验商品 `status = 'ON_SALE'`、`is_deleted = false`、`current_occupied_order_id IS NULL`。
3. 校验买家具备完整交易权限且买卖双方不同。
4. 计算并保存 `trade_orders.frozen_amount`。
5. 插入订单并更新 `goods.current_occupied_order_id` 和商品状态。
6. 写入 `order_state_records`。

商品占用释放场景包括卖家拒绝、买家取消、支付超时、订单关闭或未结算退款完成。

### 结算冻结期

订单进入 `COMPLETED_PENDING_SETTLEMENT` 时：

1. 商品进入已售语义，不再允许新订单。
2. 创建 `settlement_records`，设置 `freeze_started_at` 和 `freeze_ends_at = freeze_started_at + interval '7 days'`。
3. 冻结期内允许常规退款，托管资金不划拨。
4. 冻结期到期且无退款、纠纷、订单相关举报处理或管理员冻结时，创建 `settlement_attempts` 并推进结算。
5. 已结算后争议进入申诉与人工处理，不再走常规退款入口。

### 收款方式快照

- `trade_orders.seller_payout_account_snapshot_json` 保存下单时卖家默认收款方式快照，永久不改写。
- `settlement_attempts.payout_account_snapshot_json` 保存每次结算尝试时的收款方式快照。
- 结算失败后，卖家更新收款方式，管理员重试时生成新的结算尝试和新的尝试级快照。

### 信用呈现

- `credit_records` 保存内部数值或等级变化，必须可追溯来源业务和原因。
- `credit_summaries` 可以缓存内部排序和风控所需数值。
- 用户公开页不展示单一聚合信用分，只展示维度摘要和具名行为标签。

### 会话与系统卡片

- `messages` 是实时私信的唯一事实来源，WebSocket 推送失败不能删除或回滚已保存消息。
- 议价价必须来自 `system_message_cards` 中已接受的议价卡片。
- 同一订单关闭前，不允许继续为同一商品发起新议价卡片。

## 索引与查询

| 场景 | 建议索引 |
| --- | --- |
| 商品发现 | `goods(status, audit_status, is_deleted, category_id)`, `goods(seller_id)`, `goods USING GIN(search_vector)`, `pg_trgm` 标题和描述索引 |
| 商品占用 | `goods(current_occupied_order_id)`, `trade_orders(goods_id, status)`，占用性订单部分唯一索引 |
| 订单列表 | `trade_orders(buyer_id, created_at DESC)`, `trade_orders(seller_id, created_at DESC)`, `trade_orders(goods_id)` |
| 支付幂等 | `payment_callback_logs(provider, callback_no)` 唯一，`payment_orders(order_id)` 有效支付单部分唯一 |
| 结算任务 | `settlement_records(status, freeze_ends_at)`, `settlement_attempts(settlement_record_id, attempt_no)` 唯一 |
| 退款处理 | `refund_orders(order_id, status)`, `refund_orders(payment_order_id)` |
| 会话消息 | `messages(conversation_id, sent_at)`, `message_read_states(user_id, read_at)` |
| 治理后台 | `reports(status, priority, created_at)`, `appeals(status, created_at)` |
| 审计查询 | `operation_logs(admin_id, created_at)`, `sensitive_access_logs(admin_id, created_at)`, `sensitive_access_logs(target_type, target_id)` |

## 统计视图

建议提供以下视图或物化视图：

| 名称 | 来源 |
| --- | --- |
| `analytics_user_overview` | `users`, `campus_auths`, `user_sessions` |
| `analytics_goods_overview` | `goods`, `categories`, `favorites`, `browse_records` |
| `analytics_order_overview` | `trade_orders`, `order_state_records` |
| `analytics_payment_overview` | `payment_orders`, `settlement_records`, `refund_orders` |
| `analytics_governance_overview` | `reports`, `appeals`, `penalty_records` |
| `analytics_credit_overview` | `credit_records`, `credit_summaries`, `reviews` |

统计结果不能反向修改订单、支付、商品、信用、举报、处罚或审核记录。

## 初始化数据

种子脚本必须初始化：

- 初始超级管理员。
- 学校名称、校区、院系、校园邮箱后缀。
- 商品分类、标签、禁售关键词和禁售分类。
- 校园地点字典。
- 举报类型、处罚类型、评价标签。
- 角色和基础权限。
- 系统配置项：认证阈值、时间窗口、容量限制、AI 开关、文件预签名 URL 时长。

## 迁移顺序

建议按以下顺序实现 migration：

1. 基础字典与身份权限：`users`, `roles`, `user_roles`, `user_sessions`, `system_configs`。
2. 文件、认证和地点：`stored_files`, `campus_auths`, `campus_auth_factors`, `campus_places`。
3. 商品与发现：`categories`, `tags`, `goods`, `goods_images`, `goods_tags`, `favorites`, `follows`, `goods_comments`, `browse_records`, `search_records`, `recommendation_results`。
4. 会话：`conversations`, `messages`, `message_attachments`, `system_message_cards`, `message_read_states`。
5. 订单与资金：`trade_orders`, `order_state_records`, `completion_confirmation_requests`, `payout_accounts`, `payment_orders`, `payment_transactions`, `payment_callback_logs`, `settlement_records`, `settlement_attempts`, `refund_orders`。
6. 评价信用与治理：`reviews`, `review_tag_options`, `review_tags`, `credit_records`, `credit_summaries`, `reports`, `report_evidence_files`, `appeals`, `appeal_evidence_files`, `dispute_responsibility_records`, `penalty_records`。
7. 审计通知与 AI：`notifications`, `announcements`, `operation_logs`, `sensitive_access_logs`, `audit_records`, `rule_hit_records`, `forbidden_terms`, `intelligence_tasks`, `intelligence_results`。
8. 搜索索引、部分唯一索引、统计视图和物化视图。

迁移脚本必须能在空库重复部署；种子脚本必须可幂等执行。
