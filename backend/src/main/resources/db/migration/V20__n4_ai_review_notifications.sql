ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS ck_notifications_type;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_type CHECK (type IN (
        'ORDER_CREATED',
        'ORDER_SELLER_CONFIRMED',
        'PAYMENT_ESCROWED',
        'COMPLETION_REQUESTED',
        'ORDER_COMPLETED',
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
