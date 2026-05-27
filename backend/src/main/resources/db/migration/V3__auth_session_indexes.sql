-- A 成员 Auth 补充迁移：强化用户名规范化唯一性、session 查询索引和基础账号字段约束。
-- 可扩展点：B 成员校园认证表合并后，不要在本迁移继续追加认证字段，应新增后续 migration。

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username_lower_unique
    ON users (lower(username));

CREATE INDEX IF NOT EXISTS idx_user_sessions_token_active
    ON user_sessions (session_token_hash, expires_at, absolute_expires_at)
    WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_user_roles_role_user
    ON user_roles (role_id, user_id);

ALTER TABLE users
    ADD CONSTRAINT ck_users_username_trimmed
    CHECK (username = btrim(username));

ALTER TABLE users
    ADD CONSTRAINT ck_users_username_length
    CHECK (char_length(username) BETWEEN 3 AND 80);

ALTER TABLE users
    ADD CONSTRAINT ck_users_username_lowercase
    CHECK (username = lower(username));
