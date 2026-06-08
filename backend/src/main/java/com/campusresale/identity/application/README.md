# identity/application 文件说明

本目录放身份模块的业务流程。Controller 不直接写复杂逻辑，而是调用这里的服务。

| 文件 | 说明 |
| --- | --- |
| `AuthService.java` | 注册、登录、退出、创建 session、超级管理员单端登录策略。 |
| `SessionLookupService.java` | 根据 Cookie token 加载当前用户，并执行 session 滑动续期。 |
| `CurrentUserMapper.java` | 把用户或当前主体转换成前端 `CurrentUser` 响应。 |
| `PasswordService.java` | 封装 BCrypt 密码哈希和密码校验。 |
| `SessionTokenGenerator.java` | 生成高随机性真实 token，并计算数据库 token hash。 |
| `AuthResult.java` | 登录/注册成功后的组合结果，包含用户响应和真实 token。 |
| `SessionToken.java` | 保存 raw token 与 token hash 的小对象。 |

关键约定：

- `AuthService` 是注册和登录的主入口。
- `SessionLookupService` 是普通请求恢复登录态的主入口。
- `CurrentUserMapper` 通过 `CampusTradeEligibilityResolver` 读取真实校园认证状态和交易权限。

后续扩展：

- 如果后续要展示登录设备列表，优先扩展 `SessionLookupService` 和 `UserSessionRepository`。
