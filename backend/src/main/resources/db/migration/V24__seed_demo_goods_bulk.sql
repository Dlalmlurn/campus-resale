-- ========================================================================
-- V24: 批量演示商品种子数据
-- ========================================================================
-- 文件作用：为商品搜索、价格排序和分页演示补齐 1000 条公开在售商品。
-- 数据全部挂在 seller_demo 名下，使用可重复的编号、分类、成色、地点和价格
-- 规则生成，避免破坏 V21 中 buyer_demo / seller_demo 的下单闭环演示商品。
-- ========================================================================

WITH seller AS (
    SELECT id FROM users WHERE username = 'seller_demo'
),
category_pool AS (
    SELECT
        id,
        code,
        row_number() OVER (ORDER BY sort_order, id) AS rn,
        count(*) OVER () AS total
    FROM categories
    WHERE enabled = TRUE
      AND prohibited_flag = FALSE
),
place_pool AS (
    SELECT
        id,
        row_number() OVER (ORDER BY sort_order, id) AS rn,
        count(*) OVER () AS total
    FROM campus_places
    WHERE enabled = TRUE
),
seed AS (
    SELECT
        gs AS seq,
        seller.id AS seller_id,
        category_pool.id AS category_id,
        category_pool.code AS category_code,
        place_pool.id AS place_id,
        CASE (gs % 4)
            WHEN 0 THEN 'NEW'
            WHEN 1 THEN 'LIKE_NEW'
            WHEN 2 THEN 'LIGHTLY_USED'
            ELSE 'NOTICEABLY_USED'
        END AS condition_level,
        (8 + (gs % 240) * 3.5)::numeric(12,2) AS list_price
    FROM generate_series(1, 1000) AS gs
    CROSS JOIN seller
    JOIN category_pool
      ON category_pool.rn = ((gs - 1) % category_pool.total) + 1
    JOIN place_pool
      ON place_pool.rn = ((gs - 1) % place_pool.total) + 1
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
        seed.seller_id,
        seed.category_id,
        '演示商品 ' || lpad(seed.seq::text, 4, '0') || ' · ' || seed.category_code,
        '用于搜索、筛选、价格排序和分页压测的公开演示商品，编号 ' || seed.seq || '，支持按分类、成色、地点和价格区间检索。',
        seed.condition_level,
        seed.list_price,
        seed.place_id,
        '批量演示数据',
        CASE seed.seq % 3
            WHEN 0 THEN '工作日傍晚'
            WHEN 1 THEN '午休时间'
            ELSE '周末下午'
        END,
        'ON_SALE',
        'APPROVED',
        now() - (seed.seq || ' minutes')::interval,
        now() - (seed.seq || ' minutes')::interval,
        now() - (seed.seq || ' minutes')::interval
    FROM seed
    WHERE NOT EXISTS (
        SELECT 1
        FROM goods existing
        WHERE existing.seller_id = seed.seller_id
          AND existing.title = '演示商品 ' || lpad(seed.seq::text, 4, '0') || ' · ' || seed.category_code
    )
    RETURNING id, seller_id
),
file_rows AS (
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
        business_id,
        audit_status,
        created_at
    )
    SELECT
        'campus-resale-dev',
        'seed/goods-bulk/' || inserted_goods.id || '.png',
        'bulk-demo-' || inserted_goods.id || '.png',
        'image/png',
        0,
        'seed-bulk-goods-' || inserted_goods.id,
        'GOODS_IMAGE',
        'PUBLIC',
        inserted_goods.seller_id,
        'GOODS',
        inserted_goods.id,
        'APPROVED',
        now()
    FROM inserted_goods
    ON CONFLICT (storage_bucket, storage_key) DO UPDATE
    SET visibility_scope = 'PUBLIC',
        business_type = 'GOODS',
        business_id = EXCLUDED.business_id,
        audit_status = 'APPROVED'
    RETURNING id, business_id
)
INSERT INTO goods_images (goods_id, file_id, sort_order, is_primary, created_at)
SELECT file_rows.business_id, file_rows.id, 0, TRUE, now()
FROM file_rows
ON CONFLICT (goods_id, file_id) DO UPDATE
SET sort_order = EXCLUDED.sort_order,
    is_primary = EXCLUDED.is_primary;
