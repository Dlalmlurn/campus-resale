# identity 目录说明

本目录是 A 成员负责的身份、登录会话和当前用户模块。

请求主链路：

1. `api/AuthController.java` 接收 `/api/auth/*` HTTP 请求。
2. `application/AuthService.java` 执行注册、登录、退出和 session 创建。
3. `infrastructure/UserAccountRepository.java` 与 `UserSessionRepository.java` 读写数据库。
4. `platform/security/SessionAuthenticationFilter.java` 在普通请求中识别当前用户。

子目录职责：

| 目录 | 职责 |
| --- | --- |
| `api` | Controller、请求体和响应体，对齐接口契约。 |
| `application` | 业务流程编排，例如登录、注册、session 续期、密码校验。 |
| `domain` | 领域对象，只表达用户和 session 的核心状态。 |
| `infrastructure` | 数据库访问，封装 SQL 和 `JdbcTemplate`。 |

当前约定：

- 用户名规则：3 到 20 位，只允许字母、数字和下划线，服务端统一转小写入库。
- 注册用户只授予 `REGISTERED_USER`。
- `VERIFIED_STUDENT` 在 M1 过渡期用于推导 `canTrade = true`。
- 真实 session token 只写入浏览器 Cookie，数据库只保存 SHA-256 hash。
