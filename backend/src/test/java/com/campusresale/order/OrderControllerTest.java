package com.campusresale.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusresale.identity.application.SessionLookupService;
import com.campusresale.order.OrderRequests.CreateOrderRequest;
import com.campusresale.platform.api.GlobalApiExceptionHandler;
import com.campusresale.platform.api.PageResponse;
import com.campusresale.platform.security.AuthorizationInterceptor;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.CurrentPrincipalContext;
import com.campusresale.platform.security.OriginCsrfInterceptor;
import com.campusresale.platform.security.TradeEligibilityChecker;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@Import({GlobalApiExceptionHandler.class, AuthorizationInterceptor.class})
class OrderControllerTest {

    private static final String LOCAL_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OriginCsrfInterceptor originCsrfInterceptor;

    @MockBean
    private SessionLookupService sessionLookupService;

    @MockBean
    private TradeEligibilityChecker tradeEligibilityChecker;

    @BeforeEach
    void allowSecurityInfrastructureByDefault() throws Exception {
        when(originCsrfInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(sessionLookupService.loadByRawToken(nullable(String.class))).thenReturn(Optional.empty());
        when(tradeEligibilityChecker.canTrade(any())).thenReturn(true);
    }

    @Test
    void createRequiresTradeEligibility() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.ORIGIN, LOCAL_ORIGIN)
                        .contentType("application/json")
                        .content("""
                                {"goodsId":100}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void createDelegatesToServiceWhenTradeEligible() throws Exception {
        CurrentPrincipal principal = principal(2L);
        when(orderService.create(any(CreateOrderRequest.class), eq(principal))).thenReturn(orderResponse());

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.ORIGIN, LOCAL_ORIGIN)
                        .requestAttr(CurrentPrincipalContext.REQUEST_ATTRIBUTE, principal)
                        .contentType("application/json")
                        .content("""
                                {"goodsId":100,"note":"今晚可面交"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(500))
                .andExpect(jsonPath("$.status").value("PENDING_SELLER_CONFIRM"));
    }

    @Test
    void listRequiresLoginAndReturnsCurrentUsersOrders() throws Exception {
        CurrentPrincipal principal = principal(2L);
        when(orderService.list("PENDING_PAYMENT", 1, 10, principal))
                .thenReturn(new PageResponse<>(List.of(orderResponse()), 1, 10, 1));

        mockMvc.perform(get("/api/orders")
                        .requestAttr(CurrentPrincipalContext.REQUEST_ATTRIBUTE, principal)
                        .param("status", "PENDING_PAYMENT")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].goodsTitle").value("九成新显示器"));
    }

    @Test
    void simulatePaymentUsesTradeEligibilityGuard() throws Exception {
        CurrentPrincipal principal = principal(2L);
        when(orderService.simulatePayment(500L, principal)).thenReturn(paymentResponse());

        mockMvc.perform(post("/api/orders/500/payments/simulate")
                        .header(HttpHeaders.ORIGIN, LOCAL_ORIGIN)
                        .requestAttr(CurrentPrincipalContext.REQUEST_ATTRIBUTE, principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESCROWED"));
    }

    private OrderResponse orderResponse() {
        return new OrderResponse(
                500L,
                "ORD-1",
                100L,
                "九成新显示器",
                10L,
                null,
                null,
                new OrderResponse.ParticipantSummary(2L, "买家"),
                new OrderResponse.ParticipantSummary(1L, "卖家"),
                "399.00",
                "PENDING_SELLER_CONFIRM",
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

    private PaymentResponse paymentResponse() {
        return new PaymentResponse(
                900L,
                "PAY-1",
                500L,
                "399.00",
                "ESCROWED",
                "SIMULATED",
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T01:00:00Z"),
                null
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
