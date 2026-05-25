INSERT INTO roles (code, name, description)
VALUES
    ('REGISTERED_USER', '注册用户', '已注册但未完成完整校园认证的用户'),
    ('VERIFIED_STUDENT', '认证学生', '具备完整交易权限的校内用户'),
    ('CONTENT_ADMIN', '内容管理员', '负责认证、商品、举报和申诉等日常治理'),
    ('SUPER_ADMIN', '超级管理员', '负责系统级配置、角色和敏感审计')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description;

INSERT INTO system_configs (config_key, config_value, value_type, description)
VALUES
    ('campus.auth.review_score_threshold', '50', 'NUMBER', '可信度达到该分值进入管理员审核队列'),
    ('campus.auth.trade_score_threshold', '60', 'NUMBER', '开放完整交易权限需要达到的可信度分值'),
    ('session.idle_ttl_days', '7', 'NUMBER', '服务端会话闲置有效期天数'),
    ('session.absolute_ttl_days', '30', 'NUMBER', '服务端会话绝对有效期天数'),
    ('goods.images.max_count', '15', 'NUMBER', '单个商品最多图片数量'),
    ('ai.enabled', 'false', 'BOOLEAN', '全局 AI 发布辅助开关'),
    ('settlement.freeze_days', '7', 'NUMBER', '订单完成待结算后的冻结期天数')
ON CONFLICT (config_key) DO UPDATE
SET config_value = EXCLUDED.config_value,
    value_type = EXCLUDED.value_type,
    description = EXCLUDED.description,
    updated_at = now();

INSERT INTO campus_places (campus, name, detail, enabled, sort_order)
VALUES
    ('主校区', '图书馆门口', '适合白天面交', TRUE, 10),
    ('主校区', '学生服务中心', '靠近校园卡与认证服务窗口', TRUE, 20),
    ('主校区', '南门', '校外交通便利', TRUE, 30),
    ('主校区', '体育馆入口', '晚间人流较多', TRUE, 40)
ON CONFLICT (campus, name) DO UPDATE
SET detail = EXCLUDED.detail,
    enabled = EXCLUDED.enabled,
    sort_order = EXCLUDED.sort_order;

INSERT INTO categories (code, name, sort_order)
VALUES
    ('DIGITAL', '数码电子', 10),
    ('BOOKS', '教材书籍', 20),
    ('DAILY', '生活用品', 30),
    ('SPORTS', '运动户外', 40),
    ('CLOTHING', '服饰鞋包', 50)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    sort_order = EXCLUDED.sort_order,
    enabled = TRUE;

INSERT INTO tags (name, description)
VALUES
    ('可小刀', '卖家接受小幅议价'),
    ('自提优先', '卖家倾向校内自提'),
    ('配件齐全', '商品包含主要原配附件'),
    ('急出', '卖家希望尽快完成交易')
ON CONFLICT (name) DO UPDATE
SET description = EXCLUDED.description,
    enabled = TRUE;
