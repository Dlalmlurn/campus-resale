# N2 后台审计 API 使用指南

本文档说明 N2 阶段"管理员操作日志补强"与"审计查询 API"的开发成果和使用方式。

---

## 一、数据层变更（V14 Migration）

### 文件位置
`backend/src/main/resources/db/migration/V14__n2_admin_audit_and_stats.sql`

### 核心内容

#### 1. operation_logs 表补强（5 个新字段）

| 新增字段 | 类型 | 说明 | 默认值 |
|---|---|---|---|
| `user_agent` | TEXT | 管理端浏览器 UA，用于异常溯源 | - |
| `request_path` | VARCHAR(255) | 请求路径，如 `/api/admin/goods/123/approve` | - |
| `http_method` | VARCHAR(10) | HTTP 方法：GET / POST / PATCH... | - |
| `result` | VARCHAR(40) | 操作结果：SUCCESS / FAILURE / PARTIAL | `'SUCCESS'` |
| `operator_type` | VARCHAR(40) | 操作主体类型：ADMIN / SYSTEM / SCHEDULED | `'ADMIN'` |

所有新字段均带 `DEFAULT`，已有数据行不会破坏。

#### 2. 新增 3 条索引支撑后台分页查询

- `idx_operation_logs_created` — 纯按时间翻页
- `idx_operation_logs_action_created` — 按操作类型 + 时间过滤
- `idx_operation_logs_result_created` — 按结果状态 + 时间过滤

#### 3. 新增 9 张统计视图（维度 C）

| 视图名 | 用途 |
|---|---|
| `v_order_stats` | 订单各状态计数 + 活跃冻结金额 + 已完成流水 |
| `v_order_daily_trend` | 近 30 天每日订单趋势（上海时区分天） |
| `v_payment_stats` | 支付各状态 + 托管金额 |
| `v_settlement_stats` | 结算各状态 + 待结算金额 |
| `v_goods_stats` | 商品各状态 + 审核队列待处理量（badge 数字来源） |
| `v_review_stats` | 评价各状态 + 平均评分 + 五档星级分布 |
| `v_user_stats` | 用户总量 + 今日/本月新增 |
| `v_campus_auth_stats` | 认证各状态 + 待审核队列量 |
| `v_operation_log_stats` | 操作日志今日/本月量 + 成功失败比 |

全部使用 `CREATE OR REPLACE VIEW`，幂等安全，实时计算。

---

## 二、后端代码变更

### 1. AuditLogRepository 补强

**文件：** `backend/src/main/java/com/campusresale/platform/audit/AuditLogRepository.java`

新增重载方法：

```java
public void recordOperation(
        Long adminId,
        String action,
        String targetType,
        Long targetId,
        Object before,
        Object after,
        String ipAddress,
        String userAgent,      // 新增
        String requestPath,    // 新增
        String httpMethod,     // 新增
        String result,         // 新增
        String operatorType    // 新增
)
```

**兼容性说明：** 原有 7 参数签名保持不变，内部委托给新重载，所有已有调用方无需修改。

**后续建议：** 商品审核、认证审核等管理员操作点应改用 13 参数重载，从 `HttpServletRequest` 提取 `User-Agent` 和请求路径，提升审计完整性。

---

### 2. 新增分页查询 DAO

**文件：** `backend/src/main/java/com/campusresale/platform/audit/AdminAuditLogRepository.java`

#### 操作日志分页查询

```java
// 统计总数
public long countOperationLogs(
        String action,      // 操作类型（可选）
        String result,      // SUCCESS/FAILURE/PARTIAL（可选）
        Instant startTime,  // 时间范围起点（可选）
        Instant endTime     // 时间范围终点（可选）
)

// 分页查询
public List<OperationLogRecord> findOperationLogs(
        String action,
        String result,
        Instant startTime,
        Instant endTime,
        int page,           // 从 1 开始
        int pageSize        // 建议上限 50
)
```

#### 敏感访问日志分页查询

```java
// 统计总数
public long countSensitiveAccessLogs(
        String targetType,  // 目标类型（可选）
        Long adminId,       // 操作管理员（可选）
        Instant startTime,
        Instant endTime
)

// 分页查询
public List<SensitiveAccessLogRecord> findSensitiveAccessLogs(
        String targetType,
        Long adminId,
        Instant startTime,
        Instant endTime,
        int page,
        int pageSize
)
```

**索引命中保证：**
- 所有查询均使用 `ORDER BY created_at DESC, id DESC` 排序
- WHERE 条件动态构造，PostgreSQL 优化器自动选择最佳索引
- 无过滤条件时走 `idx_xxx_created`，有 `action` 过滤时走 `idx_operation_logs_action_created`，以此类推

---

### 3. 新增后台审计查询 Controller

**文件：** `backend/src/main/java/com/campusresale/platform/audit/AdminAuditController.java`

#### 类级权限注解

```java
@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})
@RestController
@RequestMapping("/api/admin")
public class AdminAuditController { ... }
```

通过类级 `@RequireRole` 注解自动接入现有的 `AuthorizationInterceptor`，无需在方法内手动判断权限。

