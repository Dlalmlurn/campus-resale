-- ========================================================================
-- V20: AI 审核提醒通知（通知类型扩展）
-- ========================================================================
-- 设计目标：
--   1. 在通知体系中新增 'AI_REVIEW_REMINDER' 消息类型，
--      打通"AI 风险扫描 → 合规提醒 → 卖家自查 → 人工审核"的闭环。
--   2. 当 V19 的 AI 辅助扫描检测到 HIGH 或 BLOCK 级别风险时，
--      系统主动推送 AI_REVIEW_REMINDER 通知给卖家，
--      提醒其自查内容、修改潜在违规部分后再提交审核。
--   3. 通知体系作为贯穿交易全链路的横向能力，每种通知类型对应
--      一个特定的业务事件，确保用户不会错过关键的合规提示。
-- ========================================================================

-- ------------------------------------------------------------------------
-- 通知类型枚举扩展
-- 原有类型：ORDER_CREATED, PAYMENT_ESCROWED, REPORT_SUBMITTED 等
-- 新增类型：AI_REVIEW_REMINDER, MESSAGE_RECEIVED, BARGAIN 系列
-- ------------------------------------------------------------------------
-- 设计要点：
--   - 必须先 DROP 旧约束再 ADD 新约束，因为 CHECK 约束不支持 ALTER 修改枚举值
--   - 新增类型按功能分组：
--     · AI_REVIEW_REMINDER：AI 辅助扫描后提醒用户自查修改（本版本核心）
--     · MESSAGE_RECEIVED：即时通讯消息通知
--     · BARGAIN_OFFERED / BARGAIN_ACCEPTED / BARGAIN_REJECTED：砍价状态通知
--   - AI_REVIEW_REMINDER 与 V19 的 ai_assist_records 形成数据闭环：
--     ai_assist_records.risk_level >= HIGH → 触发推送 AI_REVIEW_REMINDER
--     → 卖家收到通知 → 修改内容 → 重新提交审核
-- ------------------------------------------------------------------------

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS ck_notifications_type;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_type CHECK (type IN (
        -- === 订单生命周期 ===
        'ORDER_CREATED',              -- 买家下单，通知卖家
        'ORDER_SELLER_CONFIRMED',     -- 卖家确认接单，通知买家

        -- === 支付 ===
        'PAYMENT_ESCROWED',           -- 买家付款已托管，通知双方

        -- === 完成确认与结算 ===
        'COMPLETION_REQUESTED',       -- 卖家发起完成确认，通知买家
        'ORDER_COMPLETED',            -- 交易完成，通知双方
        'SETTLEMENT_STATUS_CHANGED',  -- 结算状态变更

        -- === 治理（举报/申诉/退款/处罚）===
        'REPORT_SUBMITTED',           -- 举报已提交
        'REPORT_RESOLVED',            -- 举报已处理
        'APPEAL_REVIEWED',            -- 申诉已审核
        'REFUND_STATUS_CHANGED',      -- 退款状态变更
        'PENALTY_APPLIED',            -- 处罚已执行

        -- === AI 合规提醒（V20 新增）===
        'AI_REVIEW_REMINDER',         -- AI 扫描发现风险，提醒卖家自查修改

        -- === 即时通讯 ===
        'MESSAGE_RECEIVED',           -- 收到新消息（系统消息/即时消息）

        -- === 砍价 ===
        'BARGAIN_OFFERED',            -- 对方发送砍价
        'BARGAIN_ACCEPTED',           -- 对方接受砍价
        'BARGAIN_REJECTED'            -- 对方拒绝砍价
    ));