# db/migration 文件说明

本目录放 Flyway 数据库迁移脚本。脚本按文件名前缀版本号顺序执行，已经执行过的旧 migration 不应再修改内容，否则其他成员本地数据库可能出现校验冲突。

| 文件 | 说明 |
| --- | --- |
| `V1__foundation_schema.sql` | M0 基础表结构。 |
| `V2__seed_foundation_data.sql` | M0 基础角色、配置、地点、分类、标签种子数据。 |
| `V3__auth_session_indexes.sql` | A 成员身份会话补充索引和初始用户名约束。 |
| `V4__seed_auth_demo_users.sql` | A 成员演示账号和角色绑定。 |
| `V5__tighten_username_rules.sql` | A 成员修正用户名规则：3 到 20 位，只允许小写字母、数字和下划线。 |

用户名规则说明：

- Java 层允许用户输入大小写字母、数字和下划线。
- Java 层会统一转小写后入库。
- 数据库层最终由 `V5` 约束为 `^[a-z0-9_]{3,20}$`。
- `V3` 中 3 到 80 位的旧约束不要再改，`V5` 会先删除旧约束再添加新约束。
