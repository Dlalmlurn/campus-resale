package com.campusresale.goods;

import com.campusresale.goods.GoodsRequests.ReviewRequest;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.api.PageResponse;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.CurrentPrincipalContext;
import com.campusresale.platform.security.RequireRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})
@RestController
@RequestMapping("/api/admin/goods")
public class AdminGoodsController {

    private final GoodsService goodsService;

    public AdminGoodsController(GoodsService goodsService) {
        this.goodsService = goodsService;
    }

    @GetMapping
    public PageResponse<GoodsSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String auditStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return goodsService.adminList(status, auditStatus, page, pageSize);
    }

    @PostMapping("/{id}/approve")
    public GoodsSummary approve(
            @PathVariable long id,
            @Valid @RequestBody(required = false) ReviewRequest request,
            HttpServletRequest servletRequest
    ) {
        return goodsService.approve(id, request, principal(servletRequest));
    }

    @PostMapping("/{id}/reject")
    public GoodsSummary reject(
            @PathVariable long id,
            @Valid @RequestBody(required = false) ReviewRequest request,
            HttpServletRequest servletRequest
    ) {
        return goodsService.reject(id, request, principal(servletRequest));
    }

    private CurrentPrincipal principal(HttpServletRequest request) {
        return CurrentPrincipalContext.get(request)
                .orElseThrow(ApiExceptions::authRequired);
    }
}
