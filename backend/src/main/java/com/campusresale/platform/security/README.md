# platform/security 文件说明

本目录放跨模块复用的安全基础设施，身份模块和后续业务模块都会使用。

| 文件 | 说明 |
| --- | --- |
| `SessionAuthenticationFilter.java` | 请求刚进入后端时读取 `CR_SESSION` Cookie，识别当前用户。 |
| `AuthorizationInterceptor.java` | 进入 Controller 前读取权限注解，校验登录、角色和交易资格。 |
| `OriginCsrfInterceptor.java` | 对非 GET/HEAD/OPTIONS 请求做 `Origin` / `Referer` 来源校验。 |
| `RequireLogin.java` | 标记接口必须登录。 |
| `RequireRole.java` | 标记接口必须拥有指定角色之一。 |
| `RequireTradeEligible.java` | 标记接口必须具备完整交易资格，适合 N1 订单、模拟支付和评价交易动作。 |
| `TradeEligibilityChecker.java` | 交易资格检查接口，由身份认证模块提供具体实现。 |
| `CurrentPrincipal.java` | 描述当前请求识别出的登录用户和 session 信息。 |
| `CurrentPrincipalContext.java` | 在一次请求内保存和读取当前用户。 |
| `SecurityProperties.java` | 集中保存 Cookie 名和角色名常量。 |
| `TokenHasher.java` | 计算 session token 的 SHA-256 hash。 |

请求顺序：

1. `SessionAuthenticationFilter` 尝试识别当前用户。
2. `OriginCsrfInterceptor` 校验写请求来源。
3. `AuthorizationInterceptor` 校验接口权限，包括登录、角色和交易资格。
4. Controller 执行业务逻辑。

扩展提示：

- 后续业务接口需要登录时加 `@RequireLogin`。
- 管理员接口可加 `@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})`。
- 商品发布、订单创建、模拟支付、完成确认和评价等会创建或推进交易事实的接口可加 `@RequireTradeEligible`。
- `@RequireTradeEligible` 只判断当前用户是否具备完整交易资格；是否是订单买家、卖家或评价提交人仍由业务服务层判断。
- 当前 CSRF 是 M1 最低防护，不防本站 XSS。
