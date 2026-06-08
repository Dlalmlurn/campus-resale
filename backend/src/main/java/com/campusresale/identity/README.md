# identity 目录说明

本目录包含身份、登录会话、当前用户和校园认证模块。A 成员负责身份会话基础能力，B 成员在 `verification` 子目录补充校园认证能力。

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
| `verification` | 校园认证提交、分数计算、管理员审核和交易权限判断。 |

当前约定：

- 用户名规则：3 到 20 位，只允许字母、数字和下划线，服务端统一转小写入库。
- 注册用户只授予 `REGISTERED_USER`。
- `canTrade` 使用 B 成员认证表的完整规则推导，不再只按 `VERIFIED_STUDENT` 角色兜底。
- 真实 session token 只写入浏览器 Cookie，数据库只保存 SHA-256 hash。
