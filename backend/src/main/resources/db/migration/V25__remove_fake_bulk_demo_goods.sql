-- ========================================================================
-- V25: 清理 V24 批量演示商品（消除图片死链与机械文案）
-- ========================================================================
-- 背景：V24 用 generate_series 造了 1000 条 "演示商品 NNNN" 商品，并为每条
-- 插入了 storage_key = 'seed/goods-bulk/{id}.png' 的 stored_files 行，但 MinIO
-- 里从未写入对应对象。市场页渲染时每张图都请求 /api/files/{id}/content → 404，
-- 后端刷屏 "文件不存在或不可见" WARN（MinioObjectStorageClient）。
--
-- 处理（注意外键安全）：联调期间有人对部分批量商品下过单，trade_orders.goods_id /
-- conversations.goods_id 都不是 ON DELETE CASCADE，直接删 goods 会触发外键报错。
-- 因此分三步：
--   1) 先摘掉所有批量演示商品的图片关联（无论是否被订单引用），消除死链；
--   2) 删除死链 stored_files（此时已无 goods_images 引用）；
--   3) 只删除"没有被订单/会话引用"的批量演示商品，被引用的极少数保留，避免破坏
--      订单/会话历史（它们已无图片，前端回退统一占位图，也不再刷 404）。
--
-- 真实演示数据由 V26 用"统一占位图 + 文字差异化"补充。
-- 精确定位 V24 数据：seller_demo 名下、标题以 "演示商品 " 开头、且
-- trade_place_detail = '批量演示数据'（V24 专用标记），不会误删 V21 的闭环演示商品。
-- ========================================================================

-- 1) 摘除批量演示商品的图片关联
DELETE FROM goods_images gi
USING goods g, users u
WHERE gi.goods_id = g.id
  AND g.seller_id = u.id
  AND u.username = 'seller_demo'
  AND g.title LIKE '演示商品 %'
  AND g.trade_place_detail = '批量演示数据';

-- 2) 删除死链文件行（已无 goods_images 引用）
DELETE FROM stored_files
WHERE storage_bucket = 'campus-resale-dev'
  AND storage_key LIKE 'seed/goods-bulk/%';

-- 3) 删除未被订单/会话引用的批量演示商品（goods_tags / 收藏随 ON DELETE CASCADE 一并清理）
DELETE FROM goods g
USING users u
WHERE g.seller_id = u.id
  AND u.username = 'seller_demo'
  AND g.title LIKE '演示商品 %'
  AND g.trade_place_detail = '批量演示数据'
  AND NOT EXISTS (SELECT 1 FROM trade_orders o WHERE o.goods_id = g.id)
  AND NOT EXISTS (SELECT 1 FROM conversations c WHERE c.goods_id = g.id);
