# N1_B 成员通知与证据基础实施记录

更新时间：2026-06-03

## 背景

N1 最小交易闭环中，订单创建、卖家确认、买家模拟支付、完成确认和结算推进都需要给买卖双方留下可追踪的站内提醒。B 成员延续 M1 文件与认证材料能力线，本轮先补交易通知基础，并为后续订单证据、退款证据或治理材料复用文件服务预留最小入口。

## 本轮实现范围

1. 新增 `notifications` 表，保存站内通知事实、关联对象、已读时间和可选幂等键。
2. 新增 `NotificationService`、`NotificationRepository` 和 `/api/notifications` 用户侧接口。
3. 提供 N1-C 后续订单/支付/结算状态机可直接调用的语义方法：
   - `notifyOrderCreated`
   - `notifySellerConfirmed`
   - `notifyPaymentSucceeded`
   - `notifyCompletionRequested`
   - `notifyOrderCompleted`
   - `notifySettlementStatusChanged`
4. 新增 `ORDER_EVIDENCE` 文件用途，占位订单证据文件上传能力。
5. `ORDER_EVIDENCE` 在 N1 默认强制 `PRIVATE`，现阶段只允许上传者本人和管理员读取原件；订单参与者可见性待订单模块提供参与关系判断后再升级。

## 权限口径

- 通知列表、未读数、标记已读和全部已读均使用 `@RequireLogin`。
- 用户只能读取和操作 `receiver_user_id = 当前用户 id` 的通知。
- 访问其他用户通知时返回 `404 NOT_FOUND`，不暴露通知是否存在。
- 交易动作本身是否需要 `@RequireTradeEligible` 仍由订单、支付、完成确认等 Controller 按 N1 权限契约决定。

## 幂等口径

通知服务支持可选 `dedupeKey`。订单、支付和结算状态机在重复调用同一事件通知时，可以传入稳定键，例如：

```text
order:{orderId}:payment-escrowed:buyer:{buyerId}
```

数据库用 `(receiver_user_id, dedupe_key)` 部分唯一索引防止重复插入。没有幂等需求的普通通知可以不传 `dedupeKey`。

## 后续接入建议

- N1-C 在订单状态变化事务内调用 `NotificationService` 的语义方法。
- 若后续订单状态机需要“状态变更成功后再发通知”，可把调用移到事务提交后的事件监听；N1 先采用服务调用，保持实现简单。
- 订单证据若要对买卖双方开放，需要订单模块提供“当前用户是否订单参与者”的服务，再把 `ORDER_EVIDENCE` 从上传者私有升级到参与者可见。
