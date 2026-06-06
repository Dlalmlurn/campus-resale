-- =============================================================
-- V14: N2 后台验收闭环 —— 管理操作日志增强 + 统计聚合视图
-- =============================================================
-- 目标：支撑 N2「管理员全面验收和追溯交易数据」的四个维度：
--   维度 A: operation_logs 补充可追溯字段，满足后台审计查询
--   维度 C: 订单/支付/结算/商品/评价/用户/认证统计聚合视图
--   通用:   为后台分页查询补充辅助索引
--
-- 约束：
--   - 已存在的 V1-V13 表不得删除或重命名
--   - 所有新字段带 DEFAULT，兼容已有行（不破坏测试和种子数据）
--   - 视图使用 CREATE OR REPLACE，幂等安全
-- =============================================================


-- =============================================================
-- Part 1: operation_logs 补充字段
--   现有字段: id, admin_id, action, target_type, target_id,
--             before_json, after_json, ip_address, created_at
--   N2 新增:  user_agent, request_path, http_method,
--             result, operator_type
-- =============================================================

ALTER TABLE operation_logs
    ADD COLUMN IF NOT EXISTS user_agent    TEXT,
    ADD COLUMN IF NOT EXISTS request_path  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS http_method   VARCHAR(10),
    ADD COLUMN IF NOT EXISTS result        VARCHAR(40) NOT NULL DEFAULT 'SUCCESS',
    ADD COLUMN IF NOT EXISTS operator_type VARCHAR(40) NOT NULL DEFAULT 'ADMIN';

-- result: 操作执行结果
ALTER TABLE operation_logs
    ADD CONSTRAINT ck_operation_logs_result
    CHECK (result IN ('SUCCESS', 'FAILURE', 'PARTIAL'));

-- operator_type: 区分管理员手动操作 / 后端系统自动触发 / 定时任务
ALTER TABLE operation_logs
    ADD CONSTRAINT ck_operation_logs_operator_type
    CHECK (operator_type IN ('ADMIN', 'SYSTEM', 'SCHEDULED'));

-- 后台按时间范围全量翻页（不带任何过滤条件）
CREATE INDEX IF NOT EXISTS idx_operation_logs_created
    ON operation_logs (created_at DESC);

-- 按操作类型 + 时间筛选（如 "只看 GOODS_APPROVE"）
CREATE INDEX IF NOT EXISTS idx_operation_logs_action_created
    ON operation_logs (action, created_at DESC);

-- 按执行结果 + 时间（后台快速定位失败操作）
CREATE INDEX IF NOT EXISTS idx_operation_logs_result_created
    ON operation_logs (result, created_at DESC);


-- =============================================================
-- Part 2: 统计聚合视图
--   全部使用 CREATE OR REPLACE VIEW，不产生副作用，幂等安全
--   只做简单 COUNT / SUM / AVG 聚合，禁止窗口函数或递归 CTE
--   前端/后端读取时每次实时计算，无需刷新
-- =============================================================

-- 2.1 订单总体统计
CREATE OR REPLACE VIEW v_order_stats AS
SELECT
    COUNT(*)                                                             AS total_orders,
    COUNT(*) FILTER (WHERE status = 'PENDING_SELLER_CONFIRM')           AS pending_seller_confirm,
    COUNT(*) FILTER (WHERE status = 'PENDING_PAYMENT')                  AS pending_payment,
    COUNT(*) FILTER (WHERE status = 'PAID_PENDING_MEETUP')              AS paid_pending_meetup,
    COUNT(*) FILTER (WHERE status = 'COMPLETED_PENDING_SETTLEMENT')     AS completed_pending_settlement,
    COUNT(*) FILTER (WHERE status = 'COMPLETED')                        AS completed,
    COUNT(*) FILTER (WHERE status = 'CANCELLED')                        AS cancelled,
    COUNT(*) FILTER (WHERE status = 'CLOSED')                           AS closed,
    COUNT(*) FILTER (WHERE status = 'DISPUTE_PROCESSING')               AS dispute_processing,
    COUNT(*) FILTER (WHERE status = 'REFUND_PROCESSING')                AS refund_processing,
    -- 活跃冻结金额：仍在占用商品的订单
    COALESCE(SUM(frozen_amount) FILTER (WHERE status NOT IN (
        'CANCELLED', 'CLOSED', 'COMPLETED'
    )), 0)                                                               AS active_frozen_amount,
    -- 已完成订单总金额（可用于答辩展示平台流水）
    COALESCE(SUM(frozen_amount) FILTER (WHERE status = 'COMPLETED'), 0) AS completed_amount
