-- ========================================================================
-- V21: 演示数据补强（buyer_demo / seller_demo 账号 + 演示商品）
-- ========================================================================
-- 版本定位：
--   收尾阶段演示数据补强，补齐前端 DEV 演示切换条使用的 buyer_demo /
--   seller_demo 两个演示账号。这两个账号在 App.tsx 演示切换条和
--   App.test.tsx 中已被引用，但此前缺少种子数据，导致演示买卖双方
--   闭环无法直接登录。本脚本补齐完整的可运行演示环境。
--
-- 补齐内容：
--   1. seller_demo / buyer_demo 两个账号本身（统一密码 520zikejiang，
--      仅保存 BCrypt hash，不存明文密码）。
--   2. 赋予 REGISTERED_USER + VERIFIED_STUDENT 角色，使 canTrade
--      校园交易资格规则可通过。
--   3. APPROVED 状态的校园认证（campus_auths）+ 四因子认证
--      （campus_auth_factors），满足总分 >= 60 的完整交易资格判定。
--   4. seller_demo 名下两件 ON_SALE + APPROVED 状态商品：
--      科学计算器（DIGITAL 分类）+ 考研单词书（BOOKS 分类），
--      使 buyer_demo 可演示从浏览到下单的完整闭环。
--
-- 幂等设计：
--   全脚本使用 ON CONFLICT DO UPDATE / DO NOTHING 策略，
--   可在已存在数据的数据库上安全重复执行，不会产生重复数据或报错。
-- ========================================================================

-- ========================================================================
-- 第一阶段：创建 buyer_demo 和 seller_demo 用户账号
-- ========================================================================
-- 设计要点：
--   - 使用 WITH 子句定义种子数据，使 SQL 更清晰可读
--   - password_hash 存储的是 BCrypt 哈希值（原文密码：520zikejiang）
--   - ON CONFLICT (username) DO UPDATE：如果账号已存在则更新信息
--     （不破坏现有数据），如果不存在则插入
--   - 账号状态设为 ACTIVE，确保可以直接登录
-- ========================================================================

