INSERT INTO system_configs (config_key, config_value, value_type, description)
VALUES
    ('campus.auth.email_suffixes', '["example.edu"]', 'JSON', 'M1 校园邮箱后缀白名单；B 成员认证流程先按后缀匹配，不发送真实邮件验证链接。'),
    ('campus.auth.document_max_count', '2', 'NUMBER', '单次校园认证最多绑定的学生证或校园卡材料数量。'),
    ('campus.auth.material_max_mb', '5', 'NUMBER', 'M1 校园认证材料单文件大小上限，单位 MB。'),
    ('campus.auth.factor_resubmit_limit_24h', '3', 'NUMBER', '同一认证因子 24 小时内最多重提次数。')
ON CONFLICT (config_key) DO UPDATE
SET config_value = EXCLUDED.config_value,
    value_type = EXCLUDED.value_type,
    description = EXCLUDED.description,
    updated_at = now();

INSERT INTO campus_auths (
    user_id,
    real_name,
    student_no,
    department,
    campus_email,
    score,
    status,
    reviewed_by_admin_id,
    reviewed_at,
    identity_claim_key,
    created_at,
    updated_at
)
SELECT
    student.id,
    '演示学生',
    '20260000',
    '计算机学院',
    'student_demo@example.edu',
    100,
    'APPROVED',
    admin.id,
    now(),
    'demo:student_demo:20260000',
    now(),
    now()
FROM users student
LEFT JOIN users admin ON admin.username = 'content_admin'
WHERE student.username = 'student_demo'
ON CONFLICT (user_id) DO UPDATE
SET real_name = EXCLUDED.real_name,
    student_no = EXCLUDED.student_no,
    department = EXCLUDED.department,
    campus_email = EXCLUDED.campus_email,
    score = EXCLUDED.score,
    status = EXCLUDED.status,
    reviewed_by_admin_id = EXCLUDED.reviewed_by_admin_id,
    reviewed_at = EXCLUDED.reviewed_at,
    failure_reason = NULL,
    identity_claim_key = EXCLUDED.identity_claim_key,
    updated_at = now();

WITH auth AS (
    SELECT ca.id AS campus_auth_id, admin.id AS admin_id
    FROM campus_auths ca
    JOIN users student ON student.id = ca.user_id
    LEFT JOIN users admin ON admin.username = 'content_admin'
    WHERE student.username = 'student_demo'
),
demo_factors(factor_type, score_value, submitted_value) AS (
    VALUES
        ('NAME_STUDENT_NO', 40, '演示学生|20260000'),
        ('DEPARTMENT', 10, '计算机学院'),
        ('CAMPUS_EMAIL', 10, 'student_demo@example.edu'),
        ('STUDENT_CARD', 40, 'demo-approved-material')
)
INSERT INTO campus_auth_factors (
    campus_auth_id,
    factor_type,
    score_value,
    status,
    submitted_value,
    reviewed_by_admin_id,
    reviewed_at,
    created_at,
    updated_at
)
SELECT
    auth.campus_auth_id,
    demo_factors.factor_type,
    demo_factors.score_value,
    'VERIFIED',
    demo_factors.submitted_value,
    auth.admin_id,
    now(),
    now(),
    now()
FROM auth
CROSS JOIN demo_factors
ON CONFLICT (campus_auth_id, factor_type) DO UPDATE
SET score_value = EXCLUDED.score_value,
    status = EXCLUDED.status,
    submitted_value = EXCLUDED.submitted_value,
    reviewed_by_admin_id = EXCLUDED.reviewed_by_admin_id,
    reviewed_at = EXCLUDED.reviewed_at,
    rejected_reason = NULL,
    updated_at = now();
