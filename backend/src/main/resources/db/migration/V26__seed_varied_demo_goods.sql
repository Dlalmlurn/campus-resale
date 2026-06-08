-- ========================================================================
-- V26: 差异化演示商品种子（无实拍图，统一占位图）
-- ========================================================================
-- 取代 V24 被 V25 删除的机械数据。这里用一池真实校园二手品类 × 成色变体，
-- 生成文字差异化、价格/成色/时间各异的公开在售商品，便于演示搜索、价格排序和分页。
-- 不插入 stored_files / goods_images：商品没有实拍图，前端统一回退到
-- public/goods-placeholder.svg（随 git 同步，全员一致、无外链、无死链）。
-- 全部挂在 seller_demo 名下；trade_place_detail 标记 '差异化演示数据' 便于将来定向清理。
-- ========================================================================

WITH seller AS (
    SELECT id FROM users WHERE username = 'seller_demo'
),
base(name, category_code, base_price, descr) AS (VALUES
    ('罗技 M590 静音无线鼠标', 'DIGITAL', 75, '静音微动，附 2.4G 接收器，宿舍办公都顺手。'),
    ('小米蓝牙耳机 Air2 SE', 'DIGITAL', 99, '入耳舒适，续航够用，适合上课通勤。'),
    ('机械键盘 87 键 红轴', 'DIGITAL', 159, '紧凑布局，红轴手感顺滑，带键线。'),
    ('Kindle Paperwhite 第十代', 'DIGITAL', 480, '墨水屏护眼，背光均匀，看书利器。'),
    ('索尼 WH-1000XM3 头戴耳机', 'DIGITAL', 850, '降噪出色，自习馆神器，配收纳盒。'),
    ('iPad 第 9 代 64G WiFi', 'DIGITAL', 1680, '记笔记看网课都够用，无磕碰。'),
    ('充电宝 20000mAh 双口快充', 'DIGITAL', 89, '容量大，双口输出，出行常备。'),
    ('罗技 C270 高清摄像头', 'DIGITAL', 79, '网课答辩可用，免驱即插即用。'),
    ('桌面显示器增高支架', 'DIGITAL', 45, '抬高视线，下方可收纳键盘。'),
    ('USB 分线器 4 口 3.0', 'DIGITAL', 35, '扩展接口，传输稳定。'),
    ('《高等数学》同济第七版 上册', 'BOOKS', 28, '少量笔记，重点已划，考试复习够用。'),
    ('《线性代数》工程数学 第六版', 'BOOKS', 22, '内页干净，无缺页。'),
    ('《大学英语综合教程》第三册', 'BOOKS', 18, '附光盘，课文勾画清晰。'),
    ('考研英语真题解析 2014-2024', 'BOOKS', 55, '历年真题加解析，刷题首选。'),
    ('《概率论与数理统计》浙大第四版', 'BOOKS', 25, '经典教材，公式推导完整。'),
    ('《数据结构》严蔚敏 C 语言版', 'BOOKS', 30, '考研 408 必备，配套习题解析。'),
    ('《计算机网络》谢希仁 第八版', 'BOOKS', 32, '内容新，无水渍。'),
    ('《有机化学》邢其毅 第四版', 'BOOKS', 36, '化学专业教材，保存完好。'),
    ('408 计算机考研复习全书', 'BOOKS', 60, '四门合订，重难点梳理清楚。'),
    ('雅思真题 17-18 套装', 'BOOKS', 48, '听力音频齐全，备考实用。'),
    ('宿舍护眼小台灯 USB 供电', 'DAILY', 26, '三档调光，夹式省桌面空间。'),
    ('折叠晾衣架 阳台两用', 'DAILY', 33, '可折叠收纳，承重稳。'),
    ('保温杯 500ml 不锈钢', 'DAILY', 39, '保温持久，密封不漏。'),
    ('桌面抽屉式收纳盒', 'DAILY', 29, '分格收纳，桌面更整洁。'),
    ('静音加湿器 大容量', 'DAILY', 59, '夜间静音，干燥季实用。'),
    ('快烧电热水壶 1.5L', 'DAILY', 49, '烧水快，自动断电。'),
    ('宿舍门后挂式全身镜', 'DAILY', 42, '免打孔，出门整理仪容。'),
    ('大号带盖收纳箱', 'DAILY', 38, '换季衣物收纳，结实耐用。'),
    ('李宁羽毛球拍 全碳素', 'SPORTS', 120, '手感轻，已缠手胶，附拍套。'),
    ('瑜伽垫 加厚防滑 183cm', 'SPORTS', 45, '回弹好，附绑带。'),
    ('计数钢丝跳绳', 'SPORTS', 19, '可调长度，轴承顺滑。'),
    ('七号室外耐磨篮球', 'SPORTS', 68, '抓握好，气足。'),
    ('可调节哑铃 5kg 一对', 'SPORTS', 88, '宿舍健身，重量可换。'),
    ('入门级公路自行车', 'SPORTS', 760, '通勤代步，车况良好，可试骑。'),
    ('户外登山包 40L', 'SPORTS', 95, '分仓合理，背负舒适。'),
    ('优衣库摇粒绒外套 M 码', 'CLOTHING', 70, '保暖轻便，无破损。'),
    ('帆布双肩包 大容量', 'CLOTHING', 55, '能装电脑书本，肩带加厚。'),
    ('跑步运动鞋 42 码', 'CLOTHING', 99, '缓震舒适，鞋底磨损少。'),
    ('轻薄羽绒服 男款 L', 'CLOTHING', 130, '充绒足，可机洗收纳。'),
    ('直筒牛仔裤 31 码', 'CLOTHING', 58, '版型百搭，弹力舒适。'),
    ('可调节遮阳棒球帽', 'CLOTHING', 32, '透气挡光，男女通用。')
),
variant(suffix, cond, factor, note) AS (VALUES
    ('九成新', 'LIKE_NEW', 0.90, '外观九成新，功能完好。'),
    ('仅拆封', 'NEW', 1.15, '仅拆封验货，几乎全新。'),
    ('使用半年', 'LIGHTLY_USED', 0.75, '使用约半年，正常使用痕迹。'),
    ('有使用痕迹', 'NOTICEABLY_USED', 0.60, '有明显使用痕迹，不影响正常使用。'),
    ('全套配件', 'LIKE_NEW', 1.00, '原包装与配件齐全。'),
    ('毕业诚出', 'LIGHTLY_USED', 0.80, '毕业清仓，诚心出，可小刀。')
),
places AS (
    SELECT id, row_number() OVER (ORDER BY sort_order, id) AS rn, count(*) OVER () AS total
    FROM campus_places WHERE enabled = TRUE
),
combined AS (
    SELECT
        row_number() OVER (ORDER BY base.name, variant.suffix) AS seq,
        base.name, base.category_code, base.descr,
        variant.suffix, variant.cond, variant.note,
        round((base.base_price * variant.factor)::numeric, 2) AS price
    FROM base CROSS JOIN variant
)
INSERT INTO goods (
    seller_id, category_id, title, description, condition_level, list_price,
    trade_place_id, trade_place_detail, available_time_text,
    status, audit_status, published_at, created_at, updated_at
)
SELECT
    seller.id,
    c.id,
    combined.name || ' · ' || combined.suffix,
    combined.descr || ' ' || combined.note,
    combined.cond,
    combined.price,
    p.id,
    '差异化演示数据',
    CASE combined.seq % 3 WHEN 0 THEN '工作日傍晚' WHEN 1 THEN '午休时间' ELSE '周末下午' END,
    'ON_SALE',
    'APPROVED',
    now() - (combined.seq || ' minutes')::interval,
    now() - (combined.seq || ' minutes')::interval,
    now() - (combined.seq || ' minutes')::interval
FROM combined
CROSS JOIN seller
JOIN categories c ON c.code = combined.category_code
JOIN places p ON p.rn = ((combined.seq - 1) % p.total) + 1
WHERE NOT EXISTS (
    SELECT 1 FROM goods g
    WHERE g.seller_id = seller.id
      AND g.title = combined.name || ' · ' || combined.suffix
);
