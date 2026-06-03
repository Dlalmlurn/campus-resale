# N1_A 成员权限契约与拦截基础实施记录

更新时间：2026-06-02

## 背景

M1 已完成身份、会话、校园认证、商品审核和前端闭环。进入 N1 最小交易闭环后，订单创建、模拟支付、面交完成确认、结算状态推进和最小评价都需要大量“谁能做什么”的判断，例如：

- 只有具备完整交易资格的认证学生才能发起交易动作。
- 买家不能购买自己的商品。
- 只有订单买家或卖家能查看订单详情。
- 管理员查看订单、支付、结算和评价后台数据时必须具备管理员角色。

如果这些规则分散写在各模块 Controller 中，后续很容易出现口径不一致。因此 A 成员本轮先补权限契约和后端拦截基础。

## 本轮实现范围

本轮不实现完整会话、订单、支付、退款和结算业务，只完成以下内容：

1. 新增 `docs/阶段契约/n1_access_control_contracts.md`。
2. 新增 `@RequireTradeEligible` 注解。
3. 新增 `TradeEligibilityChecker` 平台接口。
4. 新增 `CampusTradeEligibilityChecker`，复用 B 成员 `CampusTradeEligibilityResolver` 的完整 `canTrade` 规则。
5. 扩展 `AuthorizationInterceptor`，统一识别登录、角色和交易资格注解。
6. 扩展 `AuthorizationInterceptorTest`，覆盖 N1 交易资格拦截场景。
7. 将现有商品创建草稿和提交审核接口接入 `@RequireTradeEligible`，让商品发布链路和后续订单链路使用同一交易资格入口。

## 关键设计

### 拦截器只管通用权限

`AuthorizationInterceptor` 负责处理不依赖具体资源 id 的判断：

- 是否登录。
- 是否拥有指定角色。
- 是否具备完整交易资格。

例如 `@RequireTradeEligible` 可以拦截未认证用户访问创建商品草稿、提交商品审核、创建订单、模拟支付或完成确认接口。

### 业务层继续判断资源关系

是否是某个订单买家或卖家、商品是否仍在售、订单状态是否允许支付或完成，这些都依赖数据库记录，仍由后续订单、支付、结算或评价服务层判断。

例如：

- `GET /api/orders/{id}` 可以先用 `@RequireLogin` 保证已登录。
- 服务层再判断当前用户是否是该订单买家或卖家。
- 如果不是参与者，优先返回 `404 NOT_FOUND`，避免暴露订单是否存在。

### 平台安全层不直接依赖认证表

`platform.security` 只新增 `TradeEligibilityChecker` 接口，不直接依赖 `CampusVerificationRepository`。

真实规则由 `identity.verification.CampusTradeEligibilityChecker` 实现：

```text
CurrentPrincipal
  -> CampusTradeEligibilityChecker
  -> CampusTradeEligibilityResolver
  -> campus_auths / campus_auth_factors / user_roles
  -> canTrade
```

这样后续如果交易资格规则变更，只需要改身份认证模块，不需要重写拦截器。

### 商品发布已接入统一交易资格

`POST /api/goods/drafts` 和 `POST /api/goods/{id}/submit` 已从 `@RequireLogin` 升级为 `@RequireTradeEligible`。

这样做的原因：

- 商品发布本身是交易闭环的入口，应和订单创建、支付、评价使用同一完整交易资格口径。
- Controller 前先挡住未认证账号，减少业务服务层重复处理明显无权限的请求。
- `GoodsService` 中原有完整交易资格校验和卖家本人判断继续保留，用作业务层防线和资源级权限判断。

## 后续接入建议

N1 降级会话：

- N1 可以先不做 WebSocket 和议价卡片。
- 订单备注随 `POST /api/orders` 提交时，复用订单创建权限。
- 后续如果恢复会话模块，再按 `docs/05_database_design.md` 和 N3 计划补齐私信、系统卡片和敏感访问日志。

订单模块：

- 创建订单、卖家确认、卖家拒绝、买家取消、完成确认动作：优先使用 `@RequireTradeEligible`。
- 查询我的订单和订单详情：使用 `@RequireLogin`，再由服务层判断买家/卖家关系。
- 管理员订单治理接口：使用 `@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})`。

模拟支付、结算和评价：

- 买家模拟支付：使用 `@RequireTradeEligible`，服务层确认当前用户是订单买家。
- 结算状态推进：普通用户不能伪造状态；若提供后台手动推进接口，必须使用管理员角色注解。
- 最小评价提交：使用 `@RequireTradeEligible`，服务层确认当前用户是订单参与者且订单已完成。

## 验收建议

- 运行后端测试，确认 `AuthorizationInterceptorTest` 全部通过。
- 验证商品发布接口在未登录时返回 `401 AUTH_REQUIRED`，在登录但 `canTrade=false` 时返回 `403 FORBIDDEN`。
- 后续新增订单、模拟支付、结算或评价 Controller 时，先根据 `docs/阶段契约/n1_access_control_contracts.md` 选择注解，再补服务层资源级权限测试。
- 如果发现业务接口需要新的上下文权限，例如“必须是订单卖家”，优先在服务层实现，不急着新增注解。
