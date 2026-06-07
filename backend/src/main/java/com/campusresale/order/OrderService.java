package com.campusresale.order;

import com.campusresale.conversation.AcceptedBargainQuote;
import com.campusresale.conversation.ConversationService;
import com.campusresale.goods.GoodsAuditStatus;
import com.campusresale.goods.GoodsRecord;
import com.campusresale.goods.GoodsRepository;
import com.campusresale.goods.GoodsStatus;
import com.campusresale.notification.NotificationService;
import com.campusresale.order.OrderRepository.OrderWriteData;
import com.campusresale.order.OrderRequests.CreateOrderRequest;
import com.campusresale.order.OrderRequests.ReasonRequest;
import com.campusresale.order.OrderRequests.ReviewRequest;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.api.PageResponse;
import com.campusresale.platform.security.CurrentPrincipal;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_NOTE_LENGTH = 500;
    private static final int MAX_REVIEW_CONTENT_LENGTH = 500;
    private static final long COMPLETION_WINDOW_SECONDS = 48 * 60 * 60;
    private static final long SETTLEMENT_FREEZE_SECONDS = 7 * 24 * 60 * 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orderRepository;
    private final GoodsRepository goodsRepository;
    private final ConversationService conversationService;
    private final NotificationService notificationService;

    public OrderService(
            OrderRepository orderRepository,
            GoodsRepository goodsRepository,
            ConversationService conversationService,
            NotificationService notificationService
    ) {
        this.orderRepository = orderRepository;
        this.goodsRepository = goodsRepository;
        this.conversationService = conversationService;
        this.notificationService = notificationService;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, CurrentPrincipal buyer) {
        if (request == null) {
            throw ApiExceptions.validation("请填写订单信息", Map.of("body", "required"));
        }
        long goodsId = requirePositive(request.goodsId(), "goodsId", "请选择要购买的商品");
        GoodsRecord goods = goodsRepository.findByIdForUpdate(goodsId)
                .orElseThrow(() -> ApiExceptions.notFound("商品不存在或不可见"));
        if (goods.sellerId() == buyer.id()) {
            throw ApiExceptions.conflict("不能购买自己发布的商品", Map.of("goodsId", goodsId));
        }
        if (goods.status() != GoodsStatus.ON_SALE || goods.auditStatus() != GoodsAuditStatus.APPROVED) {
            throw ApiExceptions.conflict("商品当前不可下单", Map.of("status", goods.status().name()));
        }
        if (goodsRepository.currentOccupiedOrderId(goodsId) != null) {
            throw ApiExceptions.conflict("商品已被其他订单占用", Map.of("goodsId", goodsId));
        }

        Instant now = Instant.now();
        AcceptedBargainQuote quote = request.acceptedBargainCardId() == null
                ? null
                : conversationService.validateAcceptedBargainForOrder(goodsId, buyer.id(), request.acceptedBargainCardId());
        String note = optionalTrimmed(request.note(), MAX_NOTE_LENGTH, "note", "订单备注最多 500 个字符");
        long orderId = orderRepository.createOrder(new OrderWriteData(
                generateNo("ORD"),
                goodsId,
                quote == null ? null : quote.conversationId(),
                quote == null ? null : quote.cardId(),
                buyer.id(),
                goods.sellerId(),
                quote == null ? goods.listPrice() : quote.amount(),
                TradeOrderStatus.PENDING_SELLER_CONFIRM,
                request.tradePlaceId() == null ? goods.tradePlaceId() : request.tradePlaceId(),
                optionalTrimmed(
                        request.tradePlaceDetail() == null ? goods.tradePlaceDetail() : request.tradePlaceDetail(),
                        255,
                        "tradePlaceDetail",
                        "面交地点补充最多 255 个字符"
                ),
                request.meetupTime(),
                note,
                now
        ));
        if (goodsRepository.reserveForOrder(goodsId, orderId) != 1) {
            throw ApiExceptions.conflict("商品已被其他订单占用", Map.of("goodsId", goodsId));
        }
        orderRepository.insertStateRecord(
                orderId,
                null,
                TradeOrderStatus.PENDING_SELLER_CONFIRM,
                "ORDER_CREATED",
                buyer.id(),
                null,
                null
        );
        notificationService.notifyOrderCreated(goods.sellerId(), orderId, goods.title());
        return loadOrderResponse(orderId);
    }

    public PageResponse<OrderResponse> list(String status, int page, int pageSize, CurrentPrincipal principal) {
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizePageSize(pageSize);
        TradeOrderStatus parsedStatus = TradeOrderStatus.parseFilter(status);
        List<OrderResponse> items = orderRepository
                .listByParticipant(principal.id(), parsedStatus, normalizedPage, normalizedPageSize)
                .stream()
                .map(OrderResponse::from)
                .toList();
        long total = orderRepository.countByParticipant(principal.id(), parsedStatus);
        return new PageResponse<>(items, normalizedPage, normalizedPageSize, total);
    }

    public OrderResponse detail(long orderId, CurrentPrincipal principal) {
        TradeOrderRecord order = orderRepository.findOrderById(orderId)
                .orElseThrow(() -> ApiExceptions.notFound("订单不存在或不可见"));
        requireParticipant(order, principal);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse sellerConfirm(long orderId, CurrentPrincipal seller) {
        TradeOrderRecord order = lockedOrder(orderId);
        requireSeller(order, seller);
        requireStatus(order, TradeOrderStatus.PENDING_SELLER_CONFIRM, "订单当前不能由卖家确认");
        transitionOrder(order, TradeOrderStatus.PENDING_PAYMENT, "SELLER_CONFIRMED", seller.id(), null, null, false);
        notificationService.notifySellerConfirmed(order.buyerId(), order.id());
        return loadOrderResponse(orderId);
    }

    @Transactional
    public OrderResponse sellerReject(long orderId, ReasonRequest request, CurrentPrincipal seller) {
        TradeOrderRecord order = lockedOrder(orderId);
        requireSeller(order, seller);
        requireStatus(order, TradeOrderStatus.PENDING_SELLER_CONFIRM, "订单当前不能由卖家拒绝");
        transitionOrder(
                order,
                TradeOrderStatus.CLOSED,
                "SELLER_REJECTED",
                seller.id(),
                null,
                reason(request, "卖家拒绝订单"),
                true
        );
        goodsRepository.releaseReservation(order.goodsId(), order.id());
        return loadOrderResponse(orderId);
    }

    @Transactional
    public OrderResponse buyerCancel(long orderId, ReasonRequest request, CurrentPrincipal buyer) {
        TradeOrderRecord order = lockedOrder(orderId);
        requireBuyer(order, buyer);
        if (order.status() != TradeOrderStatus.PENDING_SELLER_CONFIRM && order.status() != TradeOrderStatus.PENDING_PAYMENT) {
            throw ApiExceptions.conflict("订单当前不能由买家取消", Map.of("status", order.status().name()));
        }
        transitionOrder(
                order,
                TradeOrderStatus.CANCELLED,
                "BUYER_CANCELLED",
                buyer.id(),
                null,
                reason(request, "买家取消订单"),
                true
        );
        goodsRepository.releaseReservation(order.goodsId(), order.id());
        return loadOrderResponse(orderId);
    }

    @Transactional
    public PaymentResponse simulatePayment(long orderId, CurrentPrincipal buyer) {
        TradeOrderRecord order = lockedOrder(orderId);
        requireBuyer(order, buyer);
        if (order.status() == TradeOrderStatus.PAID_PENDING_MEETUP) {
            return orderRepository.findEscrowedPaymentByOrder(order.id())
                    .map(PaymentResponse::from)
                    .orElseThrow(() -> ApiExceptions.conflict("订单支付状态不一致", Map.of("orderId", order.id())));
        }
        requireStatus(order, TradeOrderStatus.PENDING_PAYMENT, "订单当前不能支付");
        Long occupiedOrderId = goodsRepository.currentOccupiedOrderId(order.goodsId());
        if (!Long.valueOf(order.id()).equals(occupiedOrderId)) {
            throw ApiExceptions.conflict("商品占用状态不一致，暂不能支付", Map.of("orderId", order.id()));
        }

        Instant now = Instant.now();
        PaymentOrderRecord payment = orderRepository.findLatestPaymentByOrderForUpdate(order.id())
                .orElseGet(() -> {
                    long paymentId = orderRepository.createPayment(generateNo("PAY"), order.id(), order.frozenAmount(), now);
                    return orderRepository.findLatestPaymentByOrderForUpdate(order.id())
                            .filter(record -> record.id() == paymentId)
                            .orElseThrow(ApiExceptions::internalError);
                });
        if (payment.status() != PaymentOrderStatus.ESCROWED) {
            orderRepository.markPaymentEscrowed(payment.id(), now);
            orderRepository.insertPaymentTransaction(
                    "SIM-TXN-" + payment.paymentNo(),
                    payment.id(),
                    payment.amount(),
                    now
            );
        }
        transitionOrder(order, TradeOrderStatus.PAID_PENDING_MEETUP, "PAYMENT_ESCROWED", buyer.id(), null, null, false);
        notificationService.notifyPaymentSucceeded(order.buyerId(), order.sellerId(), order.id());
        return orderRepository.findLatestPaymentByOrder(order.id())
                .map(PaymentResponse::from)
                .orElseThrow(ApiExceptions::internalError);
    }

    public PaymentResponse paymentStatus(long orderId, CurrentPrincipal principal) {
        TradeOrderRecord order = orderRepository.findOrderById(orderId)
                .orElseThrow(() -> ApiExceptions.notFound("订单不存在或不可见"));
        requireParticipant(order, principal);
        return orderRepository.findLatestPaymentByOrder(orderId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> ApiExceptions.notFound("支付单不存在或不可见"));
    }

    @Transactional
    public CompletionRequestResponse requestCompletion(long orderId, CurrentPrincipal seller) {
        TradeOrderRecord order = lockedOrder(orderId);
        requireSeller(order, seller);
        requireStatus(order, TradeOrderStatus.PAID_PENDING_MEETUP, "订单当前不能发起完成确认");
        return orderRepository.findPendingCompletionRequest(order.id())
                .map(CompletionRequestResponse::from)
                .orElseGet(() -> {
                    Instant now = Instant.now();
                    long requestId = orderRepository.createCompletionRequest(
                            order.id(),
                            order.sellerId(),
                            order.buyerId(),
                            now,
                            now.plusSeconds(COMPLETION_WINDOW_SECONDS)
                    );
                    orderRepository.insertStateRecord(
                            order.id(),
                            order.status(),
                            order.status(),
                            "COMPLETION_REQUESTED",
                            seller.id(),
                            null,
                            null
                    );
                    notificationService.notifyCompletionRequested(order.buyerId(), order.id(), requestId);
                    return orderRepository.findPendingCompletionRequest(order.id())
                            .map(CompletionRequestResponse::from)
                            .orElseThrow(ApiExceptions::internalError);
                });
    }

    @Transactional
    public OrderResponse confirmCompletion(long orderId, long requestId, CurrentPrincipal buyer) {
        CompletionRequestRecord request = orderRepository.findCompletionRequestForUpdate(requestId)
                .orElseThrow(() -> ApiExceptions.notFound("完成确认请求不存在或不可见"));
        if (request.orderId() != orderId) {
            throw ApiExceptions.notFound("完成确认请求不存在或不可见");
        }
        TradeOrderRecord order = lockedOrder(orderId);
        requireBuyer(order, buyer);
        requireStatus(order, TradeOrderStatus.PAID_PENDING_MEETUP, "订单当前不能确认完成");
        if (request.status() != CompletionRequestStatus.PENDING) {
            throw ApiExceptions.conflict("完成确认请求已处理", Map.of("status", request.status().name()));
        }
        Instant now = Instant.now();
        if (now.isAfter(request.windowEndsAt())) {
            throw ApiExceptions.conflict("完成确认请求已过期", Map.of("requestId", request.id()));
        }

        orderRepository.markCompletionRequestConfirmed(request.id(), now);
        transitionOrder(
                order,
                TradeOrderStatus.COMPLETED_PENDING_SETTLEMENT,
                "BUYER_CONFIRMED_COMPLETION",
                buyer.id(),
                null,
                null,
                false
        );
        if (goodsRepository.markSoldFromOrder(order.goodsId(), order.id()) != 1) {
            throw ApiExceptions.conflict("商品售出状态不一致", Map.of("orderId", order.id()));
        }
        PaymentOrderRecord payment = orderRepository.findEscrowedPaymentByOrder(order.id())
                .orElseThrow(() -> ApiExceptions.conflict("订单尚未完成托管支付", Map.of("orderId", order.id())));
        orderRepository.createSettlement(
                generateNo("SET"),
                order.id(),
                payment.id(),
                payment.amount(),
                now,
                now.plusSeconds(SETTLEMENT_FREEZE_SECONDS)
        );
        notificationService.notifyOrderCompleted(order.buyerId(), order.sellerId(), order.id());
        return loadOrderResponse(orderId);
    }

    @Transactional
    public SettlementResponse advanceSettlement(long settlementId, CurrentPrincipal admin) {
        SettlementRecord settlement = orderRepository.findSettlementByIdForUpdate(settlementId)
                .orElseThrow(() -> ApiExceptions.notFound("结算记录不存在或不可见"));
        if (settlement.status() == SettlementStatus.SETTLED) {
            return SettlementResponse.from(settlement);
        }
        if (settlement.status() != SettlementStatus.PENDING && settlement.status() != SettlementStatus.FAILED) {
            throw ApiExceptions.conflict("结算当前不能推进", Map.of("status", settlement.status().name()));
        }
        TradeOrderRecord order = orderRepository.findOrderByIdForUpdate(settlement.orderId())
                .orElseThrow(() -> ApiExceptions.notFound("订单不存在或不可见"));
        Instant now = Instant.now();
        int attemptNo = orderRepository.nextSettlementAttemptNo(settlement.id());
        orderRepository.createSucceededSettlementAttempt(
                settlement.id(),
                attemptNo,
                settlement.settlementAmount(),
                admin.id(),
                now
        );
        orderRepository.markSettlementSettled(settlement.id(), now);
        if (order.status() == TradeOrderStatus.COMPLETED_PENDING_SETTLEMENT) {
            transitionOrder(
                    order,
                    TradeOrderStatus.COMPLETED,
                    "SETTLEMENT_SETTLED",
                    null,
                    admin.id(),
                    null,
                    false
            );
        }
        notificationService.notifySettlementStatusChanged(order.sellerId(), settlement.id(), SettlementStatus.SETTLED.name());
        return orderRepository.findSettlementByIdForUpdate(settlement.id())
                .map(SettlementResponse::from)
                .orElseThrow(ApiExceptions::internalError);
    }

    @Transactional
    public ReviewResponse createReview(long orderId, ReviewRequest request, CurrentPrincipal principal) {
        TradeOrderRecord order = lockedOrder(orderId);
        requireParticipant(order, principal);
        requireStatus(order, TradeOrderStatus.COMPLETED, "订单完成后才能评价");
        if (request == null) {
            throw ApiExceptions.validation("请填写评价内容", Map.of("body", "required"));
        }
        int rating = requireRating(request.rating());
        String content = optionalTrimmed(request.content(), MAX_REVIEW_CONTENT_LENGTH, "content", "评价内容最多 500 个字符");
        if (orderRepository.reviewExists(order.id(), principal.id())) {
            throw ApiExceptions.conflict("每个订单每位参与者最多评价一次", Map.of("orderId", order.id()));
        }
        long reviewedUserId = principal.id() == order.buyerId() ? order.sellerId() : order.buyerId();
        long reviewId = orderRepository.createReview(
                order.id(),
                principal.id(),
                reviewedUserId,
                rating,
                content,
                Instant.now()
        );
        return orderRepository.findReviewById(reviewId)
                .map(ReviewResponse::from)
                .orElseThrow(ApiExceptions::internalError);
    }

    public List<ReviewResponse> listReviews(long orderId, CurrentPrincipal principal) {
        TradeOrderRecord order = orderRepository.findOrderById(orderId)
                .orElseThrow(() -> ApiExceptions.notFound("订单不存在或不可见"));
        requireParticipant(order, principal);
        return orderRepository.listReviews(order.id())
                .stream()
                .map(ReviewResponse::from)
                .toList();
    }

    private TradeOrderRecord lockedOrder(long orderId) {
        return orderRepository.findOrderByIdForUpdate(orderId)
                .orElseThrow(() -> ApiExceptions.notFound("订单不存在或不可见"));
    }

    private OrderResponse loadOrderResponse(long orderId) {
        return orderRepository.findOrderById(orderId)
                .map(OrderResponse::from)
                .orElseThrow(ApiExceptions::internalError);
    }

    private void transitionOrder(
            TradeOrderRecord order,
            TradeOrderStatus nextStatus,
            String eventType,
            Long operatorUserId,
            Long operatorAdminId,
            String reason,
            boolean closeOrder
    ) {
        int updated = orderRepository.updateOrderStatus(order.id(), order.status(), nextStatus, Instant.now(), closeOrder);
        if (updated != 1) {
            throw ApiExceptions.conflict("订单状态已变化，请刷新后重试", Map.of("orderId", order.id()));
        }
        orderRepository.insertStateRecord(
                order.id(),
                order.status(),
                nextStatus,
                eventType,
                operatorUserId,
                operatorAdminId,
                reason
        );
    }

    private void requireParticipant(TradeOrderRecord order, CurrentPrincipal principal) {
        if (order.buyerId() != principal.id() && order.sellerId() != principal.id()) {
            throw ApiExceptions.notFound("订单不存在或不可见");
        }
    }

    private void requireBuyer(TradeOrderRecord order, CurrentPrincipal principal) {
        if (order.buyerId() != principal.id()) {
            throw ApiExceptions.notFound("订单不存在或不可见");
        }
    }

    private void requireSeller(TradeOrderRecord order, CurrentPrincipal principal) {
        if (order.sellerId() != principal.id()) {
            throw ApiExceptions.notFound("订单不存在或不可见");
        }
    }

    private void requireStatus(TradeOrderRecord order, TradeOrderStatus expected, String message) {
        if (order.status() != expected) {
            throw ApiExceptions.conflict(message, Map.of("status", order.status().name()));
        }
    }

    private long requirePositive(Long value, String field, String message) {
        if (value == null || value <= 0) {
            throw ApiExceptions.validation(message, Map.of("field", field));
        }
        return value;
    }

    private int requireRating(Integer rating) {
        if (rating == null || rating < 1 || rating > 5) {
            throw ApiExceptions.validation("评价星级必须是 1 到 5 的整数", Map.of("field", "rating"));
        }
        return rating;
    }

    private String reason(ReasonRequest request, String fallback) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            return fallback;
        }
        return optionalTrimmed(request.reason(), MAX_NOTE_LENGTH, "reason", "原因最多 500 个字符");
    }

    private String optionalTrimmed(String value, int maxLength, String field, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw ApiExceptions.validation(message, Map.of("field", field));
        }
        return trimmed;
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizePageSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    }

    private String generateNo(String prefix) {
        long randomPart = Math.abs(RANDOM.nextLong()) % 1_000_000_000L;
        return prefix.toUpperCase(Locale.ROOT) + Instant.now().toEpochMilli() + String.format("%09d", randomPart);
    }
}
