-- A 成员 Auth 修正迁移：收紧用户名规则到 3-20 位，只允许小写字母、数字和下划线。
-- 说明：服务端会把用户名统一转小写后入库；因此数据库正则只接受小写格式。

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS ck_users_username_length;

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS ck_users_username_pattern;

ALTER TABLE users
    ADD CONSTRAINT ck_users_username_length
    CHECK (char_length(username) BETWEEN 3 AND 20);

ALTER TABLE users
    ADD CONSTRAINT ck_users_username_pattern
    CHECK (username ~ '^[a-z0-9_]{3,20}$');
