package com.campusresale.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusresale.conversation.AcceptedBargainQuote;
import com.campusresale.conversation.ConversationService;
import com.campusresale.goods.ConditionLevel;
import com.campusresale.goods.GoodsAuditStatus;
import com.campusresale.goods.GoodsRecord;
import com.campusresale.goods.GoodsRepository;
import com.campusresale.goods.GoodsStatus;
import com.campusresale.notification.NotificationService;
import com.campusresale.order.OrderRequests.CreateOrderRequest;
import com.campusresale.order.OrderRequests.ReviewRequest;
import com.campusresale.platform.api.ApiException;
import com.campusresale.platform.security.CurrentPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

    private final OrderRepository orderRepository = org.mockito.Mockito.mock(OrderRepository.class);
    private final GoodsRepository goodsRepository = org.mockito.Mockito.mock(GoodsRepository.class);
    private final ConversationService conversationService = org.mockito.Mockito.mock(ConversationService.class);
    private final NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);
    private final OrderService service = new OrderService(orderRepository, goodsRepository, conversationService, notificationService);

    @Test
    void createOrderReservesGoodsAndNotifiesSeller() {
        GoodsRecord goods = goods(GoodsStatus.ON_SALE, GoodsAuditStatus.APPROVED);
        when(goodsRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(goods));
        when(goodsRepository.currentOccupiedOrderId(100L)).thenReturn(null);
        when(orderRepository.createOrder(any())).thenReturn(500L);
        when(goodsRepository.reserveForOrder(100L, 500L)).thenReturn(1);
        when(orderRepository.findOrderById(500L)).thenReturn(Optional.of(order(TradeOrderStatus.PENDING_SELLER_CONFIRM)));

        OrderResponse response = service.create(
                new CreateOrderRequest(100L, null, null, null, null, "今晚可面交"),
                principal(2L)
        );

        assertThat(response.status()).isEqualTo("PENDING_SELLER_CONFIRM");
        verify(goodsRepository).reserveForOrder(100L, 500L);
        verify(orderRepository).insertStateRecord(500L, null, TradeOrderStatus.PENDING_SELLER_CONFIRM, "ORDER_CREATED", 2L, null, null);
        verify(notificationService).notifyOrderCreated(1L, 500L, "九成新显示器");
    }

    @Test
    void createOrderUsesAcceptedBargainAmountFromConversation() {
        GoodsRecord goods = goods(GoodsStatus.ON_SALE, GoodsAuditStatus.APPROVED);
        when(goodsRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(goods));
        when(goodsRepository.currentOccupiedOrderId(100L)).thenReturn(null);
        when(conversationService.validateAcceptedBargainForOrder(100L, 2L, 88L))
                .thenReturn(new AcceptedBargainQuote(20L, 88L, new BigDecimal("360.00")));
        when(orderRepository.createOrder(any())).thenReturn(500L);
        when(goodsRepository.reserveForOrder(100L, 500L)).thenReturn(1);
        when(orderRepository.findOrderById(500L)).thenReturn(Optional.of(order(TradeOrderStatus.PENDING_SELLER_CONFIRM)));

        service.create(new CreateOrderRequest(100L, 88L, null, null, null, null), principal(2L));

        verify(orderRepository).createOrder(org.mockito.ArgumentMatchers.argThat(data ->
                data.conversationId().equals(20L)
                        && data.acceptedBargainCardId().equals(88L)
                        && data.frozenAmount().compareTo(new BigDecimal("360.00")) == 0
        ));
    }

    @Test
    void simulatePaymentEscrowsPaymentAndMovesOrderForward() {
        TradeOrderRecord order = order(TradeOrderStatus.PENDING_PAYMENT);
        PaymentOrderRecord pendingPayment = payment(PaymentOrderStatus.PENDING);
        PaymentOrderRecord escrowedPayment = payment(PaymentOrderStatus.ESCROWED);
        when(orderRepository.findOrderByIdForUpdate(500L)).thenReturn(Optional.of(order));
        when(goodsRepository.currentOccupiedOrderId(100L)).thenReturn(500L);
        when(orderRepository.findLatestPaymentByOrderForUpdate(500L)).thenReturn(Optional.of(pendingPayment));
        when(orderRepository.updateOrderStatus(eq(500L), eq(TradeOrderStatus.PENDING_PAYMENT), eq(TradeOrderStatus.PAID_PENDING_MEETUP), any(), eq(false)))
                .thenReturn(1);
        when(orderRepository.findLatestPaymentByOrder(500L)).thenReturn(Optional.of(escrowedPayment));

        PaymentResponse response = service.simulatePayment(500L, principal(2L));

        assertThat(response.status()).isEqualTo("ESCROWED");
        verify(orderRepository).markPaymentEscrowed(eq(900L), any());
        verify(orderRepository).insertPaymentTransaction(eq("SIM-TXN-PAY-1"), eq(900L), eq(new BigDecimal("399.00")), any());
        verify(notificationService).notifyPaymentSucceeded(2L, 1L, 500L);
    }

    @Test
    void confirmCompletionCreatesSettlementAndMarksGoodsSold() {
        TradeOrderRecord order = order(TradeOrderStatus.PAID_PENDING_MEETUP);
        CompletionRequestRecord request = completionRequest(CompletionRequestStatus.PENDING);
        when(orderRepository.findCompletionRequestForUpdate(700L)).thenReturn(Optional.of(request));
        when(orderRepository.findOrderByIdForUpdate(500L)).thenReturn(Optional.of(order));
        when(orderRepository.updateOrderStatus(eq(500L), eq(TradeOrderStatus.PAID_PENDING_MEETUP), eq(TradeOrderStatus.COMPLETED_PENDING_SETTLEMENT), any(), eq(false)))
                .thenReturn(1);
        when(goodsRepository.markSoldFromOrder(100L, 500L)).thenReturn(1);
        when(orderRepository.findEscrowedPaymentByOrder(500L)).thenReturn(Optional.of(payment(PaymentOrderStatus.ESCROWED)));
        when(orderRepository.createSettlement(any(), eq(500L), eq(900L), eq(new BigDecimal("399.00")), any(), any()))
                .thenReturn(300L);
        when(orderRepository.findOrderById(500L)).thenReturn(Optional.of(order(TradeOrderStatus.COMPLETED_PENDING_SETTLEMENT)));

        OrderResponse response = service.confirmCompletion(500L, 700L, principal(2L));

        assertThat(response.status()).isEqualTo("COMPLETED_PENDING_SETTLEMENT");
        verify(orderRepository).markCompletionRequestConfirmed(eq(700L), any());
        verify(goodsRepository).markSoldFromOrder(100L, 500L);
        verify(notificationService).notifyOrderCompleted(2L, 1L, 500L);
    }

    @Test
    void createReviewRequiresCompletedOrderAndSingleReviewPerParticipant() {
        when(orderRepository.findOrderByIdForUpdate(500L)).thenReturn(Optional.of(order(TradeOrderStatus.COMPLETED)));
        when(orderRepository.reviewExists(500L, 2L)).thenReturn(false);
        when(orderRepository.createReview(eq(500L), eq(2L), eq(1L), eq(5), eq("交易顺利"), any()))
                .thenReturn(800L);
        when(orderRepository.findReviewById(800L)).thenReturn(Optional.of(review()));

        ReviewResponse response = service.createReview(500L, new ReviewRequest(5, "交易顺利"), principal(2L));

        assertThat(response.reviewedUserId()).isEqualTo(1L);
        assertThat(response.rating()).isEqualTo(5);
    }

    @Test
    void detailHidesOrderFromNonParticipant() {
        when(orderRepository.findOrderById(500L)).thenReturn(Optional.of(order(TradeOrderStatus.PENDING_PAYMENT)));

        assertThatThrownBy(() -> service.detail(500L, principal(99L)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("NOT_FOUND"));
    }

    private GoodsRecord goods(GoodsStatus status, GoodsAuditStatus auditStatus) {
        return new GoodsRecord(
                100L,
                1L,
                "卖家",
                1L,
                "DIGITAL",
                "数码电子",
                "九成新显示器",
                "自用显示器，配件齐全。",
                ConditionLevel.LIKE_NEW,
                new BigDecimal("399.00"),
                1L,
                "图书馆门口",
                "工作日晚上",
                status,
                auditStatus,
                10L,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z")
        );
    }

    private TradeOrderRecord order(TradeOrderStatus status) {
        return new TradeOrderRecord(
                500L,
                "ORD-1",
                100L,
                "九成新显示器",
                10L,
                null,
                null,
                2L,
                "买家",
                1L,
                "卖家",
                new BigDecimal("399.00"),
                status,
                1L,
                "图书馆",
                "门口",
                null,
                "今晚可面交",
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z"),
                null
        );
    }

    private PaymentOrderRecord payment(PaymentOrderStatus status) {
        return new PaymentOrderRecord(
                900L,
                "PAY-1",
                500L,
                new BigDecimal("399.00"),
                status,
                "SIMULATED",
                Instant.parse("2026-06-01T00:00:00Z"),
                status == PaymentOrderStatus.ESCROWED ? Instant.parse("2026-06-01T01:00:00Z") : null,
                null
        );
    }

    private CompletionRequestRecord completionRequest(CompletionRequestStatus status) {
        return new CompletionRequestRecord(
                700L,
                500L,
                1L,
                2L,
                status,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60),
                null,
                Instant.now().minusSeconds(60),
                Instant.now().minusSeconds(60)
        );
    }

    private ReviewRecord review() {
        return new ReviewRecord(
                800L,
                500L,
                2L,
                1L,
                5,
                "交易顺利",
                ReviewStatus.VISIBLE,
                Instant.parse("2026-06-01T02:00:00Z"),
                Instant.parse("2026-06-04T02:00:00Z"),
                Instant.parse("2026-06-01T02:00:00Z")
        );
    }

    private CurrentPrincipal principal(long id) {
        return new CurrentPrincipal(
                id,
                "user" + id,
                "User " + id,
                "ACTIVE",
                Set.of("REGISTERED_USER", "VERIFIED_STUDENT"),
                100L,
                Instant.now().plusSeconds(60),
                Instant.now().plusSeconds(120)
        );
    }
}
