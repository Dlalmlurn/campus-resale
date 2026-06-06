package com.campusresale.platform.stats;

import java.math.BigDecimal;

/**
 * N2 后台统计看板聚合响应，整合所有核心运营指标。
 * 
 * <p>前端后台首页直接绑定此对象，一次请求获取全部看板数据。
 */
public record DashboardStatsResponse(
        OrderStats orders,
        PaymentStats payments,
        SettlementStats settlements,
        GoodsStats goods,
        ReviewStats reviews,
        UserStats users,
        CampusAuthStats campusAuths,
        OperationLogStats operationLogs
) {
    /** 订单统计（来自 v_order_stats 视图） */
    public record OrderStats(
            long totalOrders,
            long pendingSellerConfirm,
            long pendingPayment,
            long paidPendingMeetup,
            long completedPendingSettlement,
            long completed,
            long cancelled,
            long closed,
            long disputeProcessing,
            long refundProcessing,
            BigDecimal activeFrozenAmount,
            BigDecimal completedAmount
    ) {}

    /** 支付统计（来自 v_payment_stats 视图） */
    public record PaymentStats(
            long totalPayments,
            long pending,
            long processing,
            long escrowed,
            long failed,
            long closed,
            BigDecimal escrowedAmount,
            BigDecimal totalProcessedAmount
    ) {}

    /** 结算统计（来自 v_settlement_stats 视图） */
    public record SettlementStats(
            long totalSettlements,
            long pending,
            long processing,
            long settled,
            long failed,
            long closed,
            BigDecimal totalSettledAmount,
            BigDecimal pendingSettlementAmount
    ) {}

    /** 商品统计（来自 v_goods_stats 视图） */
    public record GoodsStats(
            long totalGoods,
            long draft,
            long pendingReview,
            long onSale,
            long reserved,
            long sold,
            long offShelf,
            long deleted,
            long auditPending  // 审核队列待处理量，前端 Badge 数字来源
    ) {}

    /** 评价统计（来自 v_review_stats 视图） */
    public record ReviewStats(
            long totalReviews,
            long submitted,
            long visible,
            long hidden,
            long excluded,
            Double avgRating,  // 平均评分，可能为 NULL
            long fiveStar,
            long fourStar,
            long threeStar,
            long lowRating
    ) {}

    /** 用户统计（来自 v_user_stats 视图） */
    public record UserStats(
            long totalUsers,
            long activeUsers,
            long lockedUsers,
            long disabledUsers,
            long newThisMonth,
            long newToday
    ) {}

    /** 校园认证统计（来自 v_campus_auth_stats 视图） */
    public record CampusAuthStats(
            long totalVerifications,
            long draft,
            long accumulating,
            long pendingReview,  // 待审核队列量，前端 Badge 数字来源
            long approved,
            long rejected,
            long invalid
    ) {}

    /** 操作日志统计（来自 v_operation_log_stats 视图） */
    public record OperationLogStats(
            long totalLogs,
            long successCount,
            long failureCount,
            long partialCount,
            long todayCount,
            long thisMonthCount
    ) {}
}
