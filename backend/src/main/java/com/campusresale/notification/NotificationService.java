package com.campusresale.notification;

import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.api.PageResponse;
import com.campusresale.platform.security.CurrentPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public PageResponse<NotificationResponse> list(CurrentPrincipal principal, boolean unreadOnly, int page, int pageSize) {
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizePageSize(pageSize);
        List<NotificationResponse> items = notificationRepository
                .findByReceiver(principal.id(), unreadOnly, normalizedPageSize, (normalizedPage - 1) * normalizedPageSize)
                .stream()
                .map(NotificationResponse::from)
                .toList();
        long total = notificationRepository.countByReceiver(principal.id(), unreadOnly);
        return new PageResponse<>(items, normalizedPage, normalizedPageSize, total);
    }

    public UnreadCountResponse unreadCount(CurrentPrincipal principal) {
        return new UnreadCountResponse(notificationRepository.unreadCount(principal.id()));
    }

    @Transactional
    public NotificationResponse markRead(long notificationId, CurrentPrincipal principal) {
        notificationRepository.findByIdAndReceiver(notificationId, principal.id())
                .orElseThrow(() -> ApiExceptions.notFound("通知不存在或不可见"));
        notificationRepository.markRead(notificationId, principal.id(), java.time.Instant.now());
        return notificationRepository.findByIdAndReceiver(notificationId, principal.id())
                .map(NotificationResponse::from)
                .orElseThrow(() -> ApiExceptions.notFound("通知不存在或不可见"));
    }

    @Transactional
    public MarkAllReadResponse markAllRead(CurrentPrincipal principal) {
        int updated = notificationRepository.markAllRead(principal.id(), java.time.Instant.now());
        return new MarkAllReadResponse(updated);
    }

    public void markConversationRead(long receiverUserId, long conversationId) {
        notificationRepository.markRelatedRead(receiverUserId, "CONVERSATION", conversationId, java.time.Instant.now());
    }

    @Transactional
    public NotificationRecord create(
            long receiverUserId,
            NotificationType type,
            String title,
            String content,
            String relatedType,
            Long relatedId,
            String dedupeKey
    ) {
        if (type == null) {
            throw ApiExceptions.validation("通知类型不能为空", Map.of("field", "type"));
        }
        String normalizedTitle = required(title, "title", "通知标题不能为空");
        String normalizedContent = required(content, "content", "通知内容不能为空");
        String normalizedRelatedType = blankToNull(relatedType);
        if ((normalizedRelatedType == null) != (relatedId == null)) {
            throw ApiExceptions.validation("关联对象类型和 id 必须同时提供", Map.of("field", "related"));
        }
        return notificationRepository.create(
                receiverUserId,
                type,
                normalizedTitle,
                normalizedContent,
                normalizedRelatedType,
                relatedId,
                blankToNull(dedupeKey)
        );
    }

    public NotificationRecord notifyOrderCreated(long sellerId, long orderId, String goodsTitle) {
        return create(
                sellerId,
                NotificationType.ORDER_CREATED,
                "收到新的订单",
                "买家已为「" + displayGoodsTitle(goodsTitle) + "」发起下单，请及时确认。",
                "ORDER",
                orderId,
                "order:" + orderId + ":created:seller:" + sellerId
        );
    }

    public NotificationRecord notifySellerConfirmed(long buyerId, long orderId) {
        return create(
                buyerId,
                NotificationType.ORDER_SELLER_CONFIRMED,
                "卖家已确认订单",
                "卖家已确认订单，请在订单详情中完成模拟支付。",
                "ORDER",
                orderId,
                "order:" + orderId + ":seller-confirmed:buyer:" + buyerId
        );
    }

    public List<NotificationRecord> notifyPaymentSucceeded(long buyerId, long sellerId, long orderId) {
        return List.of(
                create(
                        buyerId,
                        NotificationType.PAYMENT_ESCROWED,
                        "支付已完成",
                        "模拟支付已完成，订单进入待面交状态。",
                        "ORDER",
                        orderId,
                        "order:" + orderId + ":payment-escrowed:buyer:" + buyerId
                ),
                create(
                        sellerId,
                        NotificationType.PAYMENT_ESCROWED,
                        "买家已完成支付",
                        "买家已完成模拟支付，请按约定准备面交。",
                        "ORDER",
                        orderId,
                        "order:" + orderId + ":payment-escrowed:seller:" + sellerId
                )
        );
    }

    public NotificationRecord notifyCompletionRequested(long buyerId, long orderId, long requestId) {
        return create(
                buyerId,
                NotificationType.COMPLETION_REQUESTED,
                "卖家发起完成确认",
                "卖家已发起面交完成确认，请在确认窗口内处理。",
                "COMPLETION_REQUEST",
                requestId,
                "completion-request:" + requestId + ":buyer:" + buyerId + ":order:" + orderId
        );
    }

    public List<NotificationRecord> notifyOrderCompleted(long buyerId, long sellerId, long orderId) {
        return List.of(
                create(
                        buyerId,
                        NotificationType.ORDER_COMPLETED,
                        "订单已完成",
                        "订单已完成，可以在订单详情提交评价。",
                        "ORDER",
                        orderId,
                        "order:" + orderId + ":completed:buyer:" + buyerId
                ),
                create(
                        sellerId,
                        NotificationType.ORDER_COMPLETED,
                        "订单已完成",
                        "订单已完成，平台将进入结算推进流程。",
                        "ORDER",
                        orderId,
                        "order:" + orderId + ":completed:seller:" + sellerId
                )
        );
    }

    public NotificationRecord notifySettlementStatusChanged(long sellerId, long settlementId, String status) {
        return create(
                sellerId,
                NotificationType.SETTLEMENT_STATUS_CHANGED,
                "结算状态已更新",
                "订单结算状态已更新为 " + required(status, "status", "结算状态不能为空") + "。",
                "SETTLEMENT",
                settlementId,
                "settlement:" + settlementId + ":status:" + status + ":seller:" + sellerId
        );
    }

    public NotificationRecord notifyMessageReceived(long receiverId, long conversationId, String goodsTitle) {
        return create(
                receiverId,
                NotificationType.MESSAGE_RECEIVED,
                "收到新的私信",
                "关于「" + displayGoodsTitle(goodsTitle) + "」有新的会话消息。",
                "CONVERSATION",
                conversationId,
                null
        );
    }

    public NotificationRecord notifyBargainOffered(long sellerId, long conversationId, String goodsTitle) {
        return create(
                sellerId,
                NotificationType.BARGAIN_OFFERED,
                "收到新的议价",
                "买家为「" + displayGoodsTitle(goodsTitle) + "」发起了议价，请在会话中处理。",
                "CONVERSATION",
                conversationId,
                null
        );
    }

    public NotificationRecord notifyBargainAccepted(long buyerId, long conversationId, String goodsTitle) {
        return create(
                buyerId,
                NotificationType.BARGAIN_ACCEPTED,
                "议价已接受",
                "卖家已接受「" + displayGoodsTitle(goodsTitle) + "」的议价，可以用协商价下单。",
                "CONVERSATION",
                conversationId,
                null
        );
    }

    public NotificationRecord notifyBargainRejected(long buyerId, long conversationId, String goodsTitle) {
        return create(
                buyerId,
                NotificationType.BARGAIN_REJECTED,
                "议价已拒绝",
                "卖家已拒绝「" + displayGoodsTitle(goodsTitle) + "」的议价。",
                "CONVERSATION",
                conversationId,
                null
        );
    }

    private int normalizePage(int page) {
        if (page < 1) {
            throw ApiExceptions.validation("页码必须大于等于 1", Map.of("field", "page"));
        }
        return page;
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw ApiExceptions.validation("每页数量必须在 1 到 " + MAX_PAGE_SIZE + " 之间", Map.of("field", "pageSize"));
        }
        return pageSize;
    }

    private String required(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw ApiExceptions.validation(message, Map.of("field", field));
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String displayGoodsTitle(String goodsTitle) {
        if (goodsTitle == null || goodsTitle.isBlank()) {
            return "商品";
        }
        return goodsTitle.trim();
    }

    public record UnreadCountResponse(long unreadCount) {
    }

    public record MarkAllReadResponse(int updatedCount) {
    }
}
