-- ========================================================================
-- V11: 商品搜索索引 + 敏感词种子数据 + 前端联调演示商品
-- ========================================================================
-- 设计目标：
--   1. 为 V9 的 goods 表创建全套查询索引，覆盖公网列表、卖家管理、
--      分类浏览、价格排序、全文搜索、模糊匹配六大场景。
--   2. 为 V10 的 forbidden_terms 表注入 3 条 BLOCK 级种子敏感词，
--      让审核规则体系在首次部署后立即可用。
--   3. 为前端开发提供 4 件覆盖不同分类和状态的演示商品，包括：
--      3 件 ON_SALE+APPROVED（可浏览）+ 1 件 PENDING_REVIEW+PENDING
--      （待审核，公网不可见）。这些商品挂载在 student_demo 名下。
-- ========================================================================

-- ========================================================================
-- 第一部分：商品查询索引（6 个场景 × 8 个索引）
-- ========================================================================

-- ------------------------------------------------------------------------
-- 索引 1：公网商品列表查询（最常用）
-- 索引覆盖列：(status, audit_status, is_deleted, published_at, id)
-- 查询模式：WHERE status='ON_SALE' AND audit_status='APPROVED' AND is_deleted=FALSE
--          ORDER BY published_at DESC, id DESC
-- 设计要点：
--   - 复合索引前三列精准过滤公网可见条件，后两列支持高效排序
--   - published_at DESC 用于"最新发布"排序，id DESC 作为同时间戳下的稳定 tiebreaker
--   - 这是前端首页、分类页、搜索结果页的核心依赖索引
-- ------------------------------------------------------------------------
CREATE INDEX idx_goods_public_list
    ON goods (status, audit_status, is_deleted, published_at DESC, id DESC);

-- ------------------------------------------------------------------------
-- 索引 2：卖家个人中心——"我发布的商品"
-- 索引覆盖列：(seller_id, status, audit_status, created_at, id)
-- 查询模式：WHERE seller_id=? ORDER BY created_at DESC, id DESC
-- 设计要点：
--   - 第一列 seller_id 实现快速定位卖家
--   - 后两列 (status, audit_status) 支持按状态过滤（如"待审核"、"已通过"）
--   - created_at DESC 实现按发布时间倒序
-- ------------------------------------------------------------------------
CREATE INDEX idx_goods_seller_status_created
    ON goods (seller_id, status, audit_status, created_at DESC, id DESC);

-- ------------------------------------------------------------------------
-- 索引 3：按分类浏览公网商品
-- 索引覆盖列：(category_id, status, audit_status, is_deleted)
-- 查询模式：WHERE category_id=? AND status='ON_SALE' AND audit_status='APPROVED'
--           AND is_deleted=FALSE
-- 适用场景：点击"数码电子"分类 → 只展示该分类下已审核通过的在售商品
-- ------------------------------------------------------------------------
CREATE INDEX idx_goods_category_public
    ON goods (category_id, status, audit_status, is_deleted);

-- ------------------------------------------------------------------------
-- 索引 4：按交易地点浏览公网商品
-- 索引覆盖列：(trade_place_id, status, audit_status, is_deleted)
-- 查询模式：WHERE trade_place_id=? AND status='ON_SALE' AND audit_status='APPROVED'
--           AND is_deleted=FALSE
-- 适用场景：点击"图书馆门口" → 只展示该地点已审核通过的在售商品
-- ------------------------------------------------------------------------
CREATE INDEX idx_goods_place_public
    ON goods (trade_place_id, status, audit_status, is_deleted);

-- ------------------------------------------------------------------------
-- 索引 5：价格排序索引
-- 索引覆盖列：(list_price)
-- 查询模式：ORDER BY list_price ASC / DESC
-- 适用场景：用户按"价格从低到高"浏览商品列表
-- 注：不包含公网条件过滤列，因为价格排序通常是二次排序
--     主过滤仍走 idx_goods_public_list
-- ------------------------------------------------------------------------
CREATE INDEX idx_goods_price
    ON goods (list_price);

-- ------------------------------------------------------------------------
-- 索引 6：全文搜索向量 GIN 索引（核心搜索能力）
-- 索引类型：GIN (Generalized Inverted Index)
-- 索引列：search_vector（由 V9 触发器 trg_goods_refresh_search_vector 自动维护）
-- 查询模式：WHERE search_vector @@ to_tsquery('simple', '显示器')
-- 设计要点：
--   - GIN 索引专为 tsvector 设计，支持 @@ 全文匹配操作符
--   - 与 pg_trgm 索引配合使用：GIN 做精确关键词匹配，pg_trgm 做模糊/前缀匹配
--   - 不引入 Elasticsearch 等外部中间件，降低运维成本
-- ------------------------------------------------------------------------
CREATE INDEX idx_goods_search_vector
    ON goods USING GIN (search_vector);

