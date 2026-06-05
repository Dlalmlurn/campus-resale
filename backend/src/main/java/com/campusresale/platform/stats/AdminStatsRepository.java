package com.campusresale.platform.stats;

import com.campusresale.platform.stats.DashboardStatsResponse.CampusAuthStats;
import com.campusresale.platform.stats.DashboardStatsResponse.GoodsStats;
import com.campusresale.platform.stats.DashboardStatsResponse.OperationLogStats;
import com.campusresale.platform.stats.DashboardStatsResponse.OrderStats;
import com.campusresale.platform.stats.DashboardStatsResponse.PaymentStats;
import com.campusresale.platform.stats.DashboardStatsResponse.ReviewStats;
import com.campusresale.platform.stats.DashboardStatsResponse.SettlementStats;
import com.campusresale.platform.stats.DashboardStatsResponse.UserStats;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * N2 运营统计 DAO。
 *
 * <p>每个方法直接执行 {@code SELECT * FROM v_xxx} 读取 V14 创建的统计视图，
 * 不包含任何业务聚合逻辑，符合"务实与降级原则"。
 *
 * <p>全部使用 {@link NamedParameterJdbcTemplate}，不引入 JPA/Hibernate/MyBatis。
 */
@Repository
public class AdminStatsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AdminStatsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // =========================================================
    // 订单统计（v_order_stats 是单行聚合视图）
    // =========================================================

    public OrderStats queryOrderStats() {
        String sql = "SELECT * FROM v_order_stats";
        return querySingleRow(sql, AdminStatsRepository::mapOrderStats);
    }

    // =========================================================
    // 近 30 天每日趋势（v_order_daily_trend 是多行视图）
    // =========================================================

    public List<OrderDailyTrendItem> queryOrderDailyTrend() {
        String sql = "SELECT stat_date, total_created, completed_count, cancelled_count FROM v_order_daily_trend";
        return jdbc.query(sql, EmptySqlParameterSource.INSTANCE, AdminStatsRepository::mapTrendItem);
    }

    // =========================================================
    // 支付统计（单行聚合）
    // =========================================================

    public PaymentStats queryPaymentStats() {
        String sql = "SELECT * FROM v_payment_stats";
        return querySingleRow(sql, AdminStatsRepository::mapPaymentStats);
    }

    // =========================================================
    // 结算统计（单行聚合）
    // =========================================================

    public SettlementStats querySettlementStats() {
        String sql = "SELECT * FROM v_settlement_stats";
        return querySingleRow(sql, AdminStatsRepository::mapSettlementStats);
    }

    // =========================================================
    // 商品统计（单行聚合）
    // =========================================================

    public GoodsStats queryGoodsStats() {
        String sql = "SELECT * FROM v_goods_stats";
        return querySingleRow(sql, AdminStatsRepository::mapGoodsStats);
    }

    // =========================================================
    // 评价统计（单行聚合）
    // =========================================================

    public ReviewStats queryReviewStats() {
        String sql = "SELECT * FROM v_review_stats";
        return querySingleRow(sql, AdminStatsRepository::mapReviewStats);
    }

    // =========================================================
    // 用户统计（单行聚合）
    // =========================================================

    public UserStats queryUserStats() {
        String sql = "SELECT * FROM v_user_stats";
        return querySingleRow(sql, AdminStatsRepository::mapUserStats);
    }

    // =========================================================
    // 校园认证统计（单行聚合）
    // =========================================================

    public CampusAuthStats queryCampusAuthStats() {
        String sql = "SELECT * FROM v_campus_auth_stats";
        return querySingleRow(sql, AdminStatsRepository::mapCampusAuthStats);
    }

    // =========================================================
    // 操作日志统计（单行聚合）
    // =========================================================

    public OperationLogStats queryOperationLogStats() {
        String sql = "SELECT * FROM v_operation_log_stats";
        return querySingleRow(sql, AdminStatsRepository::mapOperationLogStats);
    }

    // =========================================================
    // 工具方法
    // =========================================================

    /**
     * 对单行聚合视图的统一查询：视图为空时返回全零的默认值。
     * 单行聚合视图（如 v_order_stats）在无数据时会返回一行全 NULL 而不是空行；
     * 此方法直接映射，让 NULL 由 mapXxx 的 getLong / getBigDecimal 自行处理。
     */
    private <T> T querySingleRow(String sql, org.springframework.jdbc.core.RowMapper<T> mapper) {
        try {
            return jdbc.queryForObject(sql, EmptySqlParameterSource.INSTANCE, mapper);
        } catch (EmptyResultDataAccessException e) {
            // 视图确实无数据行时（理论上单行聚合视图不会触发此分支，防御性处理）
            return null;
        }
    }

    // =========================================================
    // ResultSet 映射方法
    // =========================================================

    private static OrderStats mapOrderStats(ResultSet rs, int row) throws SQLException {
        return new OrderStats(
                rs.getLong("total_orders"),
                rs.getLong("pending_seller_confirm"),
                rs.getLong("pending_payment"),
                rs.getLong("paid_pending_meetup"),
                rs.getLong("completed_pending_settlement"),
                rs.getLong("completed"),
                rs.getLong("cancelled"),
                rs.getLong("closed"),
                rs.getLong("dispute_processing"),
                rs.getLong("refund_processing"),
                nullableDecimal(rs, "active_frozen_amount"),
                nullableDecimal(rs, "completed_amount")
        );
    }

    private static OrderDailyTrendItem mapTrendItem(ResultSet rs, int row) throws SQLException {
        Date sqlDate = rs.getDate("stat_date");
        return new OrderDailyTrendItem(
                sqlDate != null ? sqlDate.toLocalDate() : null,
                rs.getLong("total_created"),
                rs.getLong("completed_count"),
                rs.getLong("cancelled_count")
        );
    }

    private static PaymentStats mapPaymentStats(ResultSet rs, int row) throws SQLException {
        return new PaymentStats(
                rs.getLong("total_payments"),
                rs.getLong("pending"),
                rs.getLong("processing"),
                rs.getLong("escrowed"),
                rs.getLong("failed"),
                rs.getLong("closed"),
                nullableDecimal(rs, "escrowed_amount"),
                nullableDecimal(rs, "total_processed_amount")
        );
    }

    private static SettlementStats mapSettlementStats(ResultSet rs, int row) throws SQLException {
        return new SettlementStats(
                rs.getLong("total_settlements"),
                rs.getLong("pending"),
                rs.getLong("processing"),
                rs.getLong("settled"),
                rs.getLong("failed"),
                rs.getLong("closed"),
                nullableDecimal(rs, "total_settled_amount"),
                nullableDecimal(rs, "pending_settlement_amount")
        );
    }

    private static GoodsStats mapGoodsStats(ResultSet rs, int row) throws SQLException {
        return new GoodsStats(
                rs.getLong("total_goods"),
                rs.getLong("draft"),
                rs.getLong("pending_review"),
                rs.getLong("on_sale"),
                rs.getLong("reserved"),
                rs.getLong("sold"),
                rs.getLong("off_shelf"),
                rs.getLong("deleted"),
                rs.getLong("audit_pending")
        );
    }

    private static ReviewStats mapReviewStats(ResultSet rs, int row) throws SQLException {
        // avg_rating 可能为 NULL（没有任何可见评价时）
        double avgRating = rs.getDouble("avg_rating");
        Double avgRatingBoxed = rs.wasNull() ? null : avgRating;
        return new ReviewStats(
                rs.getLong("total_reviews"),
                rs.getLong("submitted"),
                rs.getLong("visible"),
                rs.getLong("hidden"),
                rs.getLong("excluded"),
                avgRatingBoxed,
                rs.getLong("five_star"),
                rs.getLong("four_star"),
                rs.getLong("three_star"),
                rs.getLong("low_rating")
        );
    }

    private static UserStats mapUserStats(ResultSet rs, int row) throws SQLException {
        return new UserStats(
                rs.getLong("total_users"),
                rs.getLong("active_users"),
                rs.getLong("locked_users"),
                rs.getLong("disabled_users"),
                rs.getLong("new_this_month"),
                rs.getLong("new_today")
        );
    }

    private static CampusAuthStats mapCampusAuthStats(ResultSet rs, int row) throws SQLException {
        return new CampusAuthStats(
                rs.getLong("total_verifications"),
                rs.getLong("draft"),
                rs.getLong("accumulating"),
                rs.getLong("pending_review"),
                rs.getLong("approved"),
                rs.getLong("rejected"),
                rs.getLong("invalid")
        );
    }

    private static OperationLogStats mapOperationLogStats(ResultSet rs, int row) throws SQLException {
        return new OperationLogStats(
                rs.getLong("total_logs"),
                rs.getLong("success_count"),
                rs.getLong("failure_count"),
                rs.getLong("partial_count"),
                rs.getLong("today_count"),
                rs.getLong("this_month_count")
        );
    }

    /**
     * 读取可为 NULL 的 NUMERIC 列，返回 ZERO 而不是 null 以保证前端渲染安全。
     */
    private static BigDecimal nullableDecimal(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value != null ? value : BigDecimal.ZERO;
    }
}
