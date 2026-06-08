-- 文件功能：扩展评价提交通知枚举，支撑互评后的站内通知与消息提醒。
ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS ck_notifications_type;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_type CHECK (type IN (
        'ORDER_CREATED',
        'ORDER_SELLER_CONFIRMED',
        'PAYMENT_ESCROWED',
        'COMPLETION_REQUESTED',
        'ORDER_COMPLETED',
        'REVIEW_SUBMITTED',
        'SETTLEMENT_STATUS_CHANGED',
        'REPORT_SUBMITTED',
        'REPORT_RESOLVED',
        'APPEAL_REVIEWED',
        'REFUND_STATUS_CHANGED',
        'PENALTY_APPLIED',
        'AI_REVIEW_REMINDER',
        'MESSAGE_RECEIVED',
        'BARGAIN_OFFERED',
        'BARGAIN_ACCEPTED',
        'BARGAIN_REJECTED'
    ));
