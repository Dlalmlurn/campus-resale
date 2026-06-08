package com.campusresale.platform.stats;

import com.campusresale.platform.security.RequireRole;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * N2 维度 C：管理员后台统计看板接口。
 *
 * <p>接口设计：拆分为 2 个端点，而非单一 dashboard 端点。
 *
 * <ul>
 *   <li>{@code GET /api/admin/stats/dashboard} — 全量聚合指标（8 个统计维度），
 *       前端首页直接绑定，一次请求获取所有卡片数字。</li>
 *   <li>{@code GET /api/admin/stats/order-trend} — 近 30 天每日趋势（多行列表），
 *       独立端点是因为这是列表型数据，与聚合指标合并会破坏 JSON 结构，
 *       且趋势图可以按需懒加载，不阻塞首屏渲染。</li>
 * </ul>
 *
 * <p>两个接口均通过类级 {@code @RequireRole} 注解自动接入 AuthorizationInterceptor，
 * 只有 SUPER_ADMIN 或 CONTENT_ADMIN 可访问，无需在方法内手动鉴权。
 */
@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})
@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    private final AdminStatsRepository statsRepository;

    public AdminStatsController(AdminStatsRepository statsRepository) {
        this.statsRepository = statsRepository;
    }

    /**
     * GET /api/admin/stats/dashboard
     *
     * <p>返回全量运营指标聚合对象，包含订单、支付、结算、商品、评价、用户、认证、操作日志 8 个维度。
     * 所有数字直接来自 V14 创建的统计视图，后端无复杂聚合逻辑。
     *
     * <p>前端首页绑定建议：
     * <pre>
     * - 订单待处理量 = orders.pendingSellerConfirm + orders.pendingPayment
     * - 商品审核 badge = goods.auditPending
     * - 认证审核 badge = campusAuths.pendingReview
     * - 今日新用户    = users.newToday
     * </pre>
     */
    @GetMapping("/dashboard")
    public DashboardStatsResponse dashboard() {
        return new DashboardStatsResponse(
                statsRepository.queryOrderStats(),
                statsRepository.queryPaymentStats(),
                statsRepository.querySettlementStats(),
                statsRepository.queryGoodsStats(),
                statsRepository.queryReviewStats(),
                statsRepository.queryUserStats(),
                statsRepository.queryCampusAuthStats(),
                statsRepository.queryOperationLogStats()
        );
    }

    /**
     * GET /api/admin/stats/order-trend
     *
     * <p>返回近 30 天每日订单趋势列表，每行包含：建单量、完成量、取消量。
     * 数据按日期倒序（最新日期在前）。
     * 可用于折线图或纯 CSS 趋势条，不依赖任何 BI 组件。
     */
    @GetMapping("/order-trend")
    public List<OrderDailyTrendItem> orderTrend() {
        return statsRepository.queryOrderDailyTrend();
    }
}
