# Campus Resale ER 图（Mermaid erDiagram）

将以下代码粘贴到 Draw.io → `+` → `高级` → `Mermaid` 即可直接生成。

```mermaid
erDiagram

    %% ─────────────────────────────────────────────
    %% 用户与认证体系
    %% ─────────────────────────────────────────────

    users {
        bigint id PK
        varchar username
        varchar password_hash
        varchar nickname
        varchar phone
        varchar personal_email
        bigint avatar_file_id FK
        boolean public_department_enabled
        varchar account_status
        boolean alumni_flag
        timestamptz disabled_at
        timestamptz created_at
        timestamptz updated_at
    }

    stored_files {
        bigint id PK
        varchar storage_bucket
        varchar storage_key
        varchar original_name
        varchar content_type
        bigint byte_size
        varchar checksum
        varchar file_kind
        varchar visibility_scope
        bigint owner_user_id FK
        varchar business_type
        bigint business_id
        varchar audit_status
        timestamptz created_at
        timestamptz deleted_at
    }

    roles {
        bigint id PK
        varchar code
        varchar name
        text description
    }

    user_roles {
        bigint user_id FK
        bigint role_id FK
        timestamptz assigned_at
        bigint assigned_by_admin_id FK
    }

    user_sessions {
        bigint id PK
        bigint user_id FK
        varchar session_token_hash
        timestamptz last_active_at
        timestamptz expires_at
        timestamptz absolute_expires_at
        inet ip_address
        text user_agent
        timestamptz revoked_at
        timestamptz created_at
    }

    campus_auths {
        bigint id PK
        bigint user_id FK
        varchar real_name
        varchar student_no
        varchar department
        varchar campus_email
        int score
        varchar status
        bigint reviewed_by_admin_id FK
        timestamptz reviewed_at
        text failure_reason
        varchar identity_claim_key
        timestamptz created_at
        timestamptz updated_at
    }

    campus_auth_factors {
        bigint id PK
        bigint campus_auth_id FK
        varchar factor_type
        int score_value
        varchar status
        text submitted_value
        bigint stored_file_id FK
        varchar email_token_hash
        timestamptz email_token_expires_at
        bigint reviewed_by_admin_id FK
        timestamptz reviewed_at
        text rejected_reason
        int submit_count_24h
        timestamptz submit_window_started_at
        timestamptz created_at
        timestamptz updated_at
    }

    campus_auth_factor_files {
        bigint id PK
        bigint campus_auth_factor_id FK
        bigint stored_file_id FK
        int sort_order
        timestamptz created_at
    }

    %% ─────────────────────────────────────────────
    %% 平台配置与系统日志
    %% ─────────────────────────────────────────────

    system_configs {
        bigint id PK
        varchar config_key
        text config_value
        varchar value_type
        text description
        bigint updated_by_admin_id FK
        timestamptz updated_at
    }

    operation_logs {
        bigint id PK
        bigint admin_id FK
        varchar action
        varchar target_type
        bigint target_id
        jsonb before_json
        jsonb after_json
        inet ip_address
        text user_agent
        varchar request_path
        varchar http_method
        varchar result
        varchar operator_type
        timestamptz created_at
    }

    sensitive_access_logs {
        bigint id PK
        bigint admin_id FK
        varchar target_type
        bigint target_id
        text reason
        varchar result
        inet ip_address
        timestamptz created_at
    }

    %% ─────────────────────────────────────────────
    %% 商品目录体系
    %% ─────────────────────────────────────────────

    categories {
        bigint id PK
        bigint parent_id FK
        varchar name
        varchar code
        int sort_order
        boolean enabled
        boolean prohibited_flag
        timestamptz created_at
        timestamptz updated_at
    }

    tags {
        bigint id PK
        varchar name
        varchar description
        boolean enabled
        timestamptz created_at
    }

    campus_places {
        bigint id PK
        varchar campus
        varchar name
        varchar detail
        boolean enabled
        int sort_order
    }

    goods {
        bigint id PK
        bigint seller_id FK
        bigint category_id FK
        varchar title
        text description
        varchar condition_level
        numeric list_price
        bigint trade_place_id FK
        varchar trade_place_detail
        varchar available_time_text
        varchar status
        varchar audit_status
        bigint current_occupied_order_id FK
        tsvector search_vector
        boolean is_deleted
        timestamptz published_at
        timestamptz created_at
        timestamptz updated_at
    }

    goods_images {
        bigint id PK
        bigint goods_id FK
        bigint file_id FK
        int sort_order
        boolean is_primary
        timestamptz created_at
    }

    goods_tags {
        bigint goods_id FK
        bigint tag_id FK
    }

    %% ─────────────────────────────────────────────
    %% 审核规则体系
    %% ─────────────────────────────────────────────

    audit_records {
        bigint id PK
        varchar target_type
        bigint target_id
        bigint admin_id FK
        varchar result
        text reason
        text rule_summary
        timestamptz created_at
    }

    rule_hit_records {
        bigint id PK
        varchar target_type
        bigint target_id
        varchar rule_type
        varchar rule_code
        varchar matched_text_hash
        varchar severity
        varchar decision_hint
        timestamptz created_at
    }

    forbidden_terms {
        bigint id PK
        varchar term
        varchar term_type
        varchar severity
        boolean enabled
        bigint created_by_admin_id FK
        timestamptz created_at
        timestamptz updated_at
    }

    %% ─────────────────────────────────────────────
    %% 通知体系
    %% ─────────────────────────────────────────────

    notifications {
        bigint id PK
        bigint receiver_user_id FK
        varchar type
        varchar title
        text content
        varchar related_type
        bigint related_id
        varchar dedupe_key
        timestamptz read_at
        timestamptz created_at
    }

    %% ─────────────────────────────────────────────
    %% 交易订单体系
    %% ─────────────────────────────────────────────

    trade_orders {
        bigint id PK
        varchar order_no
        bigint goods_id FK
        bigint buyer_id FK
        bigint seller_id FK
        bigint conversation_id FK
        bigint accepted_bargain_card_id FK
        numeric frozen_amount
        varchar status
        bigint trade_place_id FK
        varchar trade_place_detail
        timestamptz meetup_time
        varchar buyer_note
        jsonb seller_payout_account_snapshot_json
        timestamptz created_at
        timestamptz updated_at
        timestamptz closed_at
    }

    order_state_records {
        bigint id PK
        bigint order_id FK
        varchar from_status
        varchar to_status
        varchar event_type
        bigint operator_user_id FK
        bigint operator_admin_id FK
        varchar reason
        jsonb metadata_json
        timestamptz created_at
    }

    completion_confirmation_requests {
        bigint id PK
        bigint order_id FK
        bigint seller_id FK
        bigint buyer_id FK
        varchar status
        timestamptz window_starts_at
        timestamptz window_ends_at
        timestamptz confirmed_at
        timestamptz created_at
        timestamptz updated_at
    }

    %% ─────────────────────────────────────────────
    %% 支付与结算体系
    %% ─────────────────────────────────────────────

    payment_orders {
        bigint id PK
        varchar payment_no
        bigint order_id FK
        numeric amount
        varchar status
        varchar provider
        jsonb provider_payload_json
        timestamptz created_at
        timestamptz paid_at
        timestamptz closed_at
    }

    payment_transactions {
        bigint id PK
        bigint payment_order_id FK
        varchar transaction_no
        numeric amount
        varchar status
        varchar provider
        timestamptz occurred_at
        jsonb raw_summary_json
    }

    payment_callback_logs {
        bigint id PK
        bigint payment_order_id FK
        varchar provider
        varchar callback_no
        varchar payload_hash
        varchar processed_status
        timestamptz processed_at
        timestamptz created_at
    }

    settlement_records {
        bigint id PK
        bigint order_id FK
        bigint payment_order_id FK
        varchar settlement_no
        numeric settlement_amount
        varchar status
        timestamptz freeze_started_at
        timestamptz freeze_ends_at
        timestamptz settled_at
        varchar failure_reason
        timestamptz created_at
        timestamptz updated_at
    }

    settlement_attempts {
        bigint id PK
        bigint settlement_record_id FK
        int attempt_no
        numeric amount
        varchar status
        bigint payout_account_id
        jsonb payout_account_snapshot_json
        varchar provider_attempt_no
        varchar failure_reason
        timestamptz started_at
        timestamptz finished_at
        bigint created_by_admin_id FK
    }

    reviews {
        bigint id PK
        bigint order_id FK
        bigint reviewer_id FK
        bigint reviewed_user_id FK
        int rating
        varchar content
        varchar status
        timestamptz submitted_at
        timestamptz modified_until
        timestamptz visible_at
        bigint hidden_by_admin_id FK
        varchar hidden_reason
    }

    %% ─────────────────────────────────────────────
    %% 治理体系（举报、申诉、处罚、信用）
    %% ─────────────────────────────────────────────

    favorites {
        bigint id PK
        bigint user_id FK
        bigint goods_id FK
        timestamptz created_at
    }

    follows {
        bigint id PK
        bigint follower_id FK
        bigint followed_user_id FK
        timestamptz created_at
    }

    reports {
        bigint id PK
        bigint reporter_id FK
        varchar target_type
        bigint target_id
        varchar reason_type
        varchar description
        varchar status
        varchar priority
        bigint merged_into_report_id FK
        bigint handled_by_admin_id FK
        timestamptz handled_at
        varchar handling_note
        timestamptz created_at
    }

    report_evidence_files {
        bigint report_id FK
        bigint file_id FK
        timestamptz created_at
    }

    appeals {
        bigint id PK
        bigint report_id FK
        bigint appellant_id FK
        varchar description
        varchar status
        bigint reviewed_by_admin_id FK
        timestamptz reviewed_at
        varchar review_note
        timestamptz created_at
    }

    appeal_evidence_files {
        bigint appeal_id FK
        bigint file_id FK
        timestamptz created_at
    }

    refund_orders {
        bigint id PK
        varchar refund_no
        bigint order_id FK
        bigint payment_order_id FK
        bigint requested_by_user_id FK
        numeric amount
        varchar refund_type
        varchar reason
        varchar status
        varchar status_before_refund
        bigint decision_by_admin_id FK
        varchar decision_note
        timestamptz reviewed_at
        timestamptz processed_at
        varchar provider_refund_no
        varchar failure_reason
        timestamptz created_at
    }

    refund_evidence_files {
        bigint refund_order_id FK
        bigint file_id FK
        timestamptz created_at
    }

    penalty_records {
        bigint id PK
        bigint user_id FK
        bigint report_id FK
        bigint appeal_id FK
        varchar penalty_type
        varchar reason
        varchar status
        bigint created_by_admin_id FK
        bigint lifted_by_admin_id FK
        timestamptz lifted_at
        timestamptz created_at
    }

    credit_records {
        bigint id PK
        bigint user_id FK
        varchar source_type
        bigint source_id
        varchar reason
        int internal_delta_value
        varchar internal_level_before
        varchar internal_level_after
        varchar public_label
        bigint created_by_admin_id FK
        timestamptz created_at
    }

    credit_summaries {
        bigint user_id PK-FK
        int fulfillment_count
        int on_time_meetup_count
        int positive_review_count
        int negative_event_count
        jsonb public_tags_json
        int internal_score
        varchar internal_level
        timestamptz updated_at
    }

    %% ─────────────────────────────────────────────
    %% 会话与消息体系
    %% ─────────────────────────────────────────────

    conversations {
        bigint id PK
        bigint goods_id FK
        bigint buyer_id FK
        bigint seller_id FK
        varchar status
        bigint last_message_id FK
        timestamptz last_message_at
        timestamptz buyer_archived_at
        timestamptz seller_archived_at
        timestamptz created_at
        timestamptz updated_at
    }

    system_message_cards {
        bigint id PK
        bigint conversation_id FK
        varchar card_type
        numeric amount
        jsonb payload_json
        varchar action_status
        bigint created_by_user_id FK
        bigint acted_by_user_id FK
        boolean created_by_system
        timestamptz created_at
        timestamptz expires_at
        timestamptz acted_at
    }

    messages {
        bigint id PK
        bigint conversation_id FK
        bigint sender_id FK
        varchar message_type
        varchar status
        varchar text_content
        bigint card_id FK
        timestamptz sent_at
        timestamptz recalled_at
    }

    message_read_states {
        bigint message_id FK
        bigint user_id FK
        timestamptz read_at
    }

    message_attachments {
        bigint id PK
        bigint message_id FK
        bigint file_id FK
        int sort_order
        timestamptz created_at
    }

    %% ─────────────────────────────────────────────
    %% AI 辅助体系
    %% ─────────────────────────────────────────────

    ai_assist_records {
        bigint id PK
        bigint user_id FK
        varchar scenario
        varchar input_title
        text input_description
        numeric input_price
        varchar optimized_title
        text optimized_description
        varchar suggested_category_code
        jsonb suggested_tags_json
        varchar risk_level
        jsonb risk_reasons_json
        varchar recommendation_reason
        varchar audit_reminder
        timestamptz created_at
    }


    %% ═════════════════════════════════════════════
    %% 关系定义
    %% ═════════════════════════════════════════════

    %% 用户 & 文件
    users ||--o{ stored_files : "owns (owner_user_id)"
    users ||--o| stored_files : "avatar (avatar_file_id)"

    %% 用户 & 角色
    users ||--o{ user_roles : "has"
    roles ||--o{ user_roles : "assigned to"
    users ||--o{ user_roles : "assigned_by_admin"

    %% 用户会话
    users ||--o{ user_sessions : "has session"

    %% 校园认证
    users ||--o| campus_auths : "applies for"
    users ||--o{ campus_auths : "reviews (admin)"
    campus_auths ||--|{ campus_auth_factors : "contains"
    stored_files ||--o{ campus_auth_factors : "attached as factor file"
    users ||--o{ campus_auth_factors : "reviews factor (admin)"
    campus_auth_factors ||--o{ campus_auth_factor_files : "has files"
    stored_files ||--o{ campus_auth_factor_files : "stored as"

    %% 平台配置
    users ||--o{ system_configs : "updated_by_admin"
    users ||--o{ operation_logs : "admin performs"
    users ||--o{ sensitive_access_logs : "admin accesses"

    %% 分类层级
    categories ||--o{ categories : "parent (self-ref)"

    %% 商品
    users ||--o{ goods : "sells"
    categories ||--o{ goods : "categorizes"
    campus_places ||--o{ goods : "trade_place"
    goods ||--o{ goods_images : "has images"
    stored_files ||--o{ goods_images : "is image"
    goods ||--o{ goods_tags : "tagged with"
    tags ||--o{ goods_tags : "applied to"

    %% 审核
    users ||--o{ audit_records : "admin audits"
    users ||--o{ forbidden_terms : "admin creates"

    %% 通知
    users ||--o{ notifications : "receives"

    %% 交易订单
    goods ||--o{ trade_orders : "traded as"
    users ||--o{ trade_orders : "buys (buyer)"
    users ||--o{ trade_orders : "sells (seller)"
    campus_places ||--o{ trade_orders : "meetup_place"
    trade_orders ||--o{ order_state_records : "state history"
    users ||--o{ order_state_records : "operator_user"
    users ||--o{ order_state_records : "operator_admin"
    trade_orders ||--o{ completion_confirmation_requests : "confirms"
    users ||--o{ completion_confirmation_requests : "seller"
    users ||--o{ completion_confirmation_requests : "buyer"

    %% 支付
    trade_orders ||--o{ payment_orders : "paid via"
    payment_orders ||--o{ payment_transactions : "has transactions"
    payment_orders ||--o{ payment_callback_logs : "callback logs"

    %% 结算
    trade_orders ||--o| settlement_records : "settled by"
    payment_orders ||--o{ settlement_records : "linked payment"
    settlement_records ||--o{ settlement_attempts : "attempt"
    users ||--o{ settlement_attempts : "admin creates attempt"

    %% 评价
    trade_orders ||--o{ reviews : "reviewed after"
    users ||--o{ reviews : "reviewer"
    users ||--o{ reviews : "reviewed_user"
    users ||--o{ reviews : "hidden_by_admin"

    %% 收藏 & 关注
    users ||--o{ favorites : "favorites"
    goods ||--o{ favorites : "favorited by"
    users ||--o{ follows : "follows (follower)"
    users ||--o{ follows : "followed by (followed_user)"

    %% 举报
    users ||--o{ reports : "reports"
    users ||--o{ reports : "handled_by_admin"
    reports ||--o{ reports : "merged into (self-ref)"
    reports ||--o{ report_evidence_files : "has evidence"
    stored_files ||--o{ report_evidence_files : "evidence file"

    %% 申诉
    reports ||--o{ appeals : "appealed via"
    users ||--o{ appeals : "appellant"
    users ||--o{ appeals : "reviewed_by_admin"
    appeals ||--o{ appeal_evidence_files : "has evidence"
    stored_files ||--o{ appeal_evidence_files : "evidence file"

    %% 退款
    trade_orders ||--o{ refund_orders : "refunded by"
    payment_orders ||--o{ refund_orders : "payment linked"
    users ||--o{ refund_orders : "requested_by"
    users ||--o{ refund_orders : "decision_by_admin"
    refund_orders ||--o{ refund_evidence_files : "has evidence"
    stored_files ||--o{ refund_evidence_files : "evidence file"

    %% 处罚
    users ||--o{ penalty_records : "penalized"
    reports ||--o{ penalty_records : "triggered by report"
    appeals ||--o{ penalty_records : "triggered by appeal"
    users ||--o{ penalty_records : "created_by_admin"
    users ||--o{ penalty_records : "lifted_by_admin"

    %% 信用
    users ||--o{ credit_records : "credit changed"
    users ||--o{ credit_records : "created_by_admin"
    users ||--o| credit_summaries : "has credit summary"

    %% 会话
    goods ||--o{ conversations : "discussed in"
    users ||--o{ conversations : "buyer in"
    users ||--o{ conversations : "seller in"
    conversations ||--o{ system_message_cards : "contains cards"
    users ||--o{ system_message_cards : "created_by_user"
    users ||--o{ system_message_cards : "acted_by_user"
    conversations ||--o{ messages : "contains"
    users ||--o{ messages : "sender"
    system_message_cards ||--o{ messages : "card message"
    messages ||--o{ message_read_states : "read by"
    users ||--o{ message_read_states : "reads"
    messages ||--o{ message_attachments : "has attachments"
    stored_files ||--o{ message_attachments : "attachment file"

    %% 会话 ↔ 订单互引用
    conversations ||--o| messages : "last_message (FK)"
    conversations ||--o{ trade_orders : "leads to order"
    system_message_cards ||--o{ trade_orders : "accepted_bargain_card"

    %% AI 辅助
    users ||--o{ ai_assist_records : "uses AI assist"
```
