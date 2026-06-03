package com.campusresale.order;

import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.CurrentPrincipalContext;
import com.campusresale.platform.security.RequireRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settlements")
public class AdminSettlementController {

    private final OrderService orderService;

    public AdminSettlementController(OrderService orderService) {
        this.orderService = orderService;
    }

    @RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})
    @PostMapping("/{id}/advance")
    public SettlementResponse advance(@PathVariable long id, HttpServletRequest servletRequest) {
        return orderService.advanceSettlement(id, principal(servletRequest));
    }

    private CurrentPrincipal principal(HttpServletRequest request) {
        return CurrentPrincipalContext.get(request)
                .orElseThrow(ApiExceptions::authRequired);
    }
}
