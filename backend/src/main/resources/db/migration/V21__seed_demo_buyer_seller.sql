-- 收尾阶段演示数据补强：补齐前端 DEV 演示切换条使用的 buyer_demo / seller_demo 账号。
-- 这两个账号在 App.tsx 演示切换条和 App.test.tsx 中已被引用，但此前缺少种子数据，
-- 导致演示买卖双方闭环无法直接登录。这里补齐：
--   1. 两个账号本身（统一密码 520zikejiang，仅保存 BCrypt hash）。
--   2. REGISTERED_USER + VERIFIED_STUDENT 角色，使 canTrade 规则可通过。
--   3. APPROVED 状态的校园认证与认证因子，满足完整交易资格判定。
--   4. seller_demo 名下两件 ON_SALE 商品，使 buyer_demo 可演示完整下单闭环。
-- 全脚本幂等，可在已存在数据的库上重复执行。

WITH demo_users(username, nickname, personal_email, password_hash) AS (
    VALUES
        ('seller_demo', '小林同学', 'seller-demo@example.test', '$2a$10$/sXyKydn5IawlQ7Yac/qXe.yBVtIfYsDIXzxSIhpj7eAX9DTHaWj2'),
        ('buyer_demo', '买家同学', 'buyer-demo@example.test', '$2a$10$/sXyKydn5IawlQ7Yac/qXe.yBVtIfYsDIXzxSIhpj7eAX9DTHaWj2')
)
INSERT INTO users (username, nickname, personal_email, password_hash, account_status, created_at, updated_at)
SELECT username, nickname, personal_email, password_hash, 'ACTIVE', now(), now()
FROM demo_users
ON CONFLICT (username) DO UPDATE
SET nickname = EXCLUDED.nickname,
    personal_email = EXCLUDED.personal_email,
    password_hash = EXCLUDED.password_hash,
    account_status = 'ACTIVE',
    updated_at = now();

WITH role_bindings(username, role_code) AS (
    VALUES
        ('seller_demo', 'REGISTERED_USER'),
        ('seller_demo', 'VERIFIED_STUDENT'),
        ('buyer_demo', 'REGISTERED_USER'),
        ('buyer_demo', 'VERIFIED_STUDENT')
)
INSERT INTO user_roles (user_id, role_id, assigned_at)
SELECT u.id, r.id, now()
FROM role_bindings rb
JOIN users u ON u.username = rb.username
JOIN roles r ON r.code = rb.role_code
ON CONFLICT (user_id, role_id) DO NOTHING;

-- 两个演示账号的 APPROVED 校园认证（score=100 满足 >=60 阈值）。
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
    seed.real_name,
    seed.student_no,
    '计算机学院',
    seed.campus_email,
    100,
    'APPROVED',
    admin.id,
    now(),
    seed.identity_claim_key,
    now(),
    now()
FROM (
    VALUES
        ('seller_demo', '林小卖', '20260011', 'seller_demo@example.edu', 'demo:seller_demo:20260011'),
        ('buyer_demo', '陈小买', '20260031', 'buyer_demo@example.edu', 'demo:buyer_demo:20260031')
) AS seed(username, real_name, student_no, campus_email, identity_claim_key)
JOIN users student ON student.username = seed.username
LEFT JOIN users admin ON admin.username = 'content_admin'
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

