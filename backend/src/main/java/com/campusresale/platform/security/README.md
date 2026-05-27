# platform/security 文件说明

本目录放跨模块复用的安全基础设施，身份模块和后续业务模块都会使用。

| 文件 | 说明 |
| --- | --- |
| `SessionAuthenticationFilter.java` | 请求刚进入后端时读取 `CR_SESSION` Cookie，识别当前用户。 |
| `AuthorizationInterceptor.java` | 进入 Controller 前读取权限注解，校验登录和角色。 |
| `OriginCsrfInterceptor.java` | 对非 GET/HEAD/OPTIONS 请求做 `Origin` / `Referer` 来源校验。 |
| `RequireLogin.java` | 标记接口必须登录。 |
| `RequireRole.java` | 标记接口必须拥有指定角色之一。 |
| `CurrentPrincipal.java` | 描述当前请求识别出的登录用户和 session 信息。 |
| `CurrentPrincipalContext.java` | 在一次请求内保存和读取当前用户。 |
| `SecurityProperties.java` | 集中保存 Cookie 名和角色名常量。 |
| `TokenHasher.java` | 计算 session token 的 SHA-256 hash。 |

请求顺序：

1. `SessionAuthenticationFilter` 尝试识别当前用户。
2. `OriginCsrfInterceptor` 校验写请求来源。
3. `AuthorizationInterceptor` 校验接口权限。
4. Controller 执行业务逻辑。

扩展提示：

- 后续业务接口需要登录时加 `@RequireLogin`。
- 管理员接口可加 `@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})`。
- 当前 CSRF 是 M1 最低防护，不防本站 XSS。
