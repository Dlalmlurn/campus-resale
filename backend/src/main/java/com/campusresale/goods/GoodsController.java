// 文件功能：提供商品草稿、提交审核、我的商品、公开列表和商品详情 API。
package com.campusresale.goods;

import com.campusresale.goods.GoodsRequests.UpsertRequest;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.api.PageResponse;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.CurrentPrincipalContext;
import com.campusresale.platform.security.RequireLogin;
import com.campusresale.platform.security.RequireTradeEligible;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/goods")
public class GoodsController {

    private final GoodsService goodsService;

    public GoodsController(GoodsService goodsService) {
        this.goodsService = goodsService;
    }

    @RequireTradeEligible
    @PostMapping("/drafts")
    public GoodsSummary createDraft(
            @Valid @RequestBody UpsertRequest request,
            HttpServletRequest servletRequest
    ) {
        return goodsService.createDraft(principal(servletRequest), request);
    }

    @RequireLogin
    @PatchMapping("/{id}")
    public GoodsSummary update(
            @PathVariable long id,
            @Valid @RequestBody UpsertRequest request,
            HttpServletRequest servletRequest
    ) {
        return goodsService.update(id, principal(servletRequest), request);
    }

    @RequireTradeEligible
    @PostMapping("/{id}/submit")
    public GoodsSummary submit(@PathVariable long id, HttpServletRequest servletRequest) {
        return goodsService.submit(id, principal(servletRequest));
    }

    @RequireLogin
    @GetMapping("/mine")
    public PageResponse<GoodsSummary> mine(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String auditStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest servletRequest
    ) {
        return goodsService.mine(status, auditStatus, page, pageSize, principal(servletRequest));
    }

    @GetMapping
    public PageResponse<GoodsSummary> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String conditionLevel,
            @RequestParam(required = false) Long placeId,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return goodsService.publicList(keyword, categoryId, minPrice, maxPrice, conditionLevel, placeId, sort, page, pageSize);
    }

    @GetMapping("/{id}")
    public GoodsSummary detail(@PathVariable long id, HttpServletRequest servletRequest) {
        return goodsService.detail(id, CurrentPrincipalContext.get(servletRequest));
    }

    private CurrentPrincipal principal(HttpServletRequest request) {
        return CurrentPrincipalContext.get(request)
                .orElseThrow(ApiExceptions::authRequired);
    }
}