-- ------------------------------------------------------------------------
-- 索引 7：标题模糊搜索（pg_trgm）
-- 索引类型：GIN + gin_trgm_ops
-- 索引列：title
-- 查询模式：WHERE title ILIKE '%显示器%' 或 title % '显示器'
-- 设计要点：
--   - pg_trgm（Trigram）扩展支持 LIKE/ILIKE 的索引加速
--   - 与 idx_goods_search_vector 互补：
--     · tsvector GIN：精确关键词匹配，适合"搜到即为相关"
--     · pg_trgm GIN：模糊/前缀/相似度匹配，适合"用户可能打错字"
--   - pg_trgm 扩展在 V1__foundation_schema.sql 中已启用
-- ------------------------------------------------------------------------
CREATE INDEX idx_goods_title_trgm
    ON goods USING GIN (title gin_trgm_ops);

-- ------------------------------------------------------------------------
-- 索引 8：描述模糊搜索（pg_trgm）
-- 索引类型：GIN + gin_trgm_ops
-- 索引列：description
-- 查询模式：WHERE description ILIKE '%配件齐全%'
-- 设计要点：
--   - 与 idx_goods_title_trgm 原理相同，覆盖描述字段
--   - 对于较长文本（描述最多 2000 字符），pg_trgm 索引体积较大，
--     但对于校园场景的商品量级完全可控
-- ------------------------------------------------------------------------
CREATE INDEX idx_goods_description_trgm
    ON goods USING GIN (description gin_trgm_ops);

-- ========================================================================
-- 第二部分：敏感词种子数据（2 条 BLOCK 级 + 1 条预留）
-- ========================================================================
-- 注入目标：forbidden_terms 表（V10 创建）
-- 这些种子词确保审核规则引擎在首次部署后立即有能力拦截常见的校园违规内容。
-- 管理员后续可在后台自由增删改。
-- ========================================================================

INSERT INTO forbidden_terms (term, term_type, severity, enabled, created_by_admin_id, created_at, updated_at)
SELECT seed.term, seed.term_type, seed.severity, TRUE, admin.id, now(), now()
FROM (
    VALUES
        -- 考试相关违禁（BLOCK）：买卖考试答案严重违反校规
        ('考试答案', 'KEYWORD', 'BLOCK'),

        -- 金融违规（BLOCK）：校园卡套现属于违规行为
        ('校园卡套现', 'KEYWORD', 'BLOCK'),

        -- 违禁品（BLOCK）：药品类交易涉及安全与法律风险
        ('违禁药品', 'KEYWORD', 'BLOCK')
) AS seed(term, term_type, severity)
LEFT JOIN users admin ON admin.username = 'super_admin'      -- 关联管理员（可选）
ON CONFLICT (term) DO UPDATE                                  -- 幂等：已存在则更新
SET term_type = EXCLUDED.term_type,
    severity = EXCLUDED.severity,
    enabled = TRUE,
    updated_at = now();

-- ========================================================================
-- 第三部分：前端联调演示商品（4 件，student_demo 名下）
-- ========================================================================
-- 商品清单：
--   ① 九成新显示器    DIGITAL  LIKE_NEW      ¥399   ON_SALE+APPROVED  公网可见
--   ② 数据库课程教材  BOOKS    LIGHTLY_USED  ¥35    ON_SALE+APPROVED  公网可见
--   ③ 入门羽毛球拍    SPORTS   LIGHTLY_USED  ¥58    ON_SALE+APPROVED  公网可见
--   ④ 宿舍护眼台灯    DAILY    LIKE_NEW      ¥45    PENDING_REVIEW     公网不可见
--                                                    +PENDING          （演示待审核）
--
-- 设计要点：
--   - 前 3 件覆盖 DIGITAL/BOOKS/SPORTS 三个不同分类，验证分类浏览功能
--   - 第 4 件状态为 PENDING_REVIEW+PENDING，验证"待审核商品对公网不可见"
--     的双状态机逻辑
--   - 所有商品使用占位图片（byte_size=0），不占用实际的 MinIO 存储
--   - 全脚本幂等，使用 ON CONFLICT / WHERE NOT EXISTS 策略
-- ========================================================================

-- ------------------------------------------------------------------------
-- 演示商品 ①：九成新显示器（DIGITAL，图书馆门口，¥399）
-- ------------------------------------------------------------------------
WITH seller AS (
    SELECT id FROM users WHERE username = 'student_demo'
),
category AS (
    SELECT id FROM categories WHERE code = 'DIGITAL'
),
place AS (
    SELECT id FROM campus_places WHERE campus = '主校区' AND name = '图书馆门口'
),

