-- A 成员 Auth 种子数据：为本地开发、课程演示和前端联调准备固定测试账号。
-- 演示账号统一密码：520zikejiang。这里只保存 BCrypt hash，不保存明文密码。
-- SUPER_ADMIN 用于演示单端登录；CONTENT_ADMIN 按用户要求允许多端登录。

WITH demo_users(username, nickname, personal_email, password_hash) AS (
    VALUES
        ('content_admin', '内容管理员', 'content-admin@example.test', '$2a$10$/sXyKydn5IawlQ7Yac/qXe.yBVtIfYsDIXzxSIhpj7eAX9DTHaWj2'),
        ('super_admin', '超级管理员', 'super-admin@example.test', '$2a$10$/sXyKydn5IawlQ7Yac/qXe.yBVtIfYsDIXzxSIhpj7eAX9DTHaWj2'),
        ('student_demo', '认证学生演示账号', 'student-demo@example.test', '$2a$10$/sXyKydn5IawlQ7Yac/qXe.yBVtIfYsDIXzxSIhpj7eAX9DTHaWj2'),
        ('user_demo', '普通用户演示账号', 'user-demo@example.test', '$2a$10$/sXyKydn5IawlQ7Yac/qXe.yBVtIfYsDIXzxSIhpj7eAX9DTHaWj2')
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
        ('content_admin', 'CONTENT_ADMIN'),
        ('super_admin', 'SUPER_ADMIN'),
        ('student_demo', 'REGISTERED_USER'),
        ('student_demo', 'VERIFIED_STUDENT'),
        ('user_demo', 'REGISTERED_USER')
)
INSERT INTO user_roles (user_id, role_id, assigned_at)
SELECT u.id, r.id, now()
FROM role_bindings rb
JOIN users u ON u.username = rb.username
JOIN roles r ON r.code = rb.role_code
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO system_configs (config_key, config_value, value_type, description)
VALUES
    ('auth.m1_can_trade_fallback', 'VERIFIED_STUDENT_ROLE', 'STRING', 'M1 A 分支过渡逻辑：B 认证表合并前，CurrentUser.canTrade 暂按 VERIFIED_STUDENT 角色推导。'),
    ('auth.super_admin_single_session', 'true', 'BOOLEAN', '只有 SUPER_ADMIN 执行单端登录；CONTENT_ADMIN 为课程演示和联调允许多端。')
ON CONFLICT (config_key) DO UPDATE
SET config_value = EXCLUDED.config_value,
    value_type = EXCLUDED.value_type,
    description = EXCLUDED.description,
    updated_at = now();
