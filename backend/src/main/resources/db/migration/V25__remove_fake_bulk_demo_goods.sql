-- ========================================================================
-- V25: 清理 V24 批量演示商品（消除图片死链与机械文案）
-- ========================================================================
-- 背景：V24 用 generate_series 造了 1000 条 "演示商品 NNNN" 商品，并为每条
-- 插入了 storage_key = 'seed/goods-bulk/{id}.png' 的 stored_files 行，但 MinIO
-- 里从未写入对应对象。市场页渲染时每张图都请求 /api/files/{id}/content → 404，
-- 后端刷屏 "文件不存在或不可见" WARN（MinioObjectStorageClient）。
--
-- 处理：删除这批纯演示商品（goods 上 goods_images / goods_tags / 收藏均为
-- ON DELETE CASCADE，会一并清掉），再删除对应的死链 stored_files 行。
-- 真实演示数据改由 scripts/seed_goods 导入器（公开数据集 + 真实图片入 MinIO）补充，
-- 见 scripts/seed_goods/README.md。
--
-- 精确定位 V24 数据：seller_demo 名下、标题以 "演示商品 " 开头、且
-- trade_place_detail = '批量演示数据'（V24 专用标记），不会误删 V21 的闭环演示商品。
-- ========================================================================

DELETE FROM goods g
USING users u
WHERE g.seller_id = u.id
  AND u.username = 'seller_demo'
  AND g.title LIKE '演示商品 %'
  AND g.trade_place_detail = '批量演示数据';

DELETE FROM stored_files
WHERE storage_bucket = 'campus-resale-dev'
  AND storage_key LIKE 'seed/goods-bulk/%';
