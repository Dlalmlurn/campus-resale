package com.campusresale.order;

import com.campusresale.order.OrderRequests.CreateOrderRequest;
import com.campusresale.order.OrderRequests.ReasonRequest;
import com.campusresale.order.OrderRequests.ReviewRequest;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.api.PageResponse;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.CurrentPrincipalContext;
import com.campusresale.platform.security.RequireLogin;
import com.campusresale.platform.security.RequireTradeEligible;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @RequireTradeEligible
    @PostMapping
    public OrderResponse create(
            @RequestBody CreateOrderRequest request,
            HttpServletRequest servletRequest
    ) {
        return orderService.create(request, principal(servletRequest));
    }

    @RequireLogin
    @GetMapping
    public PageResponse<OrderResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest servletRequest
    ) {
        return orderService.list(status, page, pageSize, principal(servletRequest));
    }

    @RequireLogin
    @GetMapping("/{id}")
    public OrderResponse detail(@PathVariable long id, HttpServletRequest servletRequest) {
        return orderService.detail(id, principal(servletRequest));
    }

    @RequireTradeEligible
    @PostMapping("/{id}/seller-confirm")
    public OrderResponse sellerConfirm(@PathVariable long id, HttpServletRequest servletRequest) {
        return orderService.sellerConfirm(id, principal(servletRequest));
    }

    @RequireTradeEligible
    @PostMapping("/{id}/seller-reject")
    public OrderResponse sellerReject(
            @PathVariable long id,
            @RequestBody(required = false) ReasonRequest request,
            HttpServletRequest servletRequest
    ) {
        return orderService.sellerReject(id, request, principal(servletRequest));
    }

    @RequireTradeEligible
    @PostMapping("/{id}/buyer-cancel")
    public OrderResponse buyerCancel(
            @PathVariable long id,
            @RequestBody(required = false) ReasonRequest request,
            HttpServletRequest servletRequest
    ) {
        return orderService.buyerCancel(id, request, principal(servletRequest));
    }

    @RequireTradeEligible
    @PostMapping("/{id}/payments/simulate")
    public PaymentResponse simulatePayment(@PathVariable long id, HttpServletRequest servletRequest) {
        return orderService.simulatePayment(id, principal(servletRequest));
    }

    @RequireLogin
    @GetMapping("/{id}/payment")
    public PaymentResponse paymentStatus(@PathVariable long id, HttpServletRequest servletRequest) {
        return orderService.paymentStatus(id, principal(servletRequest));
    }

    @RequireTradeEligible
    @PostMapping("/{id}/completion-requests")
    public CompletionRequestResponse requestCompletion(@PathVariable long id, HttpServletRequest servletRequest) {
        return orderService.requestCompletion(id, principal(servletRequest));
    }

    @RequireTradeEligible
    @PostMapping("/{id}/completion-requests/{requestId}/confirm")
    public OrderResponse confirmCompletion(
            @PathVariable long id,
            @PathVariable long requestId,
            HttpServletRequest servletRequest
    ) {
        return orderService.confirmCompletion(id, requestId, principal(servletRequest));
    }

    @RequireTradeEligible
    @PostMapping("/{id}/reviews")
    public ReviewResponse createReview(
            @PathVariable long id,
            @RequestBody ReviewRequest request,
            HttpServletRequest servletRequest
    ) {
        return orderService.createReview(id, request, principal(servletRequest));
    }

    @RequireLogin
    @GetMapping("/{id}/reviews")
    public List<ReviewResponse> listReviews(@PathVariable long id, HttpServletRequest servletRequest) {
        return orderService.listReviews(id, principal(servletRequest));
    }

    private CurrentPrincipal principal(HttpServletRequest request) {
        return CurrentPrincipalContext.get(request)
                .orElseThrow(ApiExceptions::authRequired);
    }
}
