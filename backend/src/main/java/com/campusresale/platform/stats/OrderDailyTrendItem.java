package com.campusresale.platform.stats;

import java.time.LocalDate;

/**
 * 近 30 天每日订单趋势，对应 v_order_daily_trend 视图中的一行。
 */
public record OrderDailyTrendItem(
        LocalDate statDate,
        long totalCreated,
        long completedCount,
        long cancelledCount
) {
}
