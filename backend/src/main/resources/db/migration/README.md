# db/migration 文件说明

本目录放 Flyway 数据库迁移脚本。脚本按文件名前缀版本号顺序执行，已经执行过的旧 migration 不应再修改内容，否则其他成员本地数据库可能出现校验冲突。

| 文件 | 说明 |
| --- | --- |
| `V1__foundation_schema.sql` | M0 基础表结构。 |
| `V2__seed_foundation_data.sql` | M0 基础角色、配置、地点、分类、标签种子数据。 |
| `V3__auth_session_indexes.sql` | A 成员身份会话补充索引和初始用户名约束。 |
| `V4__seed_auth_demo_users.sql` | A 成员演示账号和角色绑定。 |
| `V5__tighten_username_rules.sql` | A 成员修正用户名规则：3 到 20 位，只允许小写字母、数字和下划线。 |
| `V6__campus_auth_schema.sql` | B 成员校园认证主表、因子表和认证材料关联表。 |
| `V7__file_verification_indexes.sql` | B 成员文件索引、认证材料可见性约束和敏感访问日志索引。 |
| `V8__campus_auth_configs.sql` | B 成员校园认证邮箱后缀、材料数量、大小、重提限制配置和 `student_demo` 已认证记录。 |
| `V9__goods_catalog_schema.sql` | C 成员商品主表、商品图片关联、商品标签关联和搜索向量刷新触发器。 |
| `V10__goods_audit_rules_schema.sql` | C 成员商品审核记录、规则命中记录和禁售词表。 |
| `V11__goods_search_seed_data.sql` | C 成员商品搜索索引、禁售词种子和少量可读占位图商品演示数据。 |

用户名规则说明：

- Java 层允许用户输入大小写字母、数字和下划线。
- Java 层会统一转小写后入库。
- 数据库层最终由 `V5` 约束为 `^[a-z0-9_]{3,20}$`。
- `V3` 中 3 到 80 位的旧约束不要再改，`V5` 会先删除旧约束再添加新约束。
