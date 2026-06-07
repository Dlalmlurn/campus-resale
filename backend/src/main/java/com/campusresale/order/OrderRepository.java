package com.campusresale.order;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    private static final String ORDER_SELECT = """
            SELECT o.id,
                   o.order_no,
                   o.goods_id,
                   g.title AS goods_title,
                   pi.file_id AS primary_image_file_id,
                   o.conversation_id,
                   o.accepted_bargain_card_id,
                   o.buyer_id,
                   buyer.nickname AS buyer_nickname,
                   o.seller_id,
                   seller.nickname AS seller_nickname,
                   o.frozen_amount,
                   o.status,
                   o.trade_place_id,
                   p.name AS trade_place_name,
                   o.trade_place_detail,
                   o.meetup_time,
                   o.buyer_note,
                   o.created_at,
                   o.updated_at,
                   o.closed_at
            FROM trade_orders o
            JOIN goods g ON g.id = o.goods_id
            JOIN users buyer ON buyer.id = o.buyer_id
            JOIN users seller ON seller.id = o.seller_id
            LEFT JOIN campus_places p ON p.id = o.trade_place_id
            LEFT JOIN LATERAL (
                SELECT gi.file_id
                FROM goods_images gi
                WHERE gi.goods_id = o.goods_id
                ORDER BY gi.is_primary DESC, gi.sort_order, gi.id
                LIMIT 1
            ) pi ON TRUE
            """;

    private static final String PAYMENT_SELECT = """
            SELECT id,
                   payment_no,
                   order_id,
                   amount,
                   status,
                   provider,
                   created_at,
                   paid_at,
                   closed_at
            FROM payment_orders
            """;

    private static final String COMPLETION_REQUEST_SELECT = """
            SELECT id,
                   order_id,
                   seller_id,
                   buyer_id,
                   status,
                   window_starts_at,
                   window_ends_at,
                   confirmed_at,
                   created_at,
                   updated_at
            FROM completion_confirmation_requests
            """;

    private static final String SETTLEMENT_SELECT = """
            SELECT id,
                   order_id,
                   payment_order_id,
                   settlement_no,
                   settlement_amount,
                   status,
                   freeze_started_at,
                   freeze_ends_at,
                   settled_at,
                   failure_reason,
                   created_at,
                   updated_at
            FROM settlement_records
            """;

    private static final String REVIEW_SELECT = """
            SELECT id,
                   order_id,
                   reviewer_id,
                   reviewed_user_id,
                   rating,
                   content,
                   status,
                   submitted_at,
                   modified_until,
                   visible_at
            FROM reviews
            """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public long createOrder(OrderWriteData data) {
        Long orderId = jdbcTemplate.queryForObject("""
                        INSERT INTO trade_orders (
                            order_no,
                            goods_id,
                            conversation_id,
                            accepted_bargain_card_id,
                            buyer_id,
                            seller_id,
                            frozen_amount,
                            status,
                            trade_place_id,
                            trade_place_detail,
                            meetup_time,
                            buyer_note,
                            seller_payout_account_snapshot_json,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '{}'::jsonb, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                data.orderNo(),
                data.goodsId(),
                data.conversationId(),
                data.acceptedBargainCardId(),
                data.buyerId(),
                data.sellerId(),
                data.frozenAmount(),
                data.status().name(),
                data.tradePlaceId(),
                data.tradePlaceDetail(),
                data.meetupTime() == null ? null : Timestamp.from(data.meetupTime()),
                data.buyerNote(),
                Timestamp.from(data.now()),
                Timestamp.from(data.now())
        );
        return orderId;
    }

    public Optional<TradeOrderRecord> findOrderById(long orderId) {
        return jdbcTemplate.query(ORDER_SELECT + " WHERE o.id = ?",
                new TradeOrderRowMapper(),
                orderId
        ).stream().findFirst();
    }

    public Optional<TradeOrderRecord> findOrderByIdForUpdate(long orderId) {
        return jdbcTemplate.query(ORDER_SELECT + " WHERE o.id = ? FOR UPDATE OF o",
                new TradeOrderRowMapper(),
                orderId
        ).stream().findFirst();
    }

    public List<TradeOrderRecord> listByParticipant(long userId, TradeOrderStatus status, int page, int pageSize) {
        MapSqlParameterSource params = pageParams(page, pageSize)
                .addValue("userId", userId);
        StringBuilder sql = new StringBuilder(ORDER_SELECT)
                .append(" WHERE (o.buyer_id = :userId OR o.seller_id = :userId)");
        if (status != null) {
            sql.append(" AND o.status = :status");
            params.addValue("status", status.name());
        }
        sql.append(" ORDER BY o.created_at DESC, o.id DESC LIMIT :limit OFFSET :offset");
        return namedParameterJdbcTemplate.query(sql.toString(), params, new TradeOrderRowMapper());
    }

    public long countByParticipant(long userId, TradeOrderStatus status) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId);
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM trade_orders o
                WHERE (o.buyer_id = :userId OR o.seller_id = :userId)
                """);
        if (status != null) {
            sql.append(" AND o.status = :status");
            params.addValue("status", status.name());
        }
        Long total = namedParameterJdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return total == null ? 0 : total;
    }

    public int updateOrderStatus(
            long orderId,
            TradeOrderStatus expectedStatus,
            TradeOrderStatus nextStatus,
            Instant now,
            boolean closeOrder
    ) {
        return jdbcTemplate.update("""
                        UPDATE trade_orders
                        SET status = ?,
                            updated_at = ?,
                            closed_at = CASE WHEN ? THEN ? ELSE closed_at END
                        WHERE id = ?
                          AND status = ?
                        """,
                nextStatus.name(),
                Timestamp.from(now),
                closeOrder,
                closeOrder ? Timestamp.from(now) : null,
                orderId,
                expectedStatus.name()
        );
    }

    public void insertStateRecord(
            long orderId,
            TradeOrderStatus fromStatus,
            TradeOrderStatus toStatus,
            String eventType,
            Long operatorUserId,
            Long operatorAdminId,
            String reason
    ) {
        jdbcTemplate.update("""
                        INSERT INTO order_state_records (
                            order_id,
                            from_status,
                            to_status,
                            event_type,
                            operator_user_id,
                            operator_admin_id,
                            reason,
                            created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orderId,
                fromStatus == null ? null : fromStatus.name(),
                toStatus.name(),
                eventType,
                operatorUserId,
                operatorAdminId,
                reason,
                Timestamp.from(Instant.now())
        );
    }

    public Optional<PaymentOrderRecord> findLatestPaymentByOrder(long orderId) {
        return jdbcTemplate.query(PAYMENT_SELECT + """
                        WHERE order_id = ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """,
                new PaymentOrderRowMapper(),
                orderId
        ).stream().findFirst();
    }

    public Optional<PaymentOrderRecord> findLatestPaymentByOrderForUpdate(long orderId) {
        return jdbcTemplate.query(PAYMENT_SELECT + """
                        WHERE order_id = ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        FOR UPDATE
                        """,
                new PaymentOrderRowMapper(),
                orderId
        ).stream().findFirst();
    }

    public Optional<PaymentOrderRecord> findEscrowedPaymentByOrder(long orderId) {
        return jdbcTemplate.query(PAYMENT_SELECT + """
                        WHERE order_id = ?
                          AND status = 'ESCROWED'
                        ORDER BY paid_at DESC, id DESC
                        LIMIT 1
                        """,
                new PaymentOrderRowMapper(),
                orderId
        ).stream().findFirst();
    }

    public long createPayment(String paymentNo, long orderId, BigDecimal amount, Instant now) {
        Long paymentId = jdbcTemplate.queryForObject("""
                        INSERT INTO payment_orders (
                            payment_no,
                            order_id,
                            amount,
                            status,
                            provider,
                            created_at
                        )
                        VALUES (?, ?, ?, 'PENDING', 'SIMULATED', ?)
                        RETURNING id
                        """,
                Long.class,
                paymentNo,
                orderId,
                amount,
                Timestamp.from(now)
        );
        return paymentId;
    }

    public int markPaymentEscrowed(long paymentId, Instant paidAt) {
        return jdbcTemplate.update("""
                        UPDATE payment_orders
                        SET status = 'ESCROWED',
                            paid_at = COALESCE(paid_at, ?)
                        WHERE id = ?
                          AND status IN ('PENDING', 'PROCESSING')
                        """,
                Timestamp.from(paidAt),
                paymentId
        );
    }

    public void insertPaymentTransaction(String transactionNo, long paymentOrderId, BigDecimal amount, Instant occurredAt) {
        jdbcTemplate.update("""
                        INSERT INTO payment_transactions (
                            payment_order_id,
                            transaction_no,
                            amount,
                            status,
                            provider,
                            occurred_at,
                            raw_summary_json
                        )
                        VALUES (?, ?, ?, 'SUCCEEDED', 'SIMULATED', ?, '{}'::jsonb)
                        ON CONFLICT (transaction_no) DO NOTHING
                        """,
                paymentOrderId,
                transactionNo,
                amount,
                Timestamp.from(occurredAt)
        );
    }

    public Optional<CompletionRequestRecord> findPendingCompletionRequest(long orderId) {
        return jdbcTemplate.query(COMPLETION_REQUEST_SELECT + """
                        WHERE order_id = ?
                          AND status = 'PENDING'
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """,
                new CompletionRequestRowMapper(),
                orderId
        ).stream().findFirst();
    }

    public Optional<CompletionRequestRecord> findCompletionRequestForUpdate(long requestId) {
        return jdbcTemplate.query(COMPLETION_REQUEST_SELECT + " WHERE id = ? FOR UPDATE",
                new CompletionRequestRowMapper(),
                requestId
        ).stream().findFirst();
    }

    public long createCompletionRequest(long orderId, long sellerId, long buyerId, Instant startsAt, Instant endsAt) {
        Long requestId = jdbcTemplate.queryForObject("""
                        INSERT INTO completion_confirmation_requests (
                            order_id,
                            seller_id,
                            buyer_id,
                            status,
                            window_starts_at,
                            window_ends_at,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, 'PENDING', ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                orderId,
                sellerId,
                buyerId,
                Timestamp.from(startsAt),
                Timestamp.from(endsAt),
                Timestamp.from(startsAt),
                Timestamp.from(startsAt)
        );
        return requestId;
    }

    public int markCompletionRequestConfirmed(long requestId, Instant confirmedAt) {
        return jdbcTemplate.update("""
                        UPDATE completion_confirmation_requests
                        SET status = 'CONFIRMED',
                            confirmed_at = ?,
                            updated_at = ?
                        WHERE id = ?
                          AND status = 'PENDING'
                        """,
                Timestamp.from(confirmedAt),
                Timestamp.from(confirmedAt),
                requestId
        );
    }

    public long createSettlement(
            String settlementNo,
            long orderId,
            long paymentOrderId,
            BigDecimal amount,
            Instant freezeStartedAt,
            Instant freezeEndsAt
    ) {
        Long settlementId = jdbcTemplate.queryForObject("""
                        INSERT INTO settlement_records (
                            order_id,
                            payment_order_id,
                            settlement_no,
                            settlement_amount,
                            status,
                            freeze_started_at,
                            freeze_ends_at,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?, ?)
                        ON CONFLICT (order_id) DO UPDATE
                        SET updated_at = settlement_records.updated_at
                        RETURNING id
                        """,
                Long.class,
                orderId,
                paymentOrderId,
                settlementNo,
                amount,
                Timestamp.from(freezeStartedAt),
                Timestamp.from(freezeEndsAt),
                Timestamp.from(freezeStartedAt),
                Timestamp.from(freezeStartedAt)
        );
        return settlementId;
    }

    public Optional<SettlementRecord> findSettlementByIdForUpdate(long settlementId) {
        return jdbcTemplate.query(SETTLEMENT_SELECT + " WHERE id = ? FOR UPDATE",
                new SettlementRowMapper(),
                settlementId
        ).stream().findFirst();
    }

    public Optional<SettlementRecord> findSettlementByOrder(long orderId) {
        return jdbcTemplate.query(SETTLEMENT_SELECT + " WHERE order_id = ?",
                new SettlementRowMapper(),
                orderId
        ).stream().findFirst();
    }

    public int nextSettlementAttemptNo(long settlementId) {
        Integer next = jdbcTemplate.queryForObject("""
                        SELECT COALESCE(MAX(attempt_no), 0) + 1
                        FROM settlement_attempts
                        WHERE settlement_record_id = ?
                        """,
                Integer.class,
                settlementId
        );
        return next == null ? 1 : next;
    }

    public void createSucceededSettlementAttempt(long settlementId, int attemptNo, BigDecimal amount, long adminId, Instant now) {
        jdbcTemplate.update("""
                        INSERT INTO settlement_attempts (
                            settlement_record_id,
                            attempt_no,
                            amount,
                            status,
                            payout_account_snapshot_json,
                            provider_attempt_no,
                            started_at,
                            finished_at,
                            created_by_admin_id
                        )
                        VALUES (?, ?, ?, 'SUCCEEDED', '{}'::jsonb, ?, ?, ?, ?)
                        """,
                settlementId,
                attemptNo,
                amount,
                "SIM-SETTLE-" + settlementId + "-" + attemptNo,
                Timestamp.from(now),
                Timestamp.from(now),
                adminId
        );
    }

    public int markSettlementSettled(long settlementId, Instant settledAt) {
        return jdbcTemplate.update("""
                        UPDATE settlement_records
                        SET status = 'SETTLED',
                            settled_at = COALESCE(settled_at, ?),
                            updated_at = ?
                        WHERE id = ?
                          AND status IN ('PENDING', 'FAILED')
                        """,
                Timestamp.from(settledAt),
                Timestamp.from(settledAt),
                settlementId
        );
    }

    public long createReview(
            long orderId,
            long reviewerId,
            long reviewedUserId,
            int rating,
            String content,
            Instant now
    ) {
        Long reviewId = jdbcTemplate.queryForObject("""
                        INSERT INTO reviews (
                            order_id,
                            reviewer_id,
                            reviewed_user_id,
                            rating,
                            content,
                            status,
                            submitted_at,
                            modified_until,
                            visible_at
                        )
                        VALUES (?, ?, ?, ?, ?, 'VISIBLE', ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                orderId,
                reviewerId,
                reviewedUserId,
                rating,
                content,
                Timestamp.from(now),
                Timestamp.from(now.plusSeconds(72 * 60 * 60)),
                Timestamp.from(now)
        );
        return reviewId;
    }

    public boolean reviewExists(long orderId, long reviewerId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM reviews
                            WHERE order_id = ?
                              AND reviewer_id = ?
                        )
                        """,
                Boolean.class,
                orderId,
                reviewerId
        );
        return Boolean.TRUE.equals(exists);
    }

    public Optional<ReviewRecord> findReviewById(long reviewId) {
        return jdbcTemplate.query(REVIEW_SELECT + " WHERE id = ?",
                new ReviewRowMapper(),
                reviewId
        ).stream().findFirst();
    }

    public List<ReviewRecord> listReviews(long orderId) {
        return jdbcTemplate.query(REVIEW_SELECT + """
                        WHERE order_id = ?
                        ORDER BY submitted_at ASC, id ASC
                        """,
                new ReviewRowMapper(),
                orderId
        );
    }

    private MapSqlParameterSource pageParams(int page, int pageSize) {
        return new MapSqlParameterSource()
                .addValue("limit", pageSize)
                .addValue("offset", (page - 1) * pageSize);
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record OrderWriteData(
            String orderNo,
            long goodsId,
            Long conversationId,
            Long acceptedBargainCardId,
            long buyerId,
            long sellerId,
            BigDecimal frozenAmount,
            TradeOrderStatus status,
            Long tradePlaceId,
            String tradePlaceDetail,
            Instant meetupTime,
            String buyerNote,
            Instant now
    ) {
    }

    private static class TradeOrderRowMapper implements RowMapper<TradeOrderRecord> {

        @Override
        public TradeOrderRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new TradeOrderRecord(
                    resultSet.getLong("id"),
                    resultSet.getString("order_no"),
                    resultSet.getLong("goods_id"),
                    resultSet.getString("goods_title"),
                    resultSet.getObject("primary_image_file_id", Long.class),
                    resultSet.getObject("conversation_id", Long.class),
                    resultSet.getObject("accepted_bargain_card_id", Long.class),
                    resultSet.getLong("buyer_id"),
                    resultSet.getString("buyer_nickname"),
                    resultSet.getLong("seller_id"),
                    resultSet.getString("seller_nickname"),
                    resultSet.getBigDecimal("frozen_amount"),
                    TradeOrderStatus.valueOf(resultSet.getString("status")),
                    resultSet.getObject("trade_place_id", Long.class),
                    resultSet.getString("trade_place_name"),
                    resultSet.getString("trade_place_detail"),
                    nullableInstant(resultSet, "meetup_time"),
                    resultSet.getString("buyer_note"),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant(),
                    nullableInstant(resultSet, "closed_at")
            );
        }
    }

    private static class PaymentOrderRowMapper implements RowMapper<PaymentOrderRecord> {

        @Override
        public PaymentOrderRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new PaymentOrderRecord(
                    resultSet.getLong("id"),
                    resultSet.getString("payment_no"),
                    resultSet.getLong("order_id"),
                    resultSet.getBigDecimal("amount"),
                    PaymentOrderStatus.valueOf(resultSet.getString("status")),
                    resultSet.getString("provider"),
                    resultSet.getTimestamp("created_at").toInstant(),
                    nullableInstant(resultSet, "paid_at"),
                    nullableInstant(resultSet, "closed_at")
            );
        }
    }

    private static class CompletionRequestRowMapper implements RowMapper<CompletionRequestRecord> {

        @Override
        public CompletionRequestRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new CompletionRequestRecord(
                    resultSet.getLong("id"),
                    resultSet.getLong("order_id"),
                    resultSet.getLong("seller_id"),
                    resultSet.getLong("buyer_id"),
                    CompletionRequestStatus.valueOf(resultSet.getString("status")),
                    resultSet.getTimestamp("window_starts_at").toInstant(),
                    resultSet.getTimestamp("window_ends_at").toInstant(),
                    nullableInstant(resultSet, "confirmed_at"),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant()
            );
        }
    }

    private static class SettlementRowMapper implements RowMapper<SettlementRecord> {

        @Override
        public SettlementRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new SettlementRecord(
                    resultSet.getLong("id"),
                    resultSet.getLong("order_id"),
                    resultSet.getLong("payment_order_id"),
                    resultSet.getString("settlement_no"),
                    resultSet.getBigDecimal("settlement_amount"),
                    SettlementStatus.valueOf(resultSet.getString("status")),
                    resultSet.getTimestamp("freeze_started_at").toInstant(),
                    resultSet.getTimestamp("freeze_ends_at").toInstant(),
                    nullableInstant(resultSet, "settled_at"),
                    resultSet.getString("failure_reason"),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant()
            );
        }
    }

    private static class ReviewRowMapper implements RowMapper<ReviewRecord> {

        @Override
        public ReviewRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new ReviewRecord(
                    resultSet.getLong("id"),
                    resultSet.getLong("order_id"),
                    resultSet.getLong("reviewer_id"),
                    resultSet.getLong("reviewed_user_id"),
                    resultSet.getInt("rating"),
                    resultSet.getString("content"),
                    ReviewStatus.valueOf(resultSet.getString("status")),
                    resultSet.getTimestamp("submitted_at").toInstant(),
                    resultSet.getTimestamp("modified_until").toInstant(),
                    nullableInstant(resultSet, "visible_at")
            );
        }
    }
}
