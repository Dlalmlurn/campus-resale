package com.campusresale.governance;

import com.campusresale.governance.N3Responses.AppealResponse;
import com.campusresale.governance.N3Responses.CreditRecordResponse;
import com.campusresale.governance.N3Responses.CreditSummaryResponse;
import com.campusresale.governance.N3Responses.FavoriteResponse;
import com.campusresale.governance.N3Responses.FollowResponse;
import com.campusresale.governance.N3Responses.PenaltyResponse;
import com.campusresale.governance.N3Responses.RefundResponse;
import com.campusresale.governance.N3Responses.ReportResponse;
import com.campusresale.governance.N3Responses.UserSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class N3GovernanceRepository {

    private static final String REPORT_SELECT = """
            SELECT r.id,
                   r.reporter_id,
                   reporter.nickname AS reporter_nickname,
                   r.target_type,
                   r.target_id,
                   r.reason_type,
                   r.description,
                   r.status,
                   r.priority,
                   r.handled_by_admin_id,
                   r.handled_at,
                   r.handling_note,
                   r.created_at,
                   COALESCE(array_agg(ref.file_id ORDER BY ref.file_id) FILTER (WHERE ref.file_id IS NOT NULL), '{}') AS evidence_file_ids
            FROM reports r
            JOIN users reporter ON reporter.id = r.reporter_id
            LEFT JOIN report_evidence_files ref ON ref.report_id = r.id
            """;

    private static final String REPORT_GROUP = """
            GROUP BY r.id, reporter.nickname
            """;

    private static final String APPEAL_SELECT = """
            SELECT a.id,
                   a.report_id,
                   a.appellant_id,
                   appellant.nickname AS appellant_nickname,
                   a.description,
                   a.status,
                   a.reviewed_by_admin_id,
                   a.reviewed_at,
                   a.review_note,
                   a.created_at,
                   COALESCE(array_agg(aef.file_id ORDER BY aef.file_id) FILTER (WHERE aef.file_id IS NOT NULL), '{}') AS evidence_file_ids
            FROM appeals a
            JOIN users appellant ON appellant.id = a.appellant_id
            LEFT JOIN appeal_evidence_files aef ON aef.appeal_id = a.id
            """;

    private static final String APPEAL_GROUP = """
            GROUP BY a.id, appellant.nickname
            """;

    private static final String REFUND_SELECT = """
            SELECT ro.id,
                   ro.refund_no,
                   ro.order_id,
                   ro.payment_order_id,
                   ro.requested_by_user_id,
                   requester.nickname AS requester_nickname,
                   ro.amount,
                   ro.refund_type,
                   ro.reason,
                   ro.status,
                   ro.decision_by_admin_id,
                   ro.decision_note,
                   ro.processed_at,
                   ro.created_at
            FROM refund_orders ro
            JOIN users requester ON requester.id = ro.requested_by_user_id
            """;

    private static final String PENALTY_SELECT = """
            SELECT p.id,
                   p.user_id,
                   u.nickname AS user_nickname,
                   p.report_id,
                   p.appeal_id,
                   p.penalty_type,
                   p.reason,
                   p.status,
                   p.created_by_admin_id,
                   p.lifted_by_admin_id,
                   p.lifted_at,
                   p.created_at
            FROM penalty_records p
            JOIN users u ON u.id = p.user_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public N3GovernanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long createReport(long reporterId, String targetType, long targetId, String reasonType, String description, List<Long> evidenceFileIds) {
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO reports (reporter_id, target_type, target_id, reason_type, description, priority)
                        VALUES (?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                reporterId,
                targetType,
                targetId,
                reasonType,
                description,
                "FRAUD".equals(reasonType) || "SAFETY".equals(reasonType) ? "HIGH" : "NORMAL"
        );
        insertEvidence("report_evidence_files", "report_id", id, evidenceFileIds);
        return id == null ? 0 : id;
    }

    public Optional<ReportResponse> findReport(long id) {
        return jdbcTemplate.query(REPORT_SELECT + " WHERE r.id = ? " + REPORT_GROUP,
                new ReportRowMapper(),
                id
        ).stream().findFirst();
    }

    public List<ReportResponse> listReportsByUser(long userId) {
        return jdbcTemplate.query(REPORT_SELECT + " WHERE r.reporter_id = ? " + REPORT_GROUP + " ORDER BY r.created_at DESC, r.id DESC LIMIT 30",
                new ReportRowMapper(),
                userId
        );
    }

    public List<ReportResponse> listReportsForAdmin() {
        return jdbcTemplate.query(REPORT_SELECT + REPORT_GROUP + " ORDER BY CASE r.status WHEN 'PENDING' THEN 0 WHEN 'PROCESSING' THEN 1 ELSE 2 END, r.priority DESC, r.created_at DESC LIMIT 50",
                new ReportRowMapper()
        );
    }

    public List<ReportResponse> listPendingReportsForAdmin() {
        return jdbcTemplate.query(REPORT_SELECT + " WHERE r.status IN ('PENDING', 'PROCESSING') " + REPORT_GROUP + " ORDER BY r.priority DESC, r.created_at DESC LIMIT 20",
                new ReportRowMapper()
        );
    }

    public Optional<ReportResponse> updateReport(long reportId, long adminId, String status, String note, Instant now) {
        jdbcTemplate.update("""
                        UPDATE reports
                        SET status = ?,
                            handled_by_admin_id = ?,
                            handled_at = ?,
                            handling_note = ?
                        WHERE id = ?
                        """,
                status,
                adminId,
                Timestamp.from(now),
                note,
                reportId
        );
        return findReport(reportId);
    }

    public long createAppeal(long reportId, long appellantId, String description, List<Long> evidenceFileIds) {
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO appeals (report_id, appellant_id, description)
                        VALUES (?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                reportId,
                appellantId,
                description
        );
        insertEvidence("appeal_evidence_files", "appeal_id", id, evidenceFileIds);
        return id == null ? 0 : id;
    }

    public Optional<AppealResponse> findAppeal(long id) {
        return jdbcTemplate.query(APPEAL_SELECT + " WHERE a.id = ? " + APPEAL_GROUP,
                new AppealRowMapper(),
                id
        ).stream().findFirst();
    }

    public List<AppealResponse> listAppealsByUser(long userId) {
        return jdbcTemplate.query(APPEAL_SELECT + " WHERE a.appellant_id = ? " + APPEAL_GROUP + " ORDER BY a.created_at DESC, a.id DESC LIMIT 30",
                new AppealRowMapper(),
                userId
        );
    }

    public List<AppealResponse> listAppealsForAdmin() {
        return jdbcTemplate.query(APPEAL_SELECT + APPEAL_GROUP + " ORDER BY CASE a.status WHEN 'PENDING_REVIEW' THEN 0 ELSE 1 END, a.created_at DESC LIMIT 50",
                new AppealRowMapper()
        );
    }

    public List<AppealResponse> listPendingAppealsForAdmin() {
        return jdbcTemplate.query(APPEAL_SELECT + " WHERE a.status = 'PENDING_REVIEW' " + APPEAL_GROUP + " ORDER BY a.created_at DESC LIMIT 20",
                new AppealRowMapper()
        );
    }

    public Optional<AppealResponse> updateAppeal(long appealId, long adminId, String status, String note, Instant now) {
        jdbcTemplate.update("""
                        UPDATE appeals
                        SET status = ?,
                            reviewed_by_admin_id = ?,
                            reviewed_at = ?,
                            review_note = ?
                        WHERE id = ?
                        """,
                status,
                adminId,
                Timestamp.from(now),
                note,
                appealId
        );
        return findAppeal(appealId);
    }

    public long createRefund(String refundNo, long orderId, Long paymentOrderId, long requesterId, BigDecimal amount, String refundType, String reason) {
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO refund_orders (refund_no, order_id, payment_order_id, requested_by_user_id, amount, refund_type, reason)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                refundNo,
                orderId,
                paymentOrderId,
                requesterId,
                amount,
                refundType,
                reason
        );
        return id == null ? 0 : id;
    }

    public Optional<RefundResponse> findRefund(long id) {
        return jdbcTemplate.query(REFUND_SELECT + " WHERE ro.id = ?",
                new RefundRowMapper(),
                id
        ).stream().findFirst();
    }

    public List<RefundResponse> listRefundsByUser(long userId) {
        return jdbcTemplate.query(REFUND_SELECT + " WHERE ro.requested_by_user_id = ? ORDER BY ro.created_at DESC, ro.id DESC LIMIT 30",
                new RefundRowMapper(),
                userId
        );
    }

    public List<RefundResponse> listRefundsForAdmin() {
        return jdbcTemplate.query(REFUND_SELECT + " ORDER BY CASE ro.status WHEN 'PENDING' THEN 0 WHEN 'PROCESSING' THEN 1 ELSE 2 END, ro.created_at DESC LIMIT 50",
                new RefundRowMapper()
        );
    }

    public List<RefundResponse> listPendingRefundsForAdmin() {
        return jdbcTemplate.query(REFUND_SELECT + " WHERE ro.status IN ('PENDING', 'PROCESSING') ORDER BY ro.created_at DESC LIMIT 20",
                new RefundRowMapper()
        );
    }

    public Optional<RefundResponse> updateRefund(long refundId, long adminId, String status, String note, Instant now) {
        jdbcTemplate.update("""
                        UPDATE refund_orders
                        SET status = ?,
                            decision_by_admin_id = ?,
                            decision_note = ?,
                            processed_at = CASE WHEN ? IN ('REFUNDED', 'FAILED', 'CLOSED') THEN ? ELSE processed_at END
                        WHERE id = ?
                        """,
                status,
                adminId,
                note,
                status,
                Timestamp.from(now),
                refundId
        );
        return findRefund(refundId);
    }

    public long createPenalty(long userId, Long reportId, Long appealId, String penaltyType, String reason, long adminId) {
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO penalty_records (user_id, report_id, appeal_id, penalty_type, reason, created_by_admin_id)
                        VALUES (?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                userId,
                reportId,
                appealId,
                penaltyType,
                reason,
                adminId
        );
        if ("ACCOUNT_LOCK".equals(penaltyType)) {
            jdbcTemplate.update("UPDATE users SET account_status = 'LOCKED', updated_at = now() WHERE id = ?", userId);
        }
        return id == null ? 0 : id;
    }

    public Optional<PenaltyResponse> findPenalty(long id) {
        return jdbcTemplate.query(PENALTY_SELECT + " WHERE p.id = ?",
                new PenaltyRowMapper(),
                id
        ).stream().findFirst();
    }

    public List<PenaltyResponse> listActivePenaltiesForAdmin() {
        return jdbcTemplate.query(PENALTY_SELECT + " WHERE p.status = 'ACTIVE' ORDER BY p.created_at DESC LIMIT 30",
                new PenaltyRowMapper()
        );
    }

    public Optional<PenaltyResponse> liftPenalty(long penaltyId, long adminId, Instant now) {
        jdbcTemplate.update("""
                        UPDATE penalty_records
                        SET status = 'LIFTED',
                            lifted_by_admin_id = ?,
                            lifted_at = ?
                        WHERE id = ?
                        """,
                adminId,
                Timestamp.from(now),
                penaltyId
        );
        jdbcTemplate.update("""
                        UPDATE users
                        SET account_status = 'ACTIVE',
                            updated_at = now()
                        WHERE id = (
                            SELECT user_id
                            FROM penalty_records
                            WHERE id = ?
                              AND penalty_type = 'ACCOUNT_LOCK'
                        )
                          AND NOT EXISTS (
                              SELECT 1
                              FROM penalty_records p
                              WHERE p.user_id = users.id
                                AND p.status = 'ACTIVE'
                                AND p.penalty_type = 'ACCOUNT_LOCK'
                          )
                        """,
                penaltyId
        );
        return findPenalty(penaltyId);
    }

    public void upsertFavorite(long userId, long goodsId) {
        jdbcTemplate.update("""
                        INSERT INTO favorites (user_id, goods_id)
                        VALUES (?, ?)
                        ON CONFLICT (user_id, goods_id) DO NOTHING
                        """,
                userId,
                goodsId
        );
    }

    public void removeFavorite(long userId, long goodsId) {
        jdbcTemplate.update("DELETE FROM favorites WHERE user_id = ? AND goods_id = ?", userId, goodsId);
    }

    public boolean isFavorited(long userId, long goodsId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM favorites WHERE user_id = ? AND goods_id = ?", Long.class, userId, goodsId);
        return count != null && count > 0;
    }

    public List<FavoriteResponse> listFavorites(long userId) {
        return jdbcTemplate.query("""
                        SELECT f.id,
                               f.goods_id,
                               g.title AS goods_title,
                               g.list_price,
                               g.seller_id,
                               seller.nickname AS seller_nickname,
                               f.created_at
                        FROM favorites f
                        JOIN goods g ON g.id = f.goods_id
                        JOIN users seller ON seller.id = g.seller_id
                        WHERE f.user_id = ?
                        ORDER BY f.created_at DESC
                        LIMIT 30
                        """,
                (rs, rowNum) -> new FavoriteResponse(
                        rs.getLong("id"),
                        rs.getLong("goods_id"),
                        rs.getString("goods_title"),
                        money(rs.getBigDecimal("list_price")),
                        new UserSummary(rs.getLong("seller_id"), rs.getString("seller_nickname")),
                        instant(rs, "created_at")
                ),
                userId
        );
    }

    public void upsertFollow(long followerId, long followedUserId) {
        jdbcTemplate.update("""
                        INSERT INTO follows (follower_id, followed_user_id)
                        VALUES (?, ?)
                        ON CONFLICT (follower_id, followed_user_id) DO NOTHING
                        """,
                followerId,
                followedUserId
        );
    }

    public void removeFollow(long followerId, long followedUserId) {
        jdbcTemplate.update("DELETE FROM follows WHERE follower_id = ? AND followed_user_id = ?", followerId, followedUserId);
    }

    public boolean isFollowing(long followerId, long followedUserId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM follows WHERE follower_id = ? AND followed_user_id = ?", Long.class, followerId, followedUserId);
        return count != null && count > 0;
    }

    public List<FollowResponse> listFollows(long userId) {
        return jdbcTemplate.query("""
                        SELECT f.id,
                               f.followed_user_id,
                               followed.nickname,
                               f.created_at
                        FROM follows f
                        JOIN users followed ON followed.id = f.followed_user_id
                        WHERE f.follower_id = ?
                        ORDER BY f.created_at DESC
                        LIMIT 30
                        """,
                (rs, rowNum) -> new FollowResponse(
                        rs.getLong("id"),
                        new UserSummary(rs.getLong("followed_user_id"), rs.getString("nickname")),
                        instant(rs, "created_at")
                ),
                userId
        );
    }

    public CreditSummaryResponse creditSummary(long userId) {
        Integer fulfilled = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)::int
                        FROM trade_orders
                        WHERE (buyer_id = ? OR seller_id = ?)
                          AND status IN ('COMPLETED', 'COMPLETED_PENDING_SETTLEMENT')
                        """,
                Integer.class,
                userId,
                userId
        );
        Integer positive = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)::int
                        FROM reviews
                        WHERE reviewed_user_id = ?
                          AND rating >= 4
                          AND status IN ('SUBMITTED', 'VISIBLE')
                        """,
                Integer.class,
                userId
        );
        Integer negative = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)::int
                        FROM penalty_records
                        WHERE user_id = ?
                          AND status = 'ACTIVE'
                        """,
                Integer.class,
                userId
        );
        int fulfillmentCount = fulfilled == null ? 0 : fulfilled;
        int positiveReviewCount = positive == null ? 0 : positive;
        int negativeEventCount = negative == null ? 0 : negative;
        int score = Math.max(0, Math.min(100, 80 + fulfillmentCount * 2 + positiveReviewCount * 3 - negativeEventCount * 15));
        String level = score >= 90 ? "A" : score >= 75 ? "B" : score >= 60 ? "C" : "D";
        List<String> tags = new ArrayList<>();
        if (fulfillmentCount > 0) {
            tags.add("有完成交易记录");
        }
        if (positiveReviewCount > 0) {
            tags.add("获得好评");
        }
        if (negativeEventCount == 0) {
            tags.add("暂无有效处罚");
        }
        if (tags.isEmpty()) {
            tags.add("新用户信用样本积累中");
        }
        Instant now = Instant.now();
        upsertCreditSummary(userId, fulfillmentCount, fulfillmentCount, positiveReviewCount, negativeEventCount, tags, score, level, now);
        return new CreditSummaryResponse(
                userId,
                fulfillmentCount,
                fulfillmentCount,
                positiveReviewCount,
                negativeEventCount,
                tags,
                score,
                level,
                listCreditRecords(userId),
                now
        );
    }

    public void insertCreditRecord(long userId, String sourceType, Long sourceId, String reason, int delta, String label, Long adminId) {
        jdbcTemplate.update("""
                        INSERT INTO credit_records (
                            user_id,
                            source_type,
                            source_id,
                            reason,
                            internal_delta_value,
                            public_label,
                            created_by_admin_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                userId,
                sourceType,
                sourceId,
                reason,
                delta,
                label,
                adminId
        );
    }

    public boolean targetExists(String targetType, long targetId) {
        String table = switch (targetType) {
            case "GOODS" -> "goods";
            case "ORDER" -> "trade_orders";
            case "USER" -> "users";
            default -> null;
        };
        if (table == null) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ?", Long.class, targetId);
        return count != null && count > 0;
    }

    public boolean goodsExists(long goodsId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM goods WHERE id = ? AND is_deleted = FALSE", Long.class, goodsId);
        return count != null && count > 0;
    }

    public boolean userExists(long userId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Long.class, userId);
        return count != null && count > 0;
    }

    public Optional<OrderSnapshot> orderSnapshot(long orderId) {
        return jdbcTemplate.query("""
                        SELECT o.id,
                               o.buyer_id,
                               o.seller_id,
                               o.frozen_amount,
                               po.id AS payment_order_id
                        FROM trade_orders o
                        LEFT JOIN LATERAL (
                            SELECT id
                            FROM payment_orders
                            WHERE order_id = o.id
                            ORDER BY created_at DESC, id DESC
                            LIMIT 1
                        ) po ON TRUE
                        WHERE o.id = ?
                        """,
                (rs, rowNum) -> new OrderSnapshot(
                        rs.getLong("id"),
                        rs.getLong("buyer_id"),
                        rs.getLong("seller_id"),
                        rs.getBigDecimal("frozen_amount"),
                        nullableLong(rs, "payment_order_id")
                ),
                orderId
        ).stream().findFirst();
    }

    private void insertEvidence(String table, String ownerColumn, Long ownerId, List<Long> fileIds) {
        if (ownerId == null || fileIds == null) {
            return;
        }
        for (Long fileId : fileIds.stream().distinct().toList()) {
            if (fileId != null && fileId > 0) {
                jdbcTemplate.update("INSERT INTO " + table + " (" + ownerColumn + ", file_id) VALUES (?, ?) ON CONFLICT DO NOTHING", ownerId, fileId);
            }
        }
    }

    private void upsertCreditSummary(long userId, int fulfillment, int onTime, int positive, int negative, List<String> tags, int score, String level, Instant now) {
        jdbcTemplate.update("""
                        INSERT INTO credit_summaries (
                            user_id,
                            fulfillment_count,
                            on_time_meetup_count,
                            positive_review_count,
                            negative_event_count,
                            public_tags_json,
                            internal_score,
                            internal_level,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
                        ON CONFLICT (user_id) DO UPDATE
                        SET fulfillment_count = EXCLUDED.fulfillment_count,
                            on_time_meetup_count = EXCLUDED.on_time_meetup_count,
                            positive_review_count = EXCLUDED.positive_review_count,
                            negative_event_count = EXCLUDED.negative_event_count,
                            public_tags_json = EXCLUDED.public_tags_json,
                            internal_score = EXCLUDED.internal_score,
                            internal_level = EXCLUDED.internal_level,
                            updated_at = EXCLUDED.updated_at
                        """,
                userId,
                fulfillment,
                onTime,
                positive,
                negative,
                tagsJson(tags),
                score,
                level,
                Timestamp.from(now)
        );
    }

    private List<CreditRecordResponse> listCreditRecords(long userId) {
        return jdbcTemplate.query("""
                        SELECT id,
                               source_type,
                               source_id,
                               reason,
                               internal_delta_value,
                               public_label,
                               created_at
                        FROM credit_records
                        WHERE user_id = ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT 10
                        """,
                (rs, rowNum) -> new CreditRecordResponse(
                        rs.getLong("id"),
                        rs.getString("source_type"),
                        nullableLong(rs, "source_id"),
                        rs.getString("reason"),
                        rs.getInt("internal_delta_value"),
                        rs.getString("public_label"),
                        instant(rs, "created_at")
                ),
                userId
        );
    }

    private static String tagsJson(List<String> tags) {
        return "[\"" + String.join("\",\"", tags.stream().map(tag -> tag.replace("\\", "\\\\").replace("\"", "\\\"")).toList()) + "\"]";
    }

    private static List<Long> longArray(ResultSet rs, String column) throws SQLException {
        java.sql.Array array = rs.getArray(column);
        if (array == null) {
            return List.of();
        }
        Object raw = array.getArray();
        if (raw instanceof Long[] values) {
            return Arrays.asList(values);
        }
        if (raw instanceof Number[] values) {
            return Arrays.stream(values).map(Number::longValue).toList();
        }
        return List.of();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public record OrderSnapshot(long id, long buyerId, long sellerId, BigDecimal frozenAmount, Long paymentOrderId) {
    }

    private static final class ReportRowMapper implements RowMapper<ReportResponse> {
        @Override
        public ReportResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ReportResponse(
                    rs.getLong("id"),
                    new UserSummary(rs.getLong("reporter_id"), rs.getString("reporter_nickname")),
                    rs.getString("target_type"),
                    rs.getLong("target_id"),
                    rs.getString("reason_type"),
                    rs.getString("description"),
                    rs.getString("status"),
                    rs.getString("priority"),
                    nullableLong(rs, "handled_by_admin_id"),
                    instant(rs, "handled_at"),
                    rs.getString("handling_note"),
                    longArray(rs, "evidence_file_ids"),
                    instant(rs, "created_at")
            );
        }
    }

    private static final class AppealRowMapper implements RowMapper<AppealResponse> {
        @Override
        public AppealResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AppealResponse(
                    rs.getLong("id"),
                    rs.getLong("report_id"),
                    new UserSummary(rs.getLong("appellant_id"), rs.getString("appellant_nickname")),
                    rs.getString("description"),
                    rs.getString("status"),
                    nullableLong(rs, "reviewed_by_admin_id"),
                    instant(rs, "reviewed_at"),
                    rs.getString("review_note"),
                    longArray(rs, "evidence_file_ids"),
                    instant(rs, "created_at")
            );
        }
    }

    private static final class RefundRowMapper implements RowMapper<RefundResponse> {
        @Override
        public RefundResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RefundResponse(
                    rs.getLong("id"),
                    rs.getString("refund_no"),
                    rs.getLong("order_id"),
                    nullableLong(rs, "payment_order_id"),
                    new UserSummary(rs.getLong("requested_by_user_id"), rs.getString("requester_nickname")),
                    money(rs.getBigDecimal("amount")),
                    rs.getString("refund_type"),
                    rs.getString("reason"),
                    rs.getString("status"),
                    nullableLong(rs, "decision_by_admin_id"),
                    rs.getString("decision_note"),
                    instant(rs, "processed_at"),
                    instant(rs, "created_at")
            );
        }
    }

    private static final class PenaltyRowMapper implements RowMapper<PenaltyResponse> {
        @Override
        public PenaltyResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PenaltyResponse(
                    rs.getLong("id"),
                    new UserSummary(rs.getLong("user_id"), rs.getString("user_nickname")),
                    nullableLong(rs, "report_id"),
                    nullableLong(rs, "appeal_id"),
                    rs.getString("penalty_type"),
                    rs.getString("reason"),
                    rs.getString("status"),
                    rs.getLong("created_by_admin_id"),
                    nullableLong(rs, "lifted_by_admin_id"),
                    instant(rs, "lifted_at"),
                    instant(rs, "created_at")
            );
        }
    }
}