WITH demo_users(username, nickname, personal_email, password_hash) AS (
    VALUES
        ('seller_demo', '小林同学', 'seller-demo@example.test',
         -- BCrypt hash of '520zikejiang'
         '$2a$10$/sXyKydn5IawlQ7Yac/qXe.yBVtIfYsDIXzxSIhpj7eAX9DTHaWj2'),
        ('buyer_demo', '买家同学', 'buyer-demo@example.test',
         -- BCrypt hash of '520zikejiang'
         '$2a$10$/sXyKydn5IawlQ7Yac/qXe.yBVtIfYsDIXzxSIhpj7eAX9DTHaWj2')
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

-- ========================================================================
-- 第二阶段：赋予角色（REGISTERED_USER + VERIFIED_STUDENT）
-- ========================================================================
-- 设计要点：
--   - REGISTERED_USER：基础角色，所有注册用户均有
--   - VERIFIED_STUDENT：已通过校园认证的学生角色，满足 canTrade 规则
--   - ON CONFLICT (user_id, role_id) DO NOTHING：
--     如果角色已经存在，跳过不报错（幂等）
-- ========================================================================

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

-- ========================================================================
-- 第三阶段：创建 APPROVED 状态的校园认证记录
-- ========================================================================
-- 设计要点：
--   - score = 100，远超过 60 分的阈值，确保交易资格规则通过
--   - status = 'APPROVED'，表示认证已被管理员审核通过
--   - reviewed_by_admin_id 关联到 content_admin（如果存在），
--     使用 LEFT JOIN 确保即使 admin 不存在也能正常插入
--   - identity_claim_key：去重标识，每个用户一条唯一的认证声明
--     （格式：demo:用户名:学号）
--   - ON CONFLICT (user_id) DO UPDATE：幂等更新已有认证信息
-- ========================================================================

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
    100,                                    -- 满分 100，满足 >= 60 阈值
    'APPROVED',
    admin.id,
    now(),
    seed.identity_claim_key,
    now(),
    now()
FROM (
    VALUES
        ('seller_demo', '林小卖', '20260011',
         'seller_demo@example.edu', 'demo:seller_demo:20260011'),
        ('buyer_demo', '陈小买', '20260031',
         'buyer_demo@example.edu', 'demo:buyer_demo:20260031')
) AS seed(username, real_name, student_no, campus_email, identity_claim_key)
JOIN users student ON student.username = seed.username
LEFT JOIN users admin ON admin.username = 'content_admin'  -- 审核管理员
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

-- ========================================================================
-- 第四阶段：创建校园认证因子（4 因子，总分 100）
-- ========================================================================
-- 认证因子体系说明：
--   校园认证采用"多因子加权计分"模式，每个因子经管理员审核后获得
--   独立的状态（VERIFIED / REJECTED）和分值。总分 >= 60 分即可
--   获得完整交易资格（canTrade = true）。
--
-- 四个认证因子：
--   | 因子类型        | 分值 | 说明                   |
--   |-----------------|------|------------------------|
--   | NAME_STUDENT_NO | 40   | 姓名 + 学号验证        |
--   | DEPARTMENT      | 10   | 院系信息               |
--   | CAMPUS_EMAIL    | 10   | 校园邮箱（.edu 结尾）  |
--   | STUDENT_CARD    | 40   | 学生证/校园卡照片      |
--   | 总计            | 100  | 满足 >= 60 阈值        |
--
-- 设计要点：
--   - CROSS JOIN 将两个演示账号的认证记录与 4 个因子做笛卡尔积，
--     一次性生成 2×4=8 条因子记录
--   - 所有因子状态设为 'VERIFIED'，确保计分生效
--   - submitted_value 根据因子类型动态生成：
--     · NAME_STUDENT_NO：真实姓名|学号 格式
--     · STUDENT_CARD：占位文本 'demo-approved-material'
--   - ON CONFLICT (campus_auth_id, factor_type) DO UPDATE：幂等
-- ========================================================================

WITH auths AS (
    -- 获取两个演示账号的校园认证记录
    SELECT
        ca.id AS campus_auth_id,
        ca.real_name,
        ca.student_no,
        ca.campus_email,
        admin.id AS admin_id
    FROM campus_auths ca
    JOIN users student ON student.id = ca.user_id
    LEFT JOIN users admin ON admin.username = 'content_admin'
    WHERE student.username IN ('seller_demo', 'buyer_demo')
),
demo_factors(factor_type, score_value) AS (
    VALUES
        ('NAME_STUDENT_NO', 40),   -- 姓名学号验证（权重最高）
        ('DEPARTMENT', 10),         -- 院系信息
        ('CAMPUS_EMAIL', 10),       -- 校园邮箱
        ('STUDENT_CARD', 40)        -- 学生证照片（权重最高）
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
    'VERIFIED',                                     -- 所有因子均审核通过
    CASE demo_factors.factor_type
        WHEN 'NAME_STUDENT_NO' THEN auths.real_name || '|' || auths.student_no
        WHEN 'DEPARTMENT' THEN '计算机学院'
        WHEN 'CAMPUS_EMAIL' THEN auths.campus_email
        ELSE 'demo-approved-material'               -- STUDENT_CARD 占位
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

-- ========================================================================
-- 第五阶段：为 seller_demo 创建演示商品
-- ========================================================================
-- 两个演示商品：
--   商品 1：科学计算器 FX-991（DIGITAL 分类，¥65.00，图书馆门口）
--   商品 2：考研英语词汇书（BOOKS 分类，¥28.00，学生服务中心）
--
-- 每个商品的创建分为 4 个子步骤（使用 WITH CTE 链式串联）：
--   Step 1 - file_row：在 stored_files 表中创建一个占位文件记录
--   Step 2 - inserted_goods：在 goods 表中插入商品记录
--   Step 3 - target_goods：确定目标商品 ID（兼容已存在的情况）
--   Step 4 - linked：在 goods_images 表中建立商品与文件的关联
--
-- 幂等策略：
--   - stored_files 使用 ON CONFLICT DO UPDATE
--   - goods 使用 WHERE NOT EXISTS 防止重复插入
--   - goods_images 使用 ON CONFLICT DO UPDATE
-- ========================================================================

-- ------------------------------------------------------------------------
-- 演示商品 1：科学计算器 FX-991
--   分类：DIGITAL（数码电子）
--   成色：LIKE_NEW（几乎全新）
--   价格：¥65.00
--   地点：主校区 - 图书馆门口
--   状态：ON_SALE（在售）+ APPROVED（审核通过）
-- ------------------------------------------------------------------------
WITH seller AS (
    SELECT id FROM users WHERE username = 'seller_demo'
),
category AS (
    SELECT id FROM categories WHERE code = 'DIGITAL'
),
place AS (
    SELECT id FROM campus_places
    WHERE campus = '主校区' AND name = '图书馆门口'
),

-- Step 1: 创建占位文件记录
--   storage_key = 'seed/goods-placeholder/calculator.png'
--   byte_size = 0 表示占位文件，不实际存储
--   audit_status = 'APPROVED' 表示文件已通过审核
--   visibility_scope = 'PUBLIC' 表示文件可公网访问
file_row AS (
    INSERT INTO stored_files (
        storage_bucket, storage_key, original_name, content_type, byte_size,
        checksum, file_kind, visibility_scope, owner_user_id, business_type,
        audit_status, created_at
    )
    SELECT
        'campus-resale-dev',
        'seed/goods-placeholder/calculator.png',
        'calculator-placeholder.png',
        'image/png',
        0,                              -- 占位文件，不消耗实际存储
        'seed-calculator-placeholder',
        'GOODS_IMAGE',                  -- 文件用途：商品图片
        'PUBLIC',                       -- 公开可见
        seller.id,
        'GOODS',
        'APPROVED',
        now()
    FROM seller
    ON CONFLICT (storage_bucket, storage_key) DO UPDATE
    SET original_name = EXCLUDED.original_name,
        content_type = EXCLUDED.content_type,
        file_kind = EXCLUDED.file_kind,
        visibility_scope = 'PUBLIC',
        audit_status = 'APPROVED'
    RETURNING id
),

-- Step 2: 插入商品记录
--   WHERE NOT EXISTS 确保幂等：同一卖家名下不能有同标题的重复商品
inserted_goods AS (
    INSERT INTO goods (
        seller_id, category_id, title, description, condition_level, list_price,
        trade_place_id, trade_place_detail, available_time_text, status, audit_status,
        published_at, created_at, updated_at
    )
    SELECT
        seller.id, category.id, '科学计算器 FX-991',
        '理工科课程常用科学计算器，按键手感正常，含原装电池，可现场试用。',
        'LIKE_NEW',                     -- 几乎全新成色
        65.00,                          -- 价格 ¥65.00
        place.id, '图书馆门口', '工作日晚上',
        'ON_SALE',                      -- 商品状态：在售
        'APPROVED',                     -- 审核状态：已通过
        now(), now(), now()
    FROM seller, category, place
    WHERE NOT EXISTS (
        SELECT 1 FROM goods g
        WHERE g.seller_id = seller.id AND g.title = '科学计算器 FX-991'
    )
    RETURNING id
),

-- Step 3: 确定目标商品 ID
--   如果本次 INSERT 成功则使用新 ID，否则查询已存在的商品 ID
target_goods AS (
    SELECT id FROM inserted_goods
    UNION
    SELECT g.id FROM goods g JOIN seller ON seller.id = g.seller_id
    WHERE g.title = '科学计算器 FX-991'
    LIMIT 1
),

-- Step 4: 建立商品-图片关联
--   sort_order = 0（第一张图片）
--   is_primary = TRUE（主图）
linked AS (
    INSERT INTO goods_images (goods_id, file_id, sort_order, is_primary, created_at)
    SELECT target_goods.id, file_row.id, 0, TRUE, now()
    FROM target_goods, file_row
    ON CONFLICT (goods_id, file_id) DO UPDATE
    SET sort_order = EXCLUDED.sort_order,
        is_primary = EXCLUDED.is_primary
    RETURNING goods_id
)

-- 回写 stored_files.business_id，建立文件→商品的反向引用
UPDATE stored_files
SET business_id = (SELECT id FROM target_goods)
WHERE id = (SELECT id FROM file_row);

-- ------------------------------------------------------------------------
-- 演示商品 2：考研英语词汇书
--   分类：BOOKS（图书教材）
--   成色：LIGHTLY_USED（轻微使用）
--   价格：¥28.00
--   地点：主校区 - 学生服务中心
--   状态：ON_SALE（在售）+ APPROVED（审核通过）
--   创建逻辑与商品 1 完全一致，仅参数不同
-- ------------------------------------------------------------------------
WITH seller AS (
    SELECT id FROM users WHERE username = 'seller_demo'
),
category AS (
    SELECT id FROM categories WHERE code = 'BOOKS'
),
place AS (
    SELECT id FROM campus_places
    WHERE campus = '主校区' AND name = '学生服务中心'
),

-- Step 1: 创建占位文件记录
file_row AS (
    INSERT INTO stored_files (
        storage_bucket, storage_key, original_name, content_type, byte_size,
        checksum, file_kind, visibility_scope, owner_user_id, business_type,
        audit_status, created_at
    )
    SELECT
        'campus-resale-dev',
        'seed/goods-placeholder/english-book.png',
        'english-book-placeholder.png',
        'image/png',
        0,
        'seed-english-book-placeholder',
        'GOODS_IMAGE',
        'PUBLIC',
        seller.id,
        'GOODS',
        'APPROVED',
        now()
    FROM seller
    ON CONFLICT (storage_bucket, storage_key) DO UPDATE
    SET original_name = EXCLUDED.original_name,
        content_type = EXCLUDED.content_type,
        file_kind = EXCLUDED.file_kind,
        visibility_scope = 'PUBLIC',
        audit_status = 'APPROVED'
    RETURNING id
),

-- Step 2: 插入商品记录
inserted_goods AS (
    INSERT INTO goods (
        seller_id, category_id, title, description, condition_level, list_price,
        trade_place_id, trade_place_detail, available_time_text, status, audit_status,
        published_at, created_at, updated_at
    )
    SELECT
        seller.id, category.id, '考研英语词汇书',
        '考研英语高频词汇书，笔记清晰、无缺页，适合英语一英语二复习使用。',
        'LIGHTLY_USED',                -- 轻微使用痕迹
        28.00,                          -- 价格 ¥28.00
        place.id, '学生服务中心', '午休或傍晚',
        'ON_SALE',
        'APPROVED',
        now(), now(), now()
    FROM seller, category, place
    WHERE NOT EXISTS (
        SELECT 1 FROM goods g
        WHERE g.seller_id = seller.id AND g.title = '考研英语词汇书'
    )
    RETURNING id
),

-- Step 3: 确定目标商品 ID
target_goods AS (
    SELECT id FROM inserted_goods
    UNION
    SELECT g.id FROM goods g JOIN seller ON seller.id = g.seller_id
    WHERE g.title = '考研英语词汇书'
    LIMIT 1
),

-- Step 4: 建立商品-图片关联
linked AS (
    INSERT INTO goods_images (goods_id, file_id, sort_order, is_primary, created_at)
    SELECT target_goods.id, file_row.id, 0, TRUE, now()
    FROM target_goods, file_row
    ON CONFLICT (goods_id, file_id) DO UPDATE
    SET sort_order = EXCLUDED.sort_order,
        is_primary = EXCLUDED.is_primary
    RETURNING goods_id
)

-- 回写 stored_files.business_id
UPDATE stored_files
SET business_id = (SELECT id FROM target_goods)
WHERE id = (SELECT id FROM file_row);

-- ========================================================================
-- 第六阶段：回写 stored_files 的业务关联信息（最终一致性保障）
-- ========================================================================
-- 设计要点：
--   - 在所有商品和图片关系建立完成后，统一回写 stored_files 表中
--     business_type 和 business_id 字段
--   - 这确保了文件系统能够反向追溯"这个文件属于哪个商品"
--   - 仅处理本脚本中创建的占位文件，避免影响其他文件记录
-- ========================================================================

UPDATE stored_files sf
SET business_type = 'GOODS',
    business_id = gi.goods_id
FROM goods_images gi
WHERE sf.id = gi.file_id
  AND sf.storage_key IN (
      'seed/goods-placeholder/calculator.png',
      'seed/goods-placeholder/english-book.png'
  );