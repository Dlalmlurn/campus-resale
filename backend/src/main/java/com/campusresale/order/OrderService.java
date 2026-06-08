package com.campusresale.order;

import com.campusresale.conversation.AcceptedBargainQuote;
import com.campusresale.conversation.ConversationService;
import com.campusresale.files.FileRepository;
import com.campusresale.files.FileService;
import com.campusresale.goods.GoodsAuditStatus;
import com.campusresale.goods.GoodsRecord;
import com.campusresale.goods.GoodsRepository;
import com.campusresale.goods.GoodsStatus;
import com.campusresale.notification.NotificationService;
import com.campusresale.notification.NotificationType;
import com.campusresale.order.OrderRepository.OrderWriteData;
import com.campusresale.order.OrderRequests.CreateOrderRequest;
import com.campusresale.order.OrderRequests.DecideRefundRequest;
import com.campusresale.order.OrderRequests.ReasonRequest;
import com.campusresale.order.OrderRequests.RefundRequest;
import com.campusresale.order.OrderRequests.ReviewRequest;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.api.PageResponse;
import com.campusresale.platform.audit.AuditLogRepository;
import com.campusresale.platform.security.CurrentPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private static final Set<String> REFUND_TYPES = Set.of("FULL", "PARTIAL");

    private final OrderRepository orderRepository;
    private final GoodsRepository goodsRepository;
    private final ConversationService conversationService;
    private final NotificationService notificationService;
    private final PaymentProvider paymentProvider;
    private final FileService fileService;
    private final FileRepository fileRepository;
    private final AuditLogRepository auditLogRepository;

    public OrderService(
            OrderRepository orderRepository,
            GoodsRepository goodsRepository,
            ConversationService conversationService,
            NotificationService notificationService,
            PaymentProvider paymentProvider,
            FileService fileService,
            FileRepository fileRepository,
            AuditLogRepository auditLogRepository
    ) {
        this.orderRepository = orderRepository;
        this.goodsRepository = goodsRepository;
        this.conversationService = conversationService;
        this.notificationService = notificationService;
        this.paymentProvider = paymentProvider;
        this.fileService = fileService;
        this.fileRepository = fileRepository;
        this.auditLogRepository = auditLogRepository;
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
        processPaymentCallback(paymentProvider.simulateSuccessfulPayment(payment), buyer.id());
        return orderRepository.findLatestPaymentByOrder(order.id())
                .map(PaymentResponse::from)
                .orElseThrow(ApiExceptions::internalError);
    }

    @Transactional
    public PaymentResponse processPaymentCallback(PaymentProviderCallback callback, Long operatorUserId) {
        PaymentOrderRecord payment = orderRepository.findPaymentByIdForUpdate(callback.paymentOrderId())
                .orElseThrow(() -> ApiExceptions.notFound("支付单不存在"));
        TradeOrderRecord order = lockedOrder(payment.orderId());
        int inserted = orderRepository.insertPaymentCallbackIfAbsent(
                payment.id(),
                callback.provider(),
                callback.callbackNo(),
                sha256Hex(callback.payloadSummary()),
                callback.status(),
                callback.occurredAt()
        );
        if (inserted == 0 || payment.status() == PaymentOrderStatus.ESCROWED) {
            return PaymentResponse.from(payment);
        }
        if (!"SUCCEEDED".equals(callback.status())) {
            throw ApiExceptions.conflict("模拟支付回调状态不支持", Map.of("status", callback.status()));
        }
        if (callback.amount().compareTo(payment.amount()) != 0) {
            throw ApiExceptions.conflict("支付回调金额与支付单不一致", Map.of("paymentOrderId", payment.id()));
        }
        orderRepository.markPaymentEscrowed(payment.id(), callback.occurredAt());
        orderRepository.insertPaymentTransaction(
                callback.transactionNo(),
                payment.id(),
                payment.amount(),
                callback.occurredAt()
        );
        if (order.status() == TradeOrderStatus.PENDING_PAYMENT) {
            transitionOrder(order, TradeOrderStatus.PAID_PENDING_MEETUP, "PAYMENT_ESCROWED", operatorUserId, null, null, false);
            notificationService.notifyPaymentSucceeded(order.buyerId(), order.sellerId(), order.id());
        }
        auditLogRepository.recordOperation(null, "PAYMENT_CALLBACK_PROCESSED", "PAYMENT", payment.id(), null, callback, null, null, null, null, "SUCCESS", "SYSTEM");
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

    public List<PaymentTransactionResponse> paymentTransactions(long orderId, CurrentPrincipal principal) {
        TradeOrderRecord order = orderRepository.findOrderById(orderId)
                .orElseThrow(() -> ApiExceptions.notFound("订单不存在或不可见"));
        requireParticipant(order, principal);
        return orderRepository.listPaymentTransactionsByOrder(orderId)
                .stream()
                .map(PaymentTransactionResponse::from)
                .toList();
    }

    public SettlementResponse settlementStatus(long orderId, CurrentPrincipal principal) {
        TradeOrderRecord order = orderRepository.findOrderById(orderId)
                .orElseThrow(() -> ApiExceptions.notFound("订单不存在或不可见"));
        requireParticipant(order, principal);
        return orderRepository.findSettlementByOrder(orderId)
                .map(SettlementResponse::from)
                .orElseThrow(() -> ApiExceptions.notFound("结算记录不存在或不可见"));
    }

    public List<RefundResponse> refunds(long orderId, CurrentPrincipal principal) {
        TradeOrderRecord order = orderRepository.findOrderById(orderId)
                .orElseThrow(() -> ApiExceptions.notFound("订单不存在或不可见"));
        requireParticipant(order, principal);
        return orderRepository.listRefundsByOrder(order.id())
                .stream()
                .map(RefundResponse::from)
                .toList();
    }

    @Transactional
    public RefundResponse createRefund(long orderId, RefundRequest request, CurrentPrincipal principal) {
        if (request == null) {
            throw ApiExceptions.validation("请填写退款申请", Map.of("body", "required"));
        }
        TradeOrderRecord order = lockedOrder(orderId);
        requireParticipant(order, principal);
        if (order.status() != TradeOrderStatus.PAID_PENDING_MEETUP
                && order.status() != TradeOrderStatus.COMPLETED_PENDING_SETTLEMENT) {
            throw ApiExceptions.conflict("订单当前不支持发起退款", Map.of("status", order.status().name()));
        }
        PaymentOrderRecord payment = orderRepository.findEscrowedPaymentByOrder(order.id())
                .orElseThrow(() -> ApiExceptions.conflict("订单尚未完成托管支付", Map.of("orderId", order.id())));
        if (orderRepository.hasActiveRefund(order.id())) {
            throw ApiExceptions.conflict("已有退款申请正在处理", Map.of("orderId", order.id()));
        }
        String refundType = oneOf(required(request.refundType(), "refundType", "退款类型不能为空").toUpperCase(Locale.ROOT), REFUND_TYPES, "refundType");
        BigDecimal amount = parseMoney(request.amount(), "amount");
        BigDecimal unavailable = orderRepository.activeRefundAmount(payment.id(), null);
        BigDecimal available = payment.amount().subtract(unavailable);
        if (amount.compareTo(available) > 0) {
            throw ApiExceptions.validation("退款金额不能超过可退金额", Map.of("field", "amount", "available", money(available)));
        }
        if ("FULL".equals(refundType) && amount.compareTo(available) != 0) {
            throw ApiExceptions.validation("全额退款金额必须等于当前可退金额", Map.of("field", "amount", "available", money(available)));
        }
        String reason = required(request.reason(), "reason", "退款原因不能为空");
        if (reason.length() > 1000) {
            throw ApiExceptions.validation("退款原因最多 1000 个字符", Map.of("field", "reason"));
        }
        List<Long> evidenceFileIds = safeIds(request.evidenceFileIds());
        evidenceFileIds.forEach(fileId -> fileService.requireOwnedOrderEvidence(fileId, principal.id()));
        Instant now = Instant.now();
        long refundId = orderRepository.createRefund(
                generateNo("R"),
                order.id(),
                payment.id(),
                principal.id(),
                amount,
                refundType,
                reason,
                order.status(),
                now
        );
        orderRepository.attachRefundEvidence(refundId, evidenceFileIds);
        fileRepository.attachToBusiness(evidenceFileIds, "REFUND", refundId);
        transitionOrder(order, TradeOrderStatus.REFUND_PROCESSING, "REFUND_REQUESTED", principal.id(), null, reason, false);
        notificationService.create(order.sellerId(), NotificationType.REFUND_STATUS_CHANGED, "收到退款申请", "订单有新的退款申请需要处理。", "REFUND", refundId, "refund:" + refundId + ":seller:" + order.sellerId());
        notifyAdmins(NotificationType.REFUND_STATUS_CHANGED, "收到退款申请", "有新的退款申请等待审核。", "REFUND", refundId);
        return orderRepository.findRefundById(refundId)
                .map(RefundResponse::from)
                .orElseThrow(ApiExceptions::internalError);
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
        if (orderRepository.hasActiveRefund(order.id())) {
            throw ApiExceptions.conflict("存在处理中的退款，暂不能结算", Map.of("orderId", order.id()));
        }
        Instant now = Instant.now();
        orderRepository.markSettlementProcessing(settlement.id(), now);
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
        auditLogRepository.recordOperation(admin.id(), "SETTLEMENT_ADVANCE", "SETTLEMENT", settlement.id(), settlement, orderRepository.findSettlementByIdForUpdate(settlement.id()).orElse(null), null);
        return orderRepository.findSettlementByIdForUpdate(settlement.id())
                .map(SettlementResponse::from)
                .orElseThrow(ApiExceptions::internalError);
    }

    @Transactional
    public List<SettlementResponse> advanceDueSettlements(CurrentPrincipal admin) {
        List<Long> dueIds = orderRepository.listDueSettlementIds(Instant.now(), 20);
        return dueIds.stream()
                .map(id -> advanceSettlement(id, admin))
                .toList();
    }

    @Transactional
    public RefundResponse decideRefund(long refundId, DecideRefundRequest request, CurrentPrincipal admin, String ipAddress) {
        if (request == null) {
            throw ApiExceptions.validation("请填写退款审核意见", Map.of("body", "required"));
        }
        RefundOrderRecord before = orderRepository.findRefundByIdForUpdate(refundId)
                .orElseThrow(() -> ApiExceptions.notFound("退款记录不存在"));
        String decision = required(request.decision(), "decision", "退款决策不能为空").toUpperCase(Locale.ROOT);
        String note = optionalTrimmed(request.decisionNote(), 1000, "decisionNote", "退款处理说明最多 1000 个字符");
        if (note == null) {
            note = decision;
        }
        RefundResponse response = switch (decision) {
            case "APPROVE", "PROCESSING" -> markRefundProcessing(before, admin, note, ipAddress);
            case "REJECT", "CLOSE", "CLOSED" -> markRefundFinal(before, RefundOrderStatus.CLOSED, admin, note, null, "管理员关闭退款申请", ipAddress);
            case "REFUND_SUCCESS", "REFUNDED" -> markRefundFinal(before, RefundOrderStatus.REFUNDED, admin, note, "SIM-REFUND-" + before.refundNo(), null, ipAddress);
            case "REFUND_FAILED", "FAILED" -> markRefundFinal(before, RefundOrderStatus.FAILED, admin, note, null, "模拟退款失败", ipAddress);
            default -> throw ApiExceptions.validation("退款决策不支持", Map.of("field", "decision"));
        };
        notificationService.create(before.requestedByUserId(), NotificationType.REFUND_STATUS_CHANGED, "退款状态已更新", "退款申请状态已更新为 " + response.status() + "。", "REFUND", refundId, null);
        return response;
    }

    public PageResponse<PaymentResponse> adminPayments(int page, int pageSize) {
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizePageSize(pageSize);
        return new PageResponse<>(
                orderRepository.listPaymentsForAdmin(normalizedPage, normalizedPageSize).stream().map(PaymentResponse::from).toList(),
                normalizedPage,
                normalizedPageSize,
                orderRepository.countPaymentsForAdmin()
        );
    }

    public PageResponse<RefundResponse> adminRefunds(int page, int pageSize, CurrentPrincipal admin, String ipAddress) {
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizePageSize(pageSize);
        List<RefundResponse> items = orderRepository.listRefundsForAdmin(normalizedPage, normalizedPageSize)
                .stream()
                .peek(refund -> {
                    if (!refund.evidenceFileIds().isEmpty()) {
                        auditLogRepository.recordSensitiveAccess(admin.id(), "REFUND_EVIDENCE", refund.id(), "查看退款证据列表", "SUCCESS", ipAddress);
                    }
                })
                .map(RefundResponse::from)
                .toList();
        return new PageResponse<>(items, normalizedPage, normalizedPageSize, orderRepository.countRefundsForAdmin());
    }

    public PageResponse<SettlementResponse> adminSettlements(int page, int pageSize) {
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizePageSize(pageSize);
        return new PageResponse<>(
                orderRepository.listSettlements(normalizedPage, normalizedPageSize).stream().map(SettlementResponse::from).toList(),
                normalizedPage,
                normalizedPageSize,
                orderRepository.countSettlements()
        );
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

    public CompletionRequestResponse getPendingCompletion(long orderId, CurrentPrincipal principal) {
        TradeOrderRecord order = orderRepository.findOrderById(orderId)
                .orElseThrow(() -> ApiExceptions.notFound("订单不存在或不可见"));
        requireParticipant(order, principal);
        return orderRepository.findPendingCompletionRequest(order.id())
                .map(CompletionRequestResponse::from)
                .orElseThrow(() -> ApiExceptions.notFound("没有待确认的完成请求"));
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

    private RefundResponse markRefundProcessing(RefundOrderRecord before, CurrentPrincipal admin, String note, String ipAddress) {
        int updated = orderRepository.markRefundProcessing(before.id(), admin.id(), note, Instant.now());
        if (updated != 1 && before.status() != RefundOrderStatus.PROCESSING) {
            throw ApiExceptions.conflict("退款状态已变化，请刷新后重试", Map.of("refundId", before.id()));
        }
        RefundOrderRecord after = orderRepository.findRefundById(before.id()).orElseThrow(ApiExceptions::internalError);
        auditLogRepository.recordOperation(admin.id(), "REFUND_REVIEW", "REFUND", before.id(), before, after, ipAddress);
        return RefundResponse.from(after);
    }

    private RefundResponse markRefundFinal(
            RefundOrderRecord before,
            RefundOrderStatus status,
            CurrentPrincipal admin,
            String note,
            String providerRefundNo,
            String failureReason,
            String ipAddress
    ) {
        if (status != RefundOrderStatus.REFUNDED && status != RefundOrderStatus.FAILED && status != RefundOrderStatus.CLOSED) {
            throw ApiExceptions.validation("退款终态不支持", Map.of("field", "status"));
        }
        TradeOrderRecord order = orderRepository.findOrderByIdForUpdate(before.orderId())
                .orElseThrow(() -> ApiExceptions.notFound("订单不存在或不可见"));
        if (before.paymentOrderId() == null) {
            throw ApiExceptions.conflict("退款未关联有效支付单", Map.of("refundId", before.id()));
        }
        PaymentOrderRecord payment = orderRepository.findPaymentByIdForUpdate(before.paymentOrderId())
                .orElseThrow(() -> ApiExceptions.conflict("退款未关联有效支付单", Map.of("refundId", before.id())));
        if (status == RefundOrderStatus.REFUNDED) {
            BigDecimal activeOther = orderRepository.activeRefundAmount(payment.id(), before.id());
            if (activeOther.add(before.amount()).compareTo(payment.amount()) > 0) {
                throw ApiExceptions.conflict("累计退款金额超过支付金额", Map.of("refundId", before.id()));
            }
        }
        int updated = orderRepository.markRefundFinal(before.id(), status, admin.id(), note, providerRefundNo, failureReason, Instant.now());
        if (updated != 1 && before.status() != status) {
            throw ApiExceptions.conflict("退款状态已变化，请刷新后重试", Map.of("refundId", before.id()));
        }
        RefundOrderRecord after = orderRepository.findRefundById(before.id()).orElseThrow(ApiExceptions::internalError);
        if (status == RefundOrderStatus.REFUNDED) {
            applyRefundedOrderState(order, payment, after, admin);
        } else {
            restoreOrderAfterRefund(order, after, admin, "退款未完成，订单恢复到退款前状态");
        }
        auditLogRepository.recordOperation(admin.id(), "REFUND_DECIDE", "REFUND", before.id(), before, after, ipAddress);
        return RefundResponse.from(after);
    }

    private void applyRefundedOrderState(TradeOrderRecord order, PaymentOrderRecord payment, RefundOrderRecord refund, CurrentPrincipal admin) {
        BigDecimal completedRefundAmount = orderRepository.completedRefundAmount(payment.id());
        boolean fullRefunded = "FULL".equals(refund.refundType()) || completedRefundAmount.compareTo(payment.amount()) >= 0;
        if (fullRefunded) {
            if (order.status() == TradeOrderStatus.REFUND_PROCESSING) {
                transitionOrder(order, TradeOrderStatus.CLOSED, "REFUND_COMPLETED_FULL", null, admin.id(), "全额退款完成", true);
            }
            orderRepository.markSettlementClosedByOrder(order.id(), "全额退款完成，关闭结算", Instant.now());
            if (refund.statusBeforeRefund() == TradeOrderStatus.PAID_PENDING_MEETUP) {
                goodsRepository.releaseReservation(order.goodsId(), order.id());
            }
        } else {
            BigDecimal remaining = payment.amount().subtract(completedRefundAmount).setScale(2, RoundingMode.HALF_UP);
            orderRepository.updateSettlementAmountByOrder(order.id(), remaining, Instant.now());
            restoreOrderAfterRefund(order, refund, admin, "部分退款完成，订单恢复结算链路");
        }
    }

    private void restoreOrderAfterRefund(TradeOrderRecord order, RefundOrderRecord refund, CurrentPrincipal admin, String reason) {
        if (order.status() != TradeOrderStatus.REFUND_PROCESSING) {
            return;
        }
        TradeOrderStatus target = refund.statusBeforeRefund() == null
                ? TradeOrderStatus.PAID_PENDING_MEETUP
                : refund.statusBeforeRefund();
        transitionOrder(order, target, "REFUND_ORDER_RESTORED", null, admin.id(), reason, false);
    }

    private void notifyAdmins(NotificationType type, String title, String content, String relatedType, long relatedId) {
        for (long adminId : List.of(1L, 2L)) {
            try {
                notificationService.create(adminId, type, title, content, relatedType, relatedId, relatedType.toLowerCase(Locale.ROOT) + ":" + relatedId + ":admin:" + adminId);
            } catch (RuntimeException ignored) {
                // Demo seeds can vary; missing admin notifications must not block the transaction fact.
            }
        }
    }

    private long requirePositive(Long value, String field, String message) {
        if (value == null || value <= 0) {
            throw ApiExceptions.validation(message, Map.of("field", field));
        }
        return value;
    }

    private String required(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw ApiExceptions.validation(message, Map.of("field", field));
        }
        return value.trim();
    }

    private String oneOf(String value, Set<String> allowed, String field) {
        if (!allowed.contains(value)) {
            throw ApiExceptions.validation("字段取值不支持", Map.of("field", field, "value", value));
        }
        return value;
    }

    private BigDecimal parseMoney(String value, String field) {
        try {
            BigDecimal amount = new BigDecimal(required(value, field, "金额不能为空")).setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiExceptions.validation("金额必须大于 0", Map.of("field", field));
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw ApiExceptions.validation("金额格式不正确", Map.of("field", field));
        }
    }

    private String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private List<Long> safeIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(6)
                .toList();
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

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw ApiExceptions.internalError();
        }
    }
}