FROM trade_orders;

-- 2.2 订单近 30 天每日成交量（用于后台折线图/表格，按天聚合）
CREATE OR REPLACE VIEW v_order_daily_trend AS
SELECT
    DATE(created_at AT TIME ZONE 'Asia/Shanghai') AS stat_date,
    COUNT(*)                                       AS total_created,
    COUNT(*) FILTER (WHERE status = 'COMPLETED')   AS completed_count,
    COUNT(*) FILTER (WHERE status = 'CANCELLED')   AS cancelled_count
FROM trade_orders
WHERE created_at >= now() - INTERVAL '30 days'
GROUP BY DATE(created_at AT TIME ZONE 'Asia/Shanghai')
ORDER BY stat_date DESC;

-- 2.3 支付统计
CREATE OR REPLACE VIEW v_payment_stats AS
SELECT
    COUNT(*)                                                      AS total_payments,
    COUNT(*) FILTER (WHERE status = 'PENDING')                    AS pending,
    COUNT(*) FILTER (WHERE status = 'PROCESSING')                 AS processing,
    COUNT(*) FILTER (WHERE status = 'ESCROWED')                   AS escrowed,
    COUNT(*) FILTER (WHERE status = 'FAILED')                     AS failed,
    COUNT(*) FILTER (WHERE status = 'CLOSED')                     AS closed,
    COALESCE(SUM(amount) FILTER (WHERE status = 'ESCROWED'), 0)   AS escrowed_amount,
    COALESCE(SUM(amount) FILTER (WHERE status IN (
        'ESCROWED', 'CLOSED'
    )), 0)                                                        AS total_processed_amount
FROM payment_orders;

-- 2.4 结算统计
CREATE OR REPLACE VIEW v_settlement_stats AS
SELECT
    COUNT(*)                                                                   AS total_settlements,
    COUNT(*) FILTER (WHERE status = 'PENDING')                                 AS pending,
    COUNT(*) FILTER (WHERE status = 'PROCESSING')                              AS processing,
    COUNT(*) FILTER (WHERE status = 'SETTLED')                                 AS settled,
    COUNT(*) FILTER (WHERE status = 'FAILED')                                  AS failed,
    COUNT(*) FILTER (WHERE status = 'CLOSED')                                  AS closed,
    COALESCE(SUM(settlement_amount) FILTER (WHERE status = 'SETTLED'), 0)      AS total_settled_amount,
    -- 待结算金额（即将或正在处理中的结算）
    COALESCE(SUM(settlement_amount) FILTER (WHERE status IN (
        'PENDING', 'PROCESSING'
    )), 0)                                                                     AS pending_settlement_amount
FROM settlement_records;

-- 2.5 商品统计
CREATE OR REPLACE VIEW v_goods_stats AS
SELECT
    COUNT(*) FILTER (WHERE NOT is_deleted)                                  AS total_goods,
    COUNT(*) FILTER (WHERE status = 'DRAFT'          AND NOT is_deleted)    AS draft,
    COUNT(*) FILTER (WHERE status = 'PENDING_REVIEW' AND NOT is_deleted)    AS pending_review,
    COUNT(*) FILTER (WHERE status = 'ON_SALE'        AND NOT is_deleted)    AS on_sale,
    COUNT(*) FILTER (WHERE status = 'RESERVED'       AND NOT is_deleted)    AS reserved,
    COUNT(*) FILTER (WHERE status = 'SOLD'           AND NOT is_deleted)    AS sold,
    COUNT(*) FILTER (WHERE status = 'OFF_SHELF'      AND NOT is_deleted)    AS off_shelf,
    COUNT(*) FILTER (WHERE is_deleted)                                       AS deleted,
    -- 审核队列待处理量（用于后台 badge 提醒）
    COUNT(*) FILTER (WHERE audit_status = 'PENDING'  AND NOT is_deleted)    AS audit_pending
FROM goods;

