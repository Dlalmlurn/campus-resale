# N1 权限契约与后端拦截基础

更新时间：2026-06-02

本文档固定 N1 最小交易闭环的权限口径，主要服务 A 成员 Identity & Access Control 工作。后续实现订单、模拟支付、完成确认、结算推进和最小评价前，涉及“谁能做什么”的判断应优先对齐本文档。

## 范围

本轮只定义 N1 权限契约和后端拦截基础，不实现完整订单状态机、模拟支付、结算和评价业务。

已落地的后端基础：

- `@RequireLogin`：要求当前请求已有有效 `CR_SESSION`。
- `@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})`：要求当前用户拥有任意一个指定角色。
- `@RequireTradeEligible`：要求当前用户已登录，并且具备完整交易资格。
- `TradeEligibilityChecker`：平台安全层的交易资格检查接口。
- `CampusTradeEligibilityChecker`：身份认证模块提供的交易资格实现，复用 `CampusTradeEligibilityResolver` 的 `canTrade` 口径。
- 现有商品创建草稿和提交审核接口已接入 `@RequireTradeEligible`，后续订单、支付和评价接口沿用同一口径。

## 术语

| 术语 | 说明 |
| --- | --- |
| 当前用户 | 通过 `CR_SESSION` Cookie 识别出的 `CurrentPrincipal`。 |
| 交易资格 | 当前用户拥有 `VERIFIED_STUDENT` 角色，且校园认证完整规则计算出 `canTrade=true`。 |
| 买家 | 在某个商品会话或订单中发起购买的一方，不是独立账号类型。 |
| 卖家 | 某个商品的 `seller_id` 对应用户，不是独立账号类型。 |
| 会话参与者 | 某个 `conversation` 的 `buyer_id` 或 `seller_id`。 |
| 订单参与者 | 某个 `trade_order` 的 `buyer_id` 或 `seller_id`。 |
| 管理员 | 拥有 `CONTENT_ADMIN` 或 `SUPER_ADMIN` 的用户。 |

## 通用错误码

| 场景 | HTTP | code | 说明 |
| --- | --- | --- | --- |
| 未登录访问受保护接口 | 401 | `AUTH_REQUIRED` | 没有 Cookie、Cookie 无效或 session 失效。 |
| 登录但缺少角色或交易资格 | 403 | `FORBIDDEN` | 例如未完成校园认证却访问交易接口。 |
| 访问不属于自己的会话、订单或非公开资源 | 404 | `NOT_FOUND` | 优先隐藏资源是否存在，降低枚举风险。 |
| 资源状态不允许当前动作 | 409 | `CONFLICT` | 例如商品已被占用、订单状态不允许取消。 |
| 请求参数非法 | 400 | `VALIDATION_FAILED` | 字段缺失、格式错误或超出限制。 |

## 后端注解使用规则

| 注解 | 使用位置 | 作用 |
| --- | --- | --- |
| `@RequireLogin` | 只需要登录态的接口 | 例如查询自己的会话列表、查询自己的订单列表。 |
| `@RequireTradeEligible` | 会创建或推进交易事实的接口 | 例如创建商品草稿、提交商品审核、创建订单、买家模拟支付、卖家确认订单、完成确认。 |
| `@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})` | 管理员接口 | 例如订单后台查询、支付后台查询、结算后台查询。 |

注意：

- `@RequireTradeEligible` 只负责“当前用户是否有完整交易资格”。
- “是否是该会话参与者”“是否是该订单买家/卖家”“商品是否仍可交易”等资源级判断仍由业务服务层执行。
- 业务服务层判断参与关系时，非参与者读取私有资源优先返回 `404 NOT_FOUND`，不要直接暴露资源存在性。

## N1 降级会话口径

N1 为保证最小交易闭环优先，会话可以先不做 WebSocket 和议价卡片。若需要保留订单备注、系统消息或普通接口支撑演示，权限语义如下。

| 动作 | 建议接口 | 基础注解 | 资源级规则 |
| --- | --- | --- | --- |
| 创建订单备注 | 随 `POST /api/orders` 请求提交 | `@RequireTradeEligible` | 当前用户必须是买家；备注只进入订单事实，不作为支付金额来源。 |
| 查看订单备注或系统消息 | 随 `GET /api/orders/{id}` 返回 | `@RequireLogin` | 当前用户必须是订单买家或卖家；非参与者返回 `404 NOT_FOUND`。 |
| 后续升级为会话 | 待 N3 或后续 PR 决定 | 待定 | WebSocket、议价卡片和私信敏感访问不阻塞 N1。 |

## 订单权限矩阵

N1 订单接口建议口径如下。

