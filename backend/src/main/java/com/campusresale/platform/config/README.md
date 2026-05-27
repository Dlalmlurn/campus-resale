# platform/config 文件说明

本目录放 Spring Boot 项目配置。

| 文件 | 说明 |
| --- | --- |
| `CampusResaleProperties.java` | 读取 `application.yml` 中 `campus-resale.*` 配置。 |
| `WebConfig.java` | 配置 CORS，并注册 CSRF 和权限拦截器。 |

当前重点：

- `WebConfig` 允许前端跨域携带 Cookie 调用 `/api/**`。
- `OriginCsrfInterceptor` 和 `AuthorizationInterceptor` 在这里注册到 MVC 请求链。