-- 认证因子：满足"姓名学号 + 学生证因子 VERIFIED"，使完整交易资格规则通过。
WITH auths AS (
    SELECT ca.id AS campus_auth_id, ca.real_name, ca.student_no, ca.campus_email, admin.id AS admin_id
    FROM campus_auths ca
    JOIN users student ON student.id = ca.user_id
    LEFT JOIN users admin ON admin.username = 'content_admin'
    WHERE student.username IN ('seller_demo', 'buyer_demo')
),
demo_factors(factor_type, score_value) AS (
    VALUES
        ('NAME_STUDENT_NO', 40),
        ('DEPARTMENT', 10),
        ('CAMPUS_EMAIL', 10),
        ('STUDENT_CARD', 40)
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
    auths.campus_auth_id,
    demo_factors.factor_type,
    demo_factors.score_value,
    'VERIFIED',
    CASE demo_factors.factor_type
        WHEN 'NAME_STUDENT_NO' THEN auths.real_name || '|' || auths.student_no
        WHEN 'DEPARTMENT' THEN '计算机学院'
        WHEN 'CAMPUS_EMAIL' THEN auths.campus_email
        ELSE 'demo-approved-material'
    END,
    auths.admin_id,
    now(),
    now(),
    now()
FROM auths
CROSS JOIN demo_factors
ON CONFLICT (campus_auth_id, factor_type) DO UPDATE
SET score_value = EXCLUDED.score_value,
    status = EXCLUDED.status,
    submitted_value = EXCLUDED.submitted_value,
    reviewed_by_admin_id = EXCLUDED.reviewed_by_admin_id,
    reviewed_at = EXCLUDED.reviewed_at,
    rejected_reason = NULL,
    updated_at = now();

-- seller_demo 名下演示商品 1：计算器（DIGITAL，图书馆门口）。
WITH seller AS (
    SELECT id FROM users WHERE username = 'seller_demo'
),
category AS (
    SELECT id FROM categories WHERE code = 'DIGITAL'
),
place AS (
    SELECT id FROM campus_places WHERE campus = '主校区' AND name = '图书馆门口'
),
file_row AS (
    INSERT INTO stored_files (
        storage_bucket, storage_key, original_name, content_type, byte_size,
        checksum, file_kind, visibility_scope, owner_user_id, business_type, audit_status, created_at
    )
    SELECT
        'campus-resale-dev', 'seed/goods-placeholder/calculator.png', 'calculator-placeholder.png',
        'image/png', 0, 'seed-calculator-placeholder', 'GOODS_IMAGE', 'PUBLIC',
        seller.id, 'GOODS', 'APPROVED', now()
    FROM seller
    ON CONFLICT (storage_bucket, storage_key) DO UPDATE
    SET original_name = EXCLUDED.original_name,
        content_type = EXCLUDED.content_type,
        file_kind = EXCLUDED.file_kind,
        visibility_scope = 'PUBLIC',
        audit_status = 'APPROVED'
    RETURNING id
),
inserted_goods AS (
    INSERT INTO goods (
        seller_id, category_id, title, description, condition_level, list_price,
        trade_place_id, trade_place_detail, available_time_text, status, audit_status,
        published_at, created_at, updated_at
    )
    SELECT
        seller.id, category.id, '科学计算器 FX-991',
        '理工科课程常用科学计算器，按键手感正常，含原装电池，可现场试用。',
        'LIKE_NEW', 65.00, place.id, '图书馆门口', '工作日晚上',
        'ON_SALE', 'APPROVED', now(), now(), now()
    FROM seller, category, place
    WHERE NOT EXISTS (
        SELECT 1 FROM goods g WHERE g.seller_id = seller.id AND g.title = '科学计算器 FX-991'
    )
    RETURNING id
),
target_goods AS (
    SELECT id FROM inserted_goods
    UNION
    SELECT g.id FROM goods g JOIN seller ON seller.id = g.seller_id
    WHERE g.title = '科学计算器 FX-991'
    LIMIT 1
),
linked AS (
    INSERT INTO goods_images (goods_id, file_id, sort_order, is_primary, created_at)
    SELECT target_goods.id, file_row.id, 0, TRUE, now()
    FROM target_goods, file_row
    ON CONFLICT (goods_id, file_id) DO UPDATE
    SET sort_order = EXCLUDED.sort_order, is_primary = EXCLUDED.is_primary
    RETURNING goods_id
)
UPDATE stored_files
SET business_id = (SELECT id FROM target_goods)
WHERE id = (SELECT id FROM file_row);

-- seller_demo 名下演示商品 2：考研单词书（BOOKS，学生服务中心）。
WITH seller AS (
    SELECT id FROM users WHERE username = 'seller_demo'
),
category AS (
    SELECT id FROM categories WHERE code = 'BOOKS'
),
place AS (
    SELECT id FROM campus_places WHERE campus = '主校区' AND name = '学生服务中心'
),
file_row AS (
    INSERT INTO stored_files (
        storage_bucket, storage_key, original_name, content_type, byte_size,
        checksum, file_kind, visibility_scope, owner_user_id, business_type, audit_status, created_at
    )
    SELECT
        'campus-resale-dev', 'seed/goods-placeholder/english-book.png', 'english-book-placeholder.png',
        'image/png', 0, 'seed-english-book-placeholder', 'GOODS_IMAGE', 'PUBLIC',
        seller.id, 'GOODS', 'APPROVED', now()
    FROM seller
    ON CONFLICT (storage_bucket, storage_key) DO UPDATE
    SET original_name = EXCLUDED.original_name,
        content_type = EXCLUDED.content_type,
        file_kind = EXCLUDED.file_kind,
        visibility_scope = 'PUBLIC',
        audit_status = 'APPROVED'
    RETURNING id
),
inserted_goods AS (
    INSERT INTO goods (
        seller_id, category_id, title, description, condition_level, list_price,
        trade_place_id, trade_place_detail, available_time_text, status, audit_status,
        published_at, created_at, updated_at
    )
    SELECT
        seller.id, category.id, '考研英语词汇书',
        '考研英语高频词汇书，笔记清晰、无缺页，适合英语一英语二复习使用。',
        'LIGHTLY_USED', 28.00, place.id, '学生服务中心', '午休或傍晚',
        'ON_SALE', 'APPROVED', now(), now(), now()
    FROM seller, category, place
    WHERE NOT EXISTS (
        SELECT 1 FROM goods g WHERE g.seller_id = seller.id AND g.title = '考研英语词汇书'
    )
    RETURNING id
),
target_goods AS (
    SELECT id FROM inserted_goods
    UNION
    SELECT g.id FROM goods g JOIN seller ON seller.id = g.seller_id
    WHERE g.title = '考研英语词汇书'
    LIMIT 1
),
linked AS (
    INSERT INTO goods_images (goods_id, file_id, sort_order, is_primary, created_at)
    SELECT target_goods.id, file_row.id, 0, TRUE, now()
    FROM target_goods, file_row
    ON CONFLICT (goods_id, file_id) DO UPDATE
    SET sort_order = EXCLUDED.sort_order, is_primary = EXCLUDED.is_primary
    RETURNING goods_id
)
UPDATE stored_files
SET business_id = (SELECT id FROM target_goods)
WHERE id = (SELECT id FROM file_row);

UPDATE stored_files sf
SET business_type = 'GOODS',
    business_id = gi.goods_id
FROM goods_images gi
WHERE sf.id = gi.file_id
  AND sf.storage_key IN ('seed/goods-placeholder/calculator.png', 'seed/goods-placeholder/english-book.png');
