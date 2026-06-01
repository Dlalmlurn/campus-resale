CREATE INDEX idx_goods_public_list
    ON goods (status, audit_status, is_deleted, published_at DESC, id DESC);

CREATE INDEX idx_goods_seller_status_created
    ON goods (seller_id, status, audit_status, created_at DESC, id DESC);

CREATE INDEX idx_goods_category_public
    ON goods (category_id, status, audit_status, is_deleted);

CREATE INDEX idx_goods_place_public
    ON goods (trade_place_id, status, audit_status, is_deleted);

CREATE INDEX idx_goods_price
    ON goods (list_price);

CREATE INDEX idx_goods_search_vector
    ON goods USING GIN (search_vector);

CREATE INDEX idx_goods_title_trgm
    ON goods USING GIN (title gin_trgm_ops);

CREATE INDEX idx_goods_description_trgm
    ON goods USING GIN (description gin_trgm_ops);

INSERT INTO forbidden_terms (term, term_type, severity, enabled, created_by_admin_id, created_at, updated_at)
SELECT seed.term, seed.term_type, seed.severity, TRUE, admin.id, now(), now()
FROM (
    VALUES
        ('考试答案', 'KEYWORD', 'BLOCK'),
        ('校园卡套现', 'KEYWORD', 'BLOCK'),
        ('违禁药品', 'KEYWORD', 'BLOCK')
) AS seed(term, term_type, severity)
LEFT JOIN users admin ON admin.username = 'super_admin'
ON CONFLICT (term) DO UPDATE
SET term_type = EXCLUDED.term_type,
    severity = EXCLUDED.severity,
    enabled = TRUE,
    updated_at = now();

