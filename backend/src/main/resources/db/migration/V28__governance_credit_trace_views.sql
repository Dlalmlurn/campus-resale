-- 文件功能：补充治理与信用追踪视图，让管理员能按用户或举报串起举报、处罚、申诉和信用影响。

ALTER TABLE reports
    DROP CONSTRAINT IF EXISTS ck_reports_target_type;

ALTER TABLE reports
    ADD CONSTRAINT ck_reports_target_type
    CHECK (target_type IN ('GOODS', 'ORDER', 'USER', 'MESSAGE', 'COMMENT'));

ALTER TABLE credit_records
    DROP CONSTRAINT IF EXISTS ck_credit_records_source_type;

ALTER TABLE credit_records
    ADD CONSTRAINT ck_credit_records_source_type
    CHECK (source_type IN ('ORDER', 'REVIEW', 'REPORT', 'PENALTY', 'APPEAL', 'REFUND', 'MANUAL'));

CREATE INDEX IF NOT EXISTS idx_reports_target_created
    ON reports (target_type, target_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_credit_records_source
    ON credit_records (source_type, source_id);

-- 举报本身只存 target_type/target_id；视图把这些目标解析成可查的用户关系。
CREATE OR REPLACE VIEW v_admin_report_user_trace AS
SELECT
    r.id AS report_id,
    r.reporter_id,
    reporter.nickname AS reporter_nickname,
    r.target_type,
    r.target_id,
    related.related_user_id,
    related_user.nickname AS related_user_nickname,
    related.related_user_role,
    r.reason_type,
    r.description,
    r.status,
    r.priority,
    r.handled_by_admin_id,
    r.handled_at,
    r.handling_note,
    p.id AS penalty_id,
    p.penalty_type,
    p.status AS penalty_status,
    a.id AS appeal_id,
    a.status AS appeal_status,
    r.created_at
FROM reports r
JOIN users reporter ON reporter.id = r.reporter_id
JOIN LATERAL (
    SELECT r.reporter_id AS related_user_id, 'REPORTER'::varchar AS related_user_role
    UNION ALL
    SELECT u.id, 'REPORTED_USER'
    FROM users u
    WHERE r.target_type = 'USER'
      AND u.id = r.target_id
    UNION ALL
    SELECT g.seller_id, 'GOODS_SELLER'
    FROM goods g
    WHERE r.target_type = 'GOODS'
      AND g.id = r.target_id
    UNION ALL
    SELECT o.buyer_id, 'ORDER_BUYER'
    FROM trade_orders o
    WHERE r.target_type = 'ORDER'
      AND o.id = r.target_id
    UNION ALL
    SELECT o.seller_id, 'ORDER_SELLER'
    FROM trade_orders o
    WHERE r.target_type = 'ORDER'
      AND o.id = r.target_id
    UNION ALL
    SELECT m.sender_id, 'MESSAGE_SENDER'
    FROM messages m
    WHERE r.target_type = 'MESSAGE'
      AND m.id = r.target_id
      AND m.sender_id IS NOT NULL
    UNION ALL
    SELECT c.buyer_id, 'MESSAGE_BUYER'
    FROM messages m
    JOIN conversations c ON c.id = m.conversation_id
    WHERE r.target_type = 'MESSAGE'
      AND m.id = r.target_id
    UNION ALL
    SELECT c.seller_id, 'MESSAGE_SELLER'
    FROM messages m
    JOIN conversations c ON c.id = m.conversation_id
    WHERE r.target_type = 'MESSAGE'
      AND m.id = r.target_id
) related ON TRUE
JOIN users related_user ON related_user.id = related.related_user_id
LEFT JOIN penalty_records p ON p.report_id = r.id
LEFT JOIN appeals a ON a.report_id = r.id
WHERE related.related_user_id IS NOT NULL;

-- 信用流水保留 source_type/source_id；视图给每条来源补上后台可读标签。
CREATE OR REPLACE VIEW v_admin_user_credit_trace AS
SELECT
    cr.id AS credit_record_id,
    cr.user_id,
    u.nickname AS user_nickname,
    cr.source_type,
    cr.source_id,
    CASE cr.source_type
        WHEN 'ORDER' THEN concat('订单 #', cr.source_id)
        WHEN 'REVIEW' THEN concat('评价 #', cr.source_id)
        WHEN 'REPORT' THEN concat('举报 #', cr.source_id)
        WHEN 'PENALTY' THEN concat('处罚 #', cr.source_id)
        WHEN 'APPEAL' THEN concat('申诉 #', cr.source_id)
        WHEN 'REFUND' THEN concat('退款 #', cr.source_id)
        ELSE concat('人工修正 #', COALESCE(cr.source_id::text, '-'))
    END AS source_label,
    cr.reason,
    cr.internal_delta_value,
    cr.internal_level_before,
    cr.internal_level_after,
    cr.public_label,
    cr.created_by_admin_id,
    cr.created_at,
    r.id AS report_id,
    p.id AS penalty_id,
    a.id AS appeal_id
FROM credit_records cr
JOIN users u ON u.id = cr.user_id
LEFT JOIN reports r ON cr.source_type = 'REPORT' AND r.id = cr.source_id
LEFT JOIN penalty_records p ON cr.source_type IN ('PENALTY', 'MANUAL') AND p.id = cr.source_id
LEFT JOIN appeals a ON cr.source_type = 'APPEAL' AND a.id = cr.source_id;