#### 接口 1：操作日志查询

```http
GET /api/admin/operation-logs?action=GOODS_APPROVE&result=SUCCESS&page=1&pageSize=20
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `action` | String | 否 | 操作类型精确匹配，如 `GOODS_APPROVE` |
| `result` | String | 否 | 结果状态：`SUCCESS` / `FAILURE` / `PARTIAL` |
| `startTime` | ISO-8601 | 否 | 时间范围起点（含），如 `2026-06-01T00:00:00Z` |
| `endTime` | ISO-8601 | 否 | 时间范围终点（含） |
| `page` | int | 否 | 页码，从 1 开始，默认 1 |
| `pageSize` | int | 否 | 每页条数，默认 20，最大 50 |

**响应格式：**

```json
{
  "items": [
    {
      "id": 123,
      "adminId": 1,
      "action": "GOODS_APPROVE",
      "targetType": "GOODS",
      "targetId": 456,
      "ipAddress": "192.168.1.100",
      "userAgent": "Mozilla/5.0 ...",
      "requestPath": "/api/admin/goods/456/approve",
      "httpMethod": "POST",
      "result": "SUCCESS",
      "operatorType": "ADMIN",
      "createdAt": "2026-06-05T15:30:00Z"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 456
}
```

#### 接口 2：敏感访问日志查询

```http
GET /api/admin/sensitive-access-logs?targetType=CAMPUS_AUTH_MATERIAL&page=1&pageSize=20
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `targetType` | String | 否 | 目标类型，如 `CAMPUS_AUTH_MATERIAL` |
| `adminId` | Long | 否 | 操作管理员 id |
| `startTime` | ISO-8601 | 否 | 时间范围起点（含） |
| `endTime` | ISO-8601 | 否 | 时间范围终点（含） |
| `page` | int | 否 | 页码，从 1 开始，默认 1 |
| `pageSize` | int | 否 | 每页条数，默认 20，最大 50 |

**响应格式：**

```json
{
  "items": [
    {
      "id": 789,
      "adminId": 1,
      "targetType": "CAMPUS_AUTH_MATERIAL",
      "targetId": 123,
      "reason": "审核校园认证材料",
      "result": "ALLOWED",
      "ipAddress": "192.168.1.100",
      "createdAt": "2026-06-05T15:25:00Z"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 89
}
```

---

## 三、权限挂载机制说明

### 自动生效，无需额外配置

`AdminAuditController` 通过类级 `@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})` 注解自动接入现有的 `AuthorizationInterceptor`。

**工作流程：**

1. 请求进入 → `SessionAuthenticationFilter` 从 `CR_SESSION` Cookie 识别当前用户
2. 写入 `CurrentPrincipal` 到 `HttpServletRequest` 上下文
3. → `OriginCsrfInterceptor` 校验非安全方法的来源（POST/PATCH/DELETE）
4. → `AuthorizationInterceptor` 读取 `@RequireRole` 注解，判断当前用户角色
5. → 没有 `CONTENT_ADMIN` 或 `SUPER_ADMIN` 角色时抛出 `403 FORBIDDEN`
6. → 有权限则进入 Controller 方法

**验证方式：**

```bash
# 未登录访问 → 401 AUTH_REQUIRED
curl http://localhost:8080/api/admin/operation-logs

# 普通用户访问 → 403 FORBIDDEN
curl -b "CR_SESSION=student_token" http://localhost:8080/api/admin/operation-logs

# 管理员访问 → 200 OK + 分页数据
curl -b "CR_SESSION=admin_token" http://localhost:8080/api/admin/operation-logs
```

---

## 四、前端集成建议（维度 D）

### 1. 操作日志页面

**路由：** `/admin/operation-logs`

**组件职责：**
- 时间范围选择器（默认近 7 天）
- 操作类型下拉筛选（动态读取或预设常用 action）
- 结果状态 Tab 切换（全部 / 成功 / 失败）
- 表格展示：操作时间、操作类型、目标、管理员、请求路径、结果
- 分页器（基于 `total` 和 `pageSize` 计算总页数）

**API 调用示例：**

```typescript
const response = await fetch(
  `/api/admin/operation-logs?action=GOODS_APPROVE&result=SUCCESS&page=1&pageSize=20`,
  { credentials: 'include' }
);
const data = await response.json();
// { items: [...], page: 1, pageSize: 20, total: 456 }
```

### 2. 敏感访问日志页面

**路由：** `/admin/sensitive-access-logs`

**组件职责：**
- 时间范围选择器
- 目标类型筛选（如 `CAMPUS_AUTH_MATERIAL`）
- 管理员筛选（输入框或下拉，搜索 admin_id）
- 表格展示：访问时间、管理员、目标类型、目标 ID、原因、结果
- 分页器

**合规提示：**
- 页面顶部显示"敏感材料访问记录受审计保护，所有访问行为均已留痕"
- 可用于答辩演示"后台操作全程可追溯"

---

## 五、后续优化建议

### 立即可做

1. **补全现有管理员操作点的完整日志写入**
   - `GoodsService.approve()` 和 `reject()` 改用 13 参数重载
   - `CampusVerificationService` 认证审核改用 13 参数重载
   - 从 `HttpServletRequest` 提取 `getHeader("User-Agent")`、`getRequestURI()`、`getMethod()`

2. **前端接入操作日志和敏感访问日志页面**
   - 参考上述"前端集成建议"
   - 后台主导航添加"审计日志"菜单项

3. **统计视图接入**
   - 新建 `AdminStatsController`，提供 `GET /api/admin/stats/orders`、`GET /api/admin/stats/payments` 等接口
   - 直接 `SELECT * FROM v_order_stats` 返回 JSON，无需复杂聚合代码
   - 前端后台首页展示核心指标卡片（待处理订单数、今日成交额等）

### N3 后续增强

1. **操作日志详情弹窗**
   - 点击某行操作日志，弹窗展示完整 `before_json` 和 `after_json` 差异对比
   - 使用 JSON diff 库高亮变更字段

2. **敏感访问日志告警**
   - 同一管理员短时间内（如 5 分钟）访问超过 10 条认证材料 → 后台显示红色 badge 提示
   - 可配置告警规则和通知渠道

3. **操作日志导出**
   - 后台提供"导出 CSV"按钮，按当前筛选条件导出全量日志
   - 用于合规审计或外部存档

---

## 六、验收清单

### 数据库验收

- [ ] 在空库或测试库执行 `V1-V14` 全部 migration，无报错
- [ ] `operation_logs` 表包含 5 个新字段且带 DEFAULT 约束
- [ ] `SELECT * FROM v_order_stats` 能返回聚合结果
- [ ] 3 条新索引存在：`idx_operation_logs_created`、`idx_operation_logs_action_created`、`idx_operation_logs_result_created`

### 后端验收

- [ ] `mvn compile` 或 `./mvnw compile` 通过，无编译错误
- [ ] 启动后端，访问 `GET /api/admin/operation-logs`，未登录返回 `401 AUTH_REQUIRED`
- [ ] 普通用户登录后访问，返回 `403 FORBIDDEN`
- [ ] 管理员登录后访问，返回 `200 OK` + 分页 JSON
- [ ] 带 `action=GOODS_APPROVE` 查询参数，返回过滤后的结果
- [ ] `GET /api/admin/sensitive-access-logs` 同样满足上述权限和分页规则

### 前端验收

- [ ] 后台菜单新增"审计日志"入口
- [ ] 操作日志页面能按时间范围和操作类型筛选
- [ ] 敏感访问日志页面能按目标类型筛选
- [ ] 分页器能正确翻页且调用正确的 API
- [ ] 表格显示的时间戳格式友好（如"2026-06-05 15:30"）

---

## 七、常见问题

### Q1: 为什么操作日志查询不返回 `before_json` 和 `after_json`？

**A:** 当前 `OperationLogRecord` 为了列表展示性能和前端渲染效率，只返回元数据字段。若需要完整 JSON，可新增 `GET /api/admin/operation-logs/{id}` 详情接口，单独查询并返回完整记录。

### Q2: 统计视图会不会影响数据库性能？

**A:** 不会。视图是虚拟表，每次 `SELECT` 时实时计算，不占存储。9 张统计视图都是简单聚合（COUNT/SUM/AVG），在有索引的情况下 PostgreSQL 能毫秒级响应。后台首页每 30 秒轮询一次即可，不会产生性能瓶颈。

### Q3: 现有的操作日志会不会因为缺少 5 个新字段而查询失败?

**A:** 不会。V14 migration 为 5 个新字段全部设置了 `DEFAULT` 值，已有行会自动填充默认值。查询时 `user_agent`、`request_path`、`http_method` 为 NULL，`result` 为 `'SUCCESS'`，`operator_type` 为 `'ADMIN'`。

### Q4: 如何在 Postman 里测试管理员接口？

**A:** 先调用 `POST /api/auth/login` 用管理员账号（如 `content_admin` / `520zikejiang`）登录，响应会设置 `Set-Cookie: CR_SESSION=...`。Postman 会自动保存 Cookie，后续请求会自动携带。或手动复制 Cookie 值，在后续请求的 Headers 里加 `Cookie: CR_SESSION=xxx`。

---

## 八、交付清单

### 数据库迁移文件

- `backend/src/main/resources/db/migration/V14__n2_admin_audit_and_stats.sql`

### 后端代码

- `backend/src/main/java/com/campusresale/platform/audit/AuditLogRepository.java`（修改，新增重载）
- `backend/src/main/java/com/campusresale/platform/audit/OperationLogRecord.java`（新建）
- `backend/src/main/java/com/campusresale/platform/audit/SensitiveAccessLogRecord.java`（新建）
- `backend/src/main/java/com/campusresale/platform/audit/AdminAuditLogRepository.java`（新建）
- `backend/src/main/java/com/campusresale/platform/audit/AdminAuditController.java`（新建）

### 文档

- `docs/N2_AUDIT_API_GUIDE.md`（本文件）

---

**开发完成时间：** 2026-06-05  
**负责人：** A + B 成员联合交付  
**验收状态：** 待前端集成和完整 check.sh 验证