WITH seller AS (
    SELECT id FROM users WHERE username = 'student_demo'
),
category AS (
    SELECT id FROM categories WHERE code = 'DIGITAL'
),
place AS (
    SELECT id FROM campus_places WHERE campus = '主校区' AND name = '图书馆门口'
),
file_row AS (
    INSERT INTO stored_files (
        storage_bucket,
        storage_key,
        original_name,
        content_type,
        byte_size,
        checksum,
        file_kind,
        visibility_scope,
        owner_user_id,
        business_type,
        audit_status,
        created_at
    )
    SELECT
        'campus-resale-dev',
        'seed/goods-placeholder/monitor.png',
        'monitor-placeholder.png',
        'image/png',
        0,
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
inserted_goods AS (
    INSERT INTO goods (
        seller_id,
        category_id,
        title,
        description,
        condition_level,
        list_price,
        trade_place_id,
        trade_place_detail,
        available_time_text,
        status,
        audit_status,
        published_at,
        created_at,
        updated_at
    )
    SELECT
        seller.id,
        category.id,
        '九成新显示器',
        '自用显示器，配件齐全，适合宿舍和实验室使用。',
        'LIKE_NEW',
        399.00,
        place.id,
        '图书馆门口',
        '工作日晚上',
        'ON_SALE',
        'APPROVED',
        now(),
        now(),
        now()
    FROM seller, category, place
    WHERE NOT EXISTS (
        SELECT 1 FROM goods g WHERE g.seller_id = seller.id AND g.title = '九成新显示器'
    )
    RETURNING id
),
target_goods AS (
    SELECT id FROM inserted_goods
    UNION
    SELECT g.id
    FROM goods g
    JOIN seller ON seller.id = g.seller_id
    WHERE g.title = '九成新显示器'
    LIMIT 1
),
linked AS (
    INSERT INTO goods_images (goods_id, file_id, sort_order, is_primary, created_at)
    SELECT target_goods.id, file_row.id, 0, TRUE, now()
    FROM target_goods, file_row
    ON CONFLICT (goods_id, file_id) DO UPDATE
    SET sort_order = EXCLUDED.sort_order,
        is_primary = EXCLUDED.is_primary
    RETURNING goods_id
)
UPDATE stored_files
SET business_id = (SELECT id FROM target_goods)
WHERE id = (SELECT id FROM file_row);

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
        storage_bucket,
        storage_key,
        original_name,
        content_type,
        byte_size,
        checksum,
        file_kind,
        visibility_scope,
        owner_user_id,
        business_type,
        audit_status,
        created_at
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
        seller_id,
        category_id,
        title,
        description,
        condition_level,
        list_price,
        trade_place_id,
        trade_place_detail,
        available_time_text,
        status,
        audit_status,
        published_at,
        created_at,
        updated_at
    )
    SELECT
        seller.id,
        category.id,
        '数据库课程教材',
        '数据库系统概论教材，少量划线，适合课程复习和实验参考。',
        'LIGHTLY_USED',
        35.00,
        place.id,
        '学生服务中心',
        '午休或傍晚',
        'ON_SALE',
        'APPROVED',
        now(),
        now(),
        now()
    FROM seller, category, place
    WHERE NOT EXISTS (
        SELECT 1 FROM goods g WHERE g.seller_id = seller.id AND g.title = '数据库课程教材'
    )
    RETURNING id
),
target_goods AS (
    SELECT id FROM inserted_goods
    UNION
    SELECT g.id
    FROM goods g
    JOIN seller ON seller.id = g.seller_id
    WHERE g.title = '数据库课程教材'
    LIMIT 1
),
linked AS (
    INSERT INTO goods_images (goods_id, file_id, sort_order, is_primary, created_at)
    SELECT target_goods.id, file_row.id, 0, TRUE, now()
    FROM target_goods, file_row
    ON CONFLICT (goods_id, file_id) DO UPDATE
    SET sort_order = EXCLUDED.sort_order,
        is_primary = EXCLUDED.is_primary
    RETURNING goods_id
)
UPDATE stored_files
SET business_id = (SELECT id FROM target_goods)
WHERE id = (SELECT id FROM file_row);

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
        storage_bucket,
        storage_key,
        original_name,
        content_type,
        byte_size,
        checksum,
        file_kind,
        visibility_scope,
        owner_user_id,
        business_type,
        audit_status,
        created_at
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
        seller_id,
        category_id,
        title,
        description,
        condition_level,
        list_price,
        trade_place_id,
        trade_place_detail,
        available_time_text,
        status,
        audit_status,
        published_at,
        created_at,
        updated_at
    )
    SELECT
        seller.id,
        category.id,
        '入门羽毛球拍',
        '轻量羽毛球拍一支，线和手胶状态良好，适合体育课使用。',
        'LIGHTLY_USED',
        58.00,
        place.id,
        '体育馆入口',
        '周末下午',
        'ON_SALE',
        'APPROVED',
        now(),
        now(),
        now()
    FROM seller, category, place
    WHERE NOT EXISTS (
        SELECT 1 FROM goods g WHERE g.seller_id = seller.id AND g.title = '入门羽毛球拍'
    )
    RETURNING id
),
target_goods AS (
    SELECT id FROM inserted_goods
    UNION
    SELECT g.id
    FROM goods g
    JOIN seller ON seller.id = g.seller_id
    WHERE g.title = '入门羽毛球拍'
    LIMIT 1
),
linked AS (
    INSERT INTO goods_images (goods_id, file_id, sort_order, is_primary, created_at)
    SELECT target_goods.id, file_row.id, 0, TRUE, now()
    FROM target_goods, file_row
    ON CONFLICT (goods_id, file_id) DO UPDATE
    SET sort_order = EXCLUDED.sort_order,
        is_primary = EXCLUDED.is_primary
    RETURNING goods_id
)
UPDATE stored_files
SET business_id = (SELECT id FROM target_goods)
WHERE id = (SELECT id FROM file_row);

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
        storage_bucket,
        storage_key,
        original_name,
        content_type,
        byte_size,
        checksum,
        file_kind,
        visibility_scope,
        owner_user_id,
        business_type,
        audit_status,
        created_at
    )
    SELECT
        'campus-resale-dev',
        'seed/goods-placeholder/lamp.png',
        'lamp-placeholder.png',
        'image/png',
        0,
        'seed-lamp-placeholder',
        'GOODS_IMAGE',
        'PRIVATE',
        seller.id,
        'GOODS',
        'PENDING',
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
        seller_id,
        category_id,
        title,
        description,
        condition_level,
        list_price,
        trade_place_id,
        trade_place_detail,
        available_time_text,
        status,
        audit_status,
        created_at,
        updated_at
    )
    SELECT
        seller.id,
        category.id,
        '宿舍护眼台灯',
        '白色台灯亮度可调，适合晚上自习使用，等待管理员审核。',
        'LIKE_NEW',
        45.00,
        place.id,
        '南门',
        '工作日中午',
        'PENDING_REVIEW',
        'PENDING',
        now(),
        now()
    FROM seller, category, place
    WHERE NOT EXISTS (
        SELECT 1 FROM goods g WHERE g.seller_id = seller.id AND g.title = '宿舍护眼台灯'
    )
    RETURNING id
),
target_goods AS (
    SELECT id FROM inserted_goods
    UNION
    SELECT g.id
    FROM goods g
    JOIN seller ON seller.id = g.seller_id
    WHERE g.title = '宿舍护眼台灯'
    LIMIT 1
),
linked AS (
    INSERT INTO goods_images (goods_id, file_id, sort_order, is_primary, created_at)
    SELECT target_goods.id, file_row.id, 0, TRUE, now()
    FROM target_goods, file_row
    ON CONFLICT (goods_id, file_id) DO UPDATE
    SET sort_order = EXCLUDED.sort_order,
        is_primary = EXCLUDED.is_primary
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
  AND sf.storage_key LIKE 'seed/goods-placeholder/%';
