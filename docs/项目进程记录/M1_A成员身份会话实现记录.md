# M1 A 成员身份会话实现记录

记录日期：2026-05-27

## 范围

本记录对应 A 成员负责的身份、会话、权限基础能力，主要覆盖：

- `/api/auth/register`
- `/api/auth/login`
- `/api/auth/logout`
- `/api/auth/me`
- Cookie session 登录态
- 角色权限注解
- M1 CSRF 最低防护
- 演示账号种子数据

## 当前实现

登录态使用 `CR_SESSION` HttpOnly Cookie。浏览器保存真实 session token，数据库 `user_sessions.session_token_hash` 只保存 `SHA-256(token)`。

session 有两个过期时间：

- 闲置过期：默认 7 天不使用就过期。
- 绝对过期：最长 30 天后必须重新登录。

普通访问接口不会轮换 token，只会更新 `last_active_at` 和 `expires_at`。

## 用户名规则

用户名规则已经统一为：

- 长度 3 到 20 位。
- 只允许字母、数字和下划线。
- 不允许空格和其他符号。
- 服务端统一转小写入库。

Java 层允许用户输入大小写字母；数据库层最终只接受小写字母、数字和下划线。

对应位置：

- `AuthRequests.java`：请求体验证。
- `AuthService.java`：业务层验证。
- `V5__tighten_username_rules.sql`：数据库最终约束。

## 权限与安全

请求链路：

1. `SessionAuthenticationFilter` 读取 `CR_SESSION` Cookie 并识别当前用户。
2. `OriginCsrfInterceptor` 对非 `GET/HEAD/OPTIONS` 请求校验 `Origin` 或 `Referer`。
3. `AuthorizationInterceptor` 根据 `@RequireLogin` 和 `@RequireRole` 校验登录和角色。
4. Controller 执行业务逻辑。

M1 CSRF 防护只防普通跨站请求伪造，不防用户终端被控制或本站 XSS。

## 管理员多端策略

- `SUPER_ADMIN`：单端登录。新登录成功后撤销旧 session。
- `CONTENT_ADMIN`：允许多端登录，方便小组联调、实验调试和课程演示。

## canTrade 过渡规则

B 成员认证表合并前，A 分支暂时按角色推导交易权限：

- 拥有 `VERIFIED_STUDENT`：`verificationStatus = APPROVED`，`canTrade = true`。
- 没有 `VERIFIED_STUDENT`：`verificationStatus = NONE`，`canTrade = false`。

B 成员认证表合并后，需要恢复完整规则：

- 认证分数 `score >= 60`。
- 至少一项 `STUDENT_CARD` 或 `CAMPUS_CARD` 因子通过。
- 认证状态为 `APPROVED`。
- 用户拥有 `VERIFIED_STUDENT` 角色。

## 演示账号

统一密码：`520zikejiang`

| 用户名 | 角色 | 用途 |
| --- | --- | --- |
| `content_admin` | `CONTENT_ADMIN` | 日常内容审核，允许多端登录。 |
| `super_admin` | `SUPER_ADMIN` | 系统级管理员，只允许单端登录。 |
| `student_demo` | `REGISTERED_USER`, `VERIFIED_STUDENT` | 已认证学生演示账号。 |
| `user_demo` | `REGISTERED_USER` | 普通注册用户演示账号。 |

## 文档一致性

本实现需要同时保持以下文件一致：

- `README.md`
- `短期并行推进计划.md`
- `docs/阶段契约/m1_contracts.md`
- `docs/项目进程记录/M1_A成员身份会话实现记录.md`
- `backend/src/main/java/com/campusresale/identity/README.md`
- `backend/src/main/resources/db/migration/README.md`

## 验收建议

后端单元测试：

```powershell
mvn test
```

用户名规则建议验证：

- `abc`：允许。
- `abc_2026`：允许。
- `ab`：拒绝。
- `abcdefghijklmnopqrstu`：拒绝，超过 20 位。
- `abc def`：拒绝，包含空格。
- `abc-def`：拒绝，包含非下划线符号。

登录态建议验证：

- 登录成功响应包含 `Set-Cookie: CR_SESSION=...; HttpOnly; SameSite=Lax`。
- `GET /api/auth/me` 未登录返回 `401 AUTH_REQUIRED`。
- `student_demo` 登录后 `canTrade = true`。
- `super_admin` 第二次登录后第一次登录的 Cookie 失效。
- `content_admin` 两次登录后的两个 Cookie 都可用。