-- 2.6 评价统计
CREATE OR REPLACE VIEW v_review_stats AS
SELECT
    COUNT(*)                                                                         AS total_reviews,
    COUNT(*) FILTER (WHERE status = 'SUBMITTED')                                     AS submitted,
    COUNT(*) FILTER (WHERE status = 'VISIBLE')                                       AS visible,
    COUNT(*) FILTER (WHERE status = 'HIDDEN')                                        AS hidden,
    COUNT(*) FILTER (WHERE status = 'EXCLUDED')                                      AS excluded,
    ROUND(AVG(rating) FILTER (WHERE status IN ('SUBMITTED', 'VISIBLE')), 2)          AS avg_rating,
    COUNT(*) FILTER (WHERE rating = 5 AND status IN ('SUBMITTED', 'VISIBLE'))        AS five_star,
    COUNT(*) FILTER (WHERE rating = 4 AND status IN ('SUBMITTED', 'VISIBLE'))        AS four_star,
    COUNT(*) FILTER (WHERE rating = 3 AND status IN ('SUBMITTED', 'VISIBLE'))        AS three_star,
    COUNT(*) FILTER (WHERE rating <= 2 AND status IN ('SUBMITTED', 'VISIBLE'))       AS low_rating
FROM reviews;

-- 2.7 用户统计
CREATE OR REPLACE VIEW v_user_stats AS
SELECT
    COUNT(*)                                                                      AS total_users,
    COUNT(*) FILTER (WHERE account_status = 'ACTIVE')                            AS active_users,
    COUNT(*) FILTER (WHERE account_status = 'LOCKED')                            AS locked_users,
    COUNT(*) FILTER (WHERE account_status = 'DISABLED')                          AS disabled_users,
    COUNT(*) FILTER (WHERE created_at >= date_trunc('month', now()))             AS new_this_month,
    COUNT(*) FILTER (WHERE created_at >= date_trunc('day', now()))               AS new_today
FROM users;

-- 2.8 校园认证统计（含待审核队列量，用于后台 badge 提醒）
CREATE OR REPLACE VIEW v_campus_auth_stats AS
SELECT
    COUNT(*)                                                   AS total_verifications,
    COUNT(*) FILTER (WHERE status = 'DRAFT')                   AS draft,
    COUNT(*) FILTER (WHERE status = 'ACCUMULATING')            AS accumulating,
    COUNT(*) FILTER (WHERE status = 'PENDING_REVIEW')          AS pending_review,
    COUNT(*) FILTER (WHERE status = 'APPROVED')                AS approved,
    COUNT(*) FILTER (WHERE status = 'REJECTED')                AS rejected,
    COUNT(*) FILTER (WHERE status = 'INVALID')                 AS invalid
FROM campus_auths;

-- 2.9 操作日志统计（用于后台安全审计概览）
CREATE OR REPLACE VIEW v_operation_log_stats AS
SELECT
    COUNT(*)                                              AS total_logs,
    COUNT(*) FILTER (WHERE result = 'SUCCESS')            AS success_count,
    COUNT(*) FILTER (WHERE result = 'FAILURE')            AS failure_count,
    COUNT(*) FILTER (WHERE result = 'PARTIAL')            AS partial_count,
    COUNT(*) FILTER (WHERE created_at >= date_trunc('day', now()))    AS today_count,
    COUNT(*) FILTER (WHERE created_at >= date_trunc('month', now()))  AS this_month_count
FROM operation_logs;


-- =============================================================
-- Part 3: 后台分页查询辅助索引
--   原则：只补不破，IF NOT EXISTS 防重复执行
-- =============================================================

-- 订单管理后台：按状态 + 时间筛选分页
CREATE INDEX IF NOT EXISTS idx_trade_orders_status_created
    ON trade_orders (status, created_at DESC);

-- 支付管理后台：按状态 + 时间筛选分页
CREATE INDEX IF NOT EXISTS idx_payment_orders_status_created
    ON payment_orders (status, created_at DESC);

-- 结算管理后台：按状态 + 时间筛选分页
CREATE INDEX IF NOT EXISTS idx_settlement_records_status_created
    ON settlement_records (status, created_at DESC);

-- 评价管理后台：按状态 + 提交时间筛选分页
CREATE INDEX IF NOT EXISTS idx_reviews_status_submitted
    ON reviews (status, submitted_at DESC);

-- 评价管理后台：按被评价用户查找（信用摘要 N3 预留）
CREATE INDEX IF NOT EXISTS idx_reviews_reviewed_user_status
    ON reviews (reviewed_user_id, status, submitted_at DESC);

-- 敏感访问日志管理后台：按时间翻页（补现有索引，现有只有 target+time 和 admin+time）
CREATE INDEX IF NOT EXISTS idx_sensitive_access_logs_created
    ON sensitive_access_logs (created_at DESC);