| 动作 | 建议接口 | 基础注解 | 资源级规则 |
| --- | --- | --- | --- |
| 创建订单 | `POST /api/orders` | `@RequireTradeEligible` | 当前用户是买家；买家不能是卖家；商品 `ON_SALE`、未删除、未被其他订单占用；金额来自挂牌价或已接受议价卡片。 |
| 查询我的订单列表 | `GET /api/orders` | `@RequireLogin` | 只返回当前用户作为买家或卖家的订单。 |
| 查询订单详情 | `GET /api/orders/{id}` | `@RequireLogin` | 当前用户必须是订单参与者；非参与者返回 `404 NOT_FOUND`。 |
| 卖家确认订单 | `POST /api/orders/{id}/seller-confirm` | `@RequireTradeEligible` | 当前用户必须是卖家；订单状态必须是 `PENDING_SELLER_CONFIRM`。 |
| 卖家拒绝订单 | `POST /api/orders/{id}/seller-reject` | `@RequireTradeEligible` | 当前用户必须是卖家；拒绝后释放商品占用并写状态历史。 |
| 买家取消订单 | `POST /api/orders/{id}/buyer-cancel` | `@RequireTradeEligible` | 当前用户必须是买家；仅允许在支付前或业务规定的可取消状态。 |
| 卖家发起完成确认 | `POST /api/orders/{id}/completion-requests` | `@RequireTradeEligible` | 当前用户必须是卖家；只能在约定面交时间后发起。 |
| 买家确认完成 | `POST /api/orders/{id}/completion-requests/{requestId}/confirm` | `@RequireTradeEligible` | 当前用户必须是买家；确认窗口内有效。 |
| 管理员查询订单 | `GET /api/admin/orders` | `@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})` | 用于治理、纠纷和审计，不进入普通用户侧列表。 |

## 模拟支付与结算权限矩阵

N1 只做模拟支付和结算状态推进，不接真实支付平台。

| 动作 | 建议接口 | 基础注解 | 资源级规则 |
| --- | --- | --- | --- |
| 创建或触发模拟支付 | `POST /api/orders/{id}/payments/simulate` | `@RequireTradeEligible` | 当前用户必须是订单买家；订单必须已由卖家确认且仍占用商品。 |
| 查询我的支付状态 | `GET /api/orders/{id}/payment` | `@RequireLogin` | 当前用户必须是订单买家或卖家。 |
| 管理员查询支付 | `GET /api/admin/payments` | `@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})` | 用于后台验收和审计。 |
| 推进结算状态 | 后端服务或管理员接口 | `@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})` 或后端任务 | N1 可用手动后台动作或受控任务推进，不允许普通用户伪造结算状态。 |
| 管理员查询结算 | `GET /api/admin/settlements` | `@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})` | 用于后台验收和答辩展示。 |

## 最小评价权限矩阵

| 动作 | 建议接口 | 基础注解 | 资源级规则 |
| --- | --- | --- | --- |
| 提交评价 | `POST /api/orders/{id}/reviews` | `@RequireTradeEligible` | 当前用户必须是订单买家或卖家；订单必须已完成；每个订单每个参与方最多评价一次。 |
| 查看订单评价 | `GET /api/orders/{id}/reviews` | `@RequireLogin` | 当前用户必须是订单参与者；公开评价列表可在后续阶段独立开放。 |
| 管理员查看评价 | `GET /api/admin/reviews` | `@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})` | 用于后台验收和后续治理。 |

## 拦截器与业务层分工

后端权限分两层：

1. 拦截器层：处理登录、角色、交易资格这类不依赖具体资源 id 的统一判断。
2. 业务服务层：处理买家/卖家/参与者关系、商品状态、订单状态和金额来源这类依赖数据库资源的判断。

这样拆分的原因：

- 拦截器可以在 Controller 前快速拒绝明显不合规请求。
- 业务服务层拿到数据库记录后，才能准确判断当前用户是否是该资源参与者。
- 后续 WebSocket 握手可以复用 `CurrentPrincipal` 和 `TradeEligibilityChecker`，但 N1 不要求实现 WebSocket。

## N1 A 成员验收点

- `@RequireTradeEligible` 标注的方法在未登录时返回 `401 AUTH_REQUIRED`。
- `@RequireTradeEligible` 标注的方法在登录但 `canTrade=false` 时返回 `403 FORBIDDEN`。
- `@RequireTradeEligible` 标注的方法在 `canTrade=true` 时可以进入 Controller。
- `platform.security` 不直接依赖校园认证数据库或 repository。
- `identity.verification` 通过 `CampusTradeEligibilityChecker` 提供真实交易资格规则。
- `POST /api/goods/drafts` 和 `POST /api/goods/{id}/submit` 已使用 `@RequireTradeEligible`，并保留服务层完整交易资格和卖家关系判断。
- 后续订单、模拟支付、结算和评价模块可以直接引用上述注解，不需要复制商品模块里的交易资格判断。