-- Step 1: 创建占位文件
file_row AS (
    INSERT INTO stored_files (
        storage_bucket, storage_key, original_name, content_type, byte_size,
        checksum, file_kind, visibility_scope, owner_user_id, business_type,
        audit_status, created_at
    )
    SELECT
        'campus-resale-dev',
        'seed/goods-placeholder/monitor.png',
        'monitor-placeholder.png',
        'image/png',
        0,                              -- 占位文件
        'seed-monitor-placeholder',
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

-- Step 2: 插入商品（幂等）
inserted_goods AS (
    INSERT INTO goods (
        seller_id, category_id, title, description, condition_level, list_price,
        trade_place_id, trade_place_detail, available_time_text, status, audit_status,
        published_at, created_at, updated_at
    )
    SELECT
        seller.id, category.id, '九成新显示器',
        '自用显示器，配件齐全，适合宿舍和实验室使用。',
        'LIKE_NEW', 399.00, place.id, '图书馆门口', '工作日晚上',
        'ON_SALE', 'APPROVED', now(), now(), now()
    FROM seller, category, place
    WHERE NOT EXISTS (
        SELECT 1 FROM goods g
        WHERE g.seller_id = seller.id AND g.title = '九成新显示器'
    )
    RETURNING id
),

-- Step 3: 确定目标商品 ID
target_goods AS (
    SELECT id FROM inserted_goods
    UNION
    SELECT g.id FROM goods g JOIN seller ON seller.id = g.seller_id
    WHERE g.title = '九成新显示器'
    LIMIT 1
),

-- Step 4: 建立商品-图片关联
linked AS (
    INSERT INTO goods_images (goods_id, file_id, sort_order, is_primary, created_at)
    SELECT target_goods.id, file_row.id, 0, TRUE, now()
    FROM target_goods, file_row
    ON CONFLICT (goods_id, file_id) DO UPDATE
    SET sort_order = EXCLUDED.sort_order, is_primary = EXCLUDED.is_primary
    RETURNING goods_id
)

-- 回写文件→商品的反向引用
UPDATE stored_files
SET business_id = (SELECT id FROM target_goods)
WHERE id = (SELECT id FROM file_row);

-- ------------------------------------------------------------------------
-- 演示商品 ②：数据库课程教材（BOOKS，学生服务中心，¥35）
-- ------------------------------------------------------------------------
WITH seller AS (
    SELECT id FROM users WHERE username = 'student_demo'
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
        checksum, file_kind, visibility_scope, owner_user_id, business_type,
        audit_status, created_at
    )
    SELECT
        'campus-resale-dev',
        'seed/goods-placeholder/book.png',
        'book-placeholder.png',
        'image/png',
        0,
        'seed-book-placeholder',
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
inserted_goods AS (
    INSERT INTO goods (
        seller_id, category_id, title, description, condition_level, list_price,
        trade_place_id, trade_place_detail, available_time_text, status, audit_status,
        published_at, created_at, updated_at
    )
    SELECT
        seller.id, category.id, '数据库课程教材',
        '数据库系统概论教材，少量划线，适合课程复习和实验参考。',
        'LIGHTLY_USED', 35.00, place.id, '学生服务中心', '午休或傍晚',
        'ON_SALE', 'APPROVED', now(), now(), now()
    FROM seller, category, place
    WHERE NOT EXISTS (
        SELECT 1 FROM goods g
        WHERE g.seller_id = seller.id AND g.title = '数据库课程教材'
    )
    RETURNING id
),
target_goods AS (
    SELECT id FROM inserted_goods
    UNION
    SELECT g.id FROM goods g JOIN seller ON seller.id = g.seller_id
    WHERE g.title = '数据库课程教材'
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

-- ------------------------------------------------------------------------
-- 演示商品 ③：入门羽毛球拍（SPORTS，体育馆入口，¥58）
-- ------------------------------------------------------------------------
WITH seller AS (
    SELECT id FROM users WHERE username = 'student_demo'
),
category AS (
    SELECT id FROM categories WHERE code = 'SPORTS'
),
place AS (
    SELECT id FROM campus_places WHERE campus = '主校区' AND name = '体育馆入口'
),
file_row AS (
    INSERT INTO stored_files (
        storage_bucket, storage_key, original_name, content_type, byte_size,
        checksum, file_kind, visibility_scope, owner_user_id, business_type,
        audit_status, created_at
    )
    SELECT
        'campus-resale-dev',
        'seed/goods-placeholder/racket.png',
        'racket-placeholder.png',
        'image/png',
        0,
        'seed-racket-placeholder',
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
inserted_goods AS (
    INSERT INTO goods (
        seller_id, category_id, title, description, condition_level, list_price,
        trade_place_id, trade_place_detail, available_time_text, status, audit_status,
        published_at, created_at, updated_at
    )
    SELECT
        seller.id, category.id, '入门羽毛球拍',
        '轻量羽毛球拍一支，线和手胶状态良好，适合体育课使用。',
        'LIGHTLY_USED', 58.00, place.id, '体育馆入口', '周末下午',
        'ON_SALE', 'APPROVED', now(), now(), now()
    FROM seller, category, place
    WHERE NOT EXISTS (
        SELECT 1 FROM goods g
        WHERE g.seller_id = seller.id AND g.title = '入门羽毛球拍'
    )
    RETURNING id
),
target_goods AS (
    SELECT id FROM inserted_goods
    UNION
    SELECT g.id FROM goods g JOIN seller ON seller.id = g.seller_id
    WHERE g.title = '入门羽毛球拍'
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

-- ------------------------------------------------------------------------
-- 演示商品 ④：宿舍护眼台灯（DAILY，南门，¥45）—— 待审核状态
-- 关键差异：
--   - status = 'PENDING_REVIEW'（待审核上架）
--   - audit_status = 'PENDING'（审核中）
--   - 没有 published_at（未正式发布）
--   - 这张商品的图片 visibility_scope = 'PRIVATE'（审核通过前不公开）
--   - 此商品在公网列表不可见，但卖家自己在"我的发布"中可以看到，
--     用于演示"商品提交审核后，等待管理员审核"的状态
-- ------------------------------------------------------------------------
WITH seller AS (
    SELECT id FROM users WHERE username = 'student_demo'
),
category AS (
    SELECT id FROM categories WHERE code = 'DAILY'
),
place AS (
    SELECT id FROM campus_places WHERE campus = '主校区' AND name = '南门'
),
file_row AS (
    INSERT INTO stored_files (
        storage_bucket, storage_key, original_name, content_type, byte_size,
        checksum, file_kind, visibility_scope, owner_user_id, business_type,
        audit_status, created_at
    )
    SELECT
        'campus-resale-dev',
        'seed/goods-placeholder/lamp.png',
        'lamp-placeholder.png',
        'image/png',
        0,
        'seed-lamp-placeholder',
        'GOODS_IMAGE',
        'PRIVATE',                      -- 审核通过前图片不公开
        seller.id,
        'GOODS',
        'PENDING',                      -- 文件也处于待审核状态
        now()
    FROM seller
    ON CONFLICT (storage_bucket, storage_key) DO UPDATE
    SET original_name = EXCLUDED.original_name,
        content_type = EXCLUDED.content_type,
        file_kind = EXCLUDED.file_kind,
        visibility_scope = 'PRIVATE',
        audit_status = 'PENDING'
    RETURNING id
),
inserted_goods AS (
    INSERT INTO goods (
        seller_id, category_id, title, description, condition_level, list_price,
        trade_place_id, trade_place_detail, available_time_text, status, audit_status,
        created_at, updated_at
        -- 注意：没有 published_at 字段，因为尚未正式发布
    )
    SELECT
        seller.id, category.id, '宿舍护眼台灯',
        '白色台灯亮度可调，适合晚上自习使用，等待管理员审核。',
        'LIKE_NEW', 45.00, place.id, '南门', '工作日中午',
        'PENDING_REVIEW',               -- 商品状态：等待审核
        'PENDING',                      -- 审核状态：待审核
        now(), now()
    FROM seller, category, place
    WHERE NOT EXISTS (
        SELECT 1 FROM goods g
        WHERE g.seller_id = seller.id AND g.title = '宿舍护眼台灯'
    )
    RETURNING id
),
target_goods AS (
    SELECT id FROM inserted_goods
    UNION
    SELECT g.id FROM goods g JOIN seller ON seller.id = g.seller_id
    WHERE g.title = '宿舍护眼台灯'
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

-- ========================================================================
-- 第四部分：回写 stored_files 的业务关联信息
-- ========================================================================
-- 将所有本脚本创建的占位文件统一回写 business_type 和 business_id，
-- 确保文件系统能反向追溯"这个文件属于哪个商品"。
-- 使用 LIKE 'seed/goods-placeholder/%' 批量匹配，覆盖全部 4 件商品。
-- ========================================================================
UPDATE stored_files sf
SET business_type = 'GOODS',
    business_id = gi.goods_id
FROM goods_images gi
WHERE sf.id = gi.file_id
  AND sf.storage_key LIKE 'seed/goods-placeholder/%';