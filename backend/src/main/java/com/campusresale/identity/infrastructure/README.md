# identity/infrastructure 文件说明

本目录放身份模块的数据访问代码，负责把业务对象和数据库表互相转换。

| 文件 | 说明 |
| --- | --- |
| `UserAccountRepository.java` | 读写 `users`、`roles`、`user_roles`。 |
| `UserSessionRepository.java` | 读写 `user_sessions`，包括创建、查询、续期、撤销。 |

当前数据库约定：

- `users.username` 统一保存小写用户名。
- `users.password_hash` 保存 BCrypt hash，不保存明文密码。
- `user_sessions.session_token_hash` 保存 SHA-256 hash，不保存真实 token。

扩展提示：

- 不要在 Controller 或 Service 中散写 SQL。
- 新增身份相关数据库读写时，优先放入本目录的 Repository。
