# identity/domain 文件说明

本目录放身份模块的领域对象。领域对象只表达核心状态，不直接关心 HTTP 和 SQL。

| 文件 | 说明 |
| --- | --- |
| `UserAccount.java` | 用户账号对象，包含 id、用户名、密码 hash、昵称、状态、角色。 |
| `UserSessionRecord.java` | 服务端 session 对象，包含 token hash、过期时间和撤销状态。 |

设计原则：

- 领域对象保持简单，避免引入 Controller、Repository 依赖。
- 判断自身状态的方法可以放在对象内，例如 `isActive()`、`isUsableAt()`。
