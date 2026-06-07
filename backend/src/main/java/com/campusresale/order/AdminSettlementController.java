package com.campusresale.order;

import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.api.PageResponse;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.CurrentPrincipalContext;
import com.campusresale.platform.security.RequireRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})
@RequestMapping("/api/admin")
public class AdminSettlementController {

    private final OrderService orderService;

    public AdminSettlementController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/payments")
    public PageResponse<PaymentResponse> payments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return orderService.adminPayments(page, pageSize);
    }

    @GetMapping("/refunds")
    public PageResponse<RefundResponse> refunds(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest servletRequest
    ) {
        return orderService.adminRefunds(page, pageSize, principal(servletRequest), ip(servletRequest));
    }

    @PostMapping("/refunds/{id}/decide")
    public RefundResponse decideRefund(
            @PathVariable long id,
            @RequestBody OrderRequests.DecideRefundRequest body,
            HttpServletRequest servletRequest
    ) {
        return orderService.decideRefund(id, body, principal(servletRequest), ip(servletRequest));
    }

    @GetMapping("/settlements")
    public PageResponse<SettlementResponse> settlements(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return orderService.adminSettlements(page, pageSize);
    }

    @PostMapping("/settlements/{id}/advance")
    public SettlementResponse advance(@PathVariable long id, HttpServletRequest servletRequest) {
        return orderService.advanceSettlement(id, principal(servletRequest));
    }

    @PostMapping("/settlements/advance-due")
    public java.util.List<SettlementResponse> advanceDue(HttpServletRequest servletRequest) {
        return orderService.advanceDueSettlements(principal(servletRequest));
    }

    private CurrentPrincipal principal(HttpServletRequest request) {
        return CurrentPrincipalContext.get(request)
                .orElseThrow(ApiExceptions::authRequired);
    }

    private String ip(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
