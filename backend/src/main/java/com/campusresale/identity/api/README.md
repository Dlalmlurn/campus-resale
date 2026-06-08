# identity/api 文件说明

本目录放身份模块暴露给前端的 HTTP 接口和数据结构。

| 文件 | 说明 |
| --- | --- |
| `AuthController.java` | 实现注册、登录、退出、查询当前用户四个接口。 |
| `AuthRequests.java` | 定义注册和登录请求体，并声明字段校验规则。 |
| `CurrentUserResponse.java` | 定义登录、注册、`/api/auth/me` 返回的当前用户结构。 |
| `LogoutResponse.java` | 定义退出登录接口的 `{ "ok": true }` 响应。 |

阅读顺序建议：

1. 先看 `AuthController.java`，理解 HTTP 请求入口。
2. 再看 `AuthRequests.java`，理解用户名、密码、昵称等入参规则。
3. 最后看响应对象，确认前端能拿到哪些字段。

扩展提示：

- 新增 Auth 相关接口时优先放在 `AuthController.java`。
- 请求体和响应体不要直接复用数据库对象，保持接口契约稳定。
