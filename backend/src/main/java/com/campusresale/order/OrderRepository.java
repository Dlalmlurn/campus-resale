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
                   ro.status_before_refund,
                   ro.decision_by_admin_id,
                   ro.decision_note,
                   ro.reviewed_at,
                   ro.processed_at,
                   ro.provider_refund_no,
                   ro.failure_reason,
                   ro.created_at,
                   COALESCE(array_agg(ref.file_id ORDER BY ref.file_id) FILTER (WHERE ref.file_id IS NOT NULL), '{}') AS evidence_file_ids
            FROM refund_orders ro
            JOIN users requester ON requester.id = ro.requested_by_user_id
            LEFT JOIN refund_evidence_files ref ON ref.refund_order_id = ro.id
            """;

    private static final String REFUND_GROUP = """
            GROUP BY ro.id, requester.nickname
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

    public List<Long> listExpiredPendingPaymentOrderIds(Instant paymentStartedBefore, int limit) {
        return jdbcTemplate.query("""
                        SELECT id
                        FROM trade_orders
                        WHERE status = 'PENDING_PAYMENT'
                          AND updated_at <= ?
                        ORDER BY updated_at ASC, id ASC
                        LIMIT ?
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                Timestamp.from(paymentStartedBefore),
                limit
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

    public Optional<PaymentOrderRecord> findPaymentByIdForUpdate(long paymentId) {
        return jdbcTemplate.query(PAYMENT_SELECT + " WHERE id = ? FOR UPDATE",
                new PaymentOrderRowMapper(),
                paymentId
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

    public int closeActivePaymentsByOrder(long orderId, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE payment_orders
                        SET status = 'CLOSED',
                            closed_at = COALESCE(closed_at, ?)
                        WHERE order_id = ?
                          AND status IN ('PENDING', 'PROCESSING')
                        """,
                Timestamp.from(now),
                orderId
        );
    }

    public int insertPaymentCallbackIfAbsent(
            long paymentOrderId,
            String provider,
            String callbackNo,
            String payloadHash,
            String processedStatus,
            Instant processedAt
    ) {
        return jdbcTemplate.update("""
                        INSERT INTO payment_callback_logs (
                            payment_order_id,
                            provider,
                            callback_no,
                            payload_hash,
                            processed_status,
                            processed_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (provider, callback_no) DO NOTHING
                        """,
                paymentOrderId,
                provider,
                callbackNo,
                payloadHash,
                processedStatus,
                Timestamp.from(processedAt)
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

    public List<PaymentTransactionRecord> listPaymentTransactionsByOrder(long orderId) {
        return jdbcTemplate.query("""
                        SELECT pt.id,
                               pt.payment_order_id,
                               pt.transaction_no,
                               pt.amount,
                               pt.status,
                               pt.provider,
                               pt.occurred_at
                        FROM payment_transactions pt
                        JOIN payment_orders po ON po.id = pt.payment_order_id
                        WHERE po.order_id = ?
                        ORDER BY pt.occurred_at DESC, pt.id DESC
                        """,
                new PaymentTransactionRowMapper(),
                orderId
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

    public List<SettlementRecord> listSettlements(int page, int pageSize) {
        return jdbcTemplate.query(SETTLEMENT_SELECT + """
                        ORDER BY created_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """,
                new SettlementRowMapper(),
                pageSize,
                (page - 1) * pageSize
        );
    }

    public List<Long> listDueSettlementIds(Instant now, int limit) {
        return jdbcTemplate.query("""
                        SELECT sr.id
                        FROM settlement_records sr
                        JOIN trade_orders o ON o.id = sr.order_id
                        WHERE sr.status IN ('PENDING', 'FAILED')
                          AND sr.freeze_ends_at <= ?
                          AND o.status = 'COMPLETED_PENDING_SETTLEMENT'
                          AND NOT EXISTS (
                              SELECT 1
                              FROM refund_orders ro
                              WHERE ro.order_id = sr.order_id
                                AND ro.status IN ('PENDING', 'PROCESSING')
                          )
                        ORDER BY sr.freeze_ends_at ASC, sr.id ASC
                        LIMIT ?
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                Timestamp.from(now),
                limit
        );
    }

    public long countSettlements() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlement_records", Long.class);
        return total == null ? 0 : total;
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

    public void createSucceededSettlementAttempt(long settlementId, int attemptNo, BigDecimal amount, Long adminId, Instant now) {
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
                          AND status IN ('PENDING', 'PROCESSING', 'FAILED')
                        """,
                Timestamp.from(settledAt),
                Timestamp.from(settledAt),
                settlementId
        );
    }

    public int markSettlementProcessing(long settlementId, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE settlement_records
                        SET status = 'PROCESSING',
                            updated_at = ?
                        WHERE id = ?
                          AND status IN ('PENDING', 'FAILED')
                        """,
                Timestamp.from(now),
                settlementId
        );
    }

    public int markSettlementClosedByOrder(long orderId, String reason, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE settlement_records
                        SET status = 'CLOSED',
                            failure_reason = ?,
                            updated_at = ?
                        WHERE order_id = ?
                          AND status IN ('PENDING', 'PROCESSING', 'FAILED')
                        """,
                reason,
                Timestamp.from(now),
                orderId
        );
    }

    public int updateSettlementAmountByOrder(long orderId, BigDecimal amount, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE settlement_records
                        SET settlement_amount = ?,
                            updated_at = ?
                        WHERE order_id = ?
                          AND status IN ('PENDING', 'FAILED')
                        """,
                amount,
                Timestamp.from(now),
                orderId
        );
    }

    public long createRefund(
            String refundNo,
            long orderId,
            long paymentOrderId,
            long requesterId,
            BigDecimal amount,
            String refundType,
            String reason,
            TradeOrderStatus statusBeforeRefund,
            Instant now
    ) {
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO refund_orders (
                            refund_no,
                            order_id,
                            payment_order_id,
                            requested_by_user_id,
                            amount,
                            refund_type,
                            reason,
                            status,
                            status_before_refund,
                            created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                        RETURNING id
                        """,
                Long.class,
                refundNo,
                orderId,
                paymentOrderId,
                requesterId,
                amount,
                refundType,
                reason,
                statusBeforeRefund.name(),
                Timestamp.from(now)
        );
        return id == null ? 0 : id;
    }

    public void attachRefundEvidence(long refundId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        for (Long fileId : fileIds) {
            jdbcTemplate.update("""
                            INSERT INTO refund_evidence_files (refund_order_id, file_id)
                            VALUES (?, ?)
                            ON CONFLICT DO NOTHING
                            """,
                    refundId,
                    fileId
            );
        }
    }

    public Optional<RefundOrderRecord> findRefundById(long refundId) {
        return jdbcTemplate.query(REFUND_SELECT + " WHERE ro.id = ? " + REFUND_GROUP,
                new RefundOrderRowMapper(),
                refundId
        ).stream().findFirst();
    }

    public Optional<RefundOrderRecord> findRefundByIdForUpdate(long refundId) {
        List<Long> locked = jdbcTemplate.query("SELECT id FROM refund_orders WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getLong("id"),
                refundId
        );
        if (locked.isEmpty()) {
            return Optional.empty();
        }
        return findRefundById(refundId);
    }

    public List<RefundOrderRecord> listRefundsByOrder(long orderId) {
        return jdbcTemplate.query(REFUND_SELECT + " WHERE ro.order_id = ? " + REFUND_GROUP + " ORDER BY ro.created_at DESC, ro.id DESC",
                new RefundOrderRowMapper(),
                orderId
        );
    }

    public List<RefundOrderRecord> listRefundsByUser(long userId) {
        return jdbcTemplate.query(REFUND_SELECT + " WHERE ro.requested_by_user_id = ? " + REFUND_GROUP + " ORDER BY ro.created_at DESC, ro.id DESC LIMIT 50",
                new RefundOrderRowMapper(),
                userId
        );
    }

    public List<RefundOrderRecord> listRefundsForAdmin(int page, int pageSize) {
        return jdbcTemplate.query(REFUND_SELECT + REFUND_GROUP + """
                        ORDER BY CASE ro.status WHEN 'PENDING' THEN 0 WHEN 'PROCESSING' THEN 1 ELSE 2 END,
                                 ro.created_at DESC,
                                 ro.id DESC
                        LIMIT ? OFFSET ?
                        """,
                new RefundOrderRowMapper(),
                pageSize,
                (page - 1) * pageSize
        );
    }

    public long countRefundsForAdmin() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refund_orders", Long.class);
        return total == null ? 0 : total;
    }

    public BigDecimal completedRefundAmount(long paymentOrderId) {
        BigDecimal amount = jdbcTemplate.queryForObject("""
                        SELECT COALESCE(SUM(amount), 0)
                        FROM refund_orders
                        WHERE payment_order_id = ?
                          AND status = 'REFUNDED'
                        """,
                BigDecimal.class,
                paymentOrderId
        );
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public BigDecimal activeRefundAmount(long paymentOrderId, Long excludingRefundId) {
        BigDecimal amount = excludingRefundId == null
                ? jdbcTemplate.queryForObject("""
                                SELECT COALESCE(SUM(amount), 0)
                                FROM refund_orders
                                WHERE payment_order_id = ?
                                  AND status IN ('PENDING', 'PROCESSING', 'REFUNDED')
                                """,
                        BigDecimal.class,
                        paymentOrderId
                )
                : jdbcTemplate.queryForObject("""
                                SELECT COALESCE(SUM(amount), 0)
                                FROM refund_orders
                                WHERE payment_order_id = ?
                                  AND status IN ('PENDING', 'PROCESSING', 'REFUNDED')
                                  AND id <> ?
                                """,
                        BigDecimal.class,
                        paymentOrderId,
                        excludingRefundId
                );
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public boolean hasActiveRefund(long orderId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM refund_orders
                            WHERE order_id = ?
                              AND status IN ('PENDING', 'PROCESSING')
                        )
                        """,
                Boolean.class,
                orderId
        );
        return Boolean.TRUE.equals(exists);
    }

    public int markRefundProcessing(long refundId, long adminId, String note, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE refund_orders
                        SET status = 'PROCESSING',
                            decision_by_admin_id = ?,
                            decision_note = ?,
                            reviewed_at = ?,
                            processed_at = NULL
                        WHERE id = ?
                          AND status = 'PENDING'
                        """,
                adminId,
                note,
                Timestamp.from(now),
                refundId
        );
    }

    public int markRefundFinal(
            long refundId,
            RefundOrderStatus status,
            long adminId,
            String note,
            String providerRefundNo,
            String failureReason,
            Instant now
    ) {
        return jdbcTemplate.update("""
                        UPDATE refund_orders
                        SET status = ?,
                            decision_by_admin_id = ?,
                            decision_note = ?,
                            reviewed_at = COALESCE(reviewed_at, ?),
                            processed_at = ?,
                            provider_refund_no = ?,
                            failure_reason = ?
                        WHERE id = ?
                          AND status IN ('PENDING', 'PROCESSING')
                        """,
                status.name(),
                adminId,
                note,
                Timestamp.from(now),
                Timestamp.from(now),
                providerRefundNo,
                failureReason,
                refundId
        );
    }

    public List<PaymentOrderRecord> listPaymentsForAdmin(int page, int pageSize) {
        return jdbcTemplate.query(PAYMENT_SELECT + """
                        ORDER BY created_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """,
                new PaymentOrderRowMapper(),
                pageSize,
                (page - 1) * pageSize
        );
    }

    public long countPaymentsForAdmin() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_orders", Long.class);
        return total == null ? 0 : total;
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

    private static List<Long> longArray(ResultSet resultSet, String column) throws SQLException {
        Long[] values = (Long[]) resultSet.getArray(column).getArray();
        return List.of(values);
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

    private static class PaymentTransactionRowMapper implements RowMapper<PaymentTransactionRecord> {

        @Override
        public PaymentTransactionRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new PaymentTransactionRecord(
                    resultSet.getLong("id"),
                    resultSet.getLong("payment_order_id"),
                    resultSet.getString("transaction_no"),
                    resultSet.getBigDecimal("amount"),
                    resultSet.getString("status"),
                    resultSet.getString("provider"),
                    resultSet.getTimestamp("occurred_at").toInstant()
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

    private static class RefundOrderRowMapper implements RowMapper<RefundOrderRecord> {

        @Override
        public RefundOrderRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            String statusBeforeRefund = resultSet.getString("status_before_refund");
            return new RefundOrderRecord(
                    resultSet.getLong("id"),
                    resultSet.getString("refund_no"),
                    resultSet.getLong("order_id"),
                    resultSet.getObject("payment_order_id", Long.class),
                    resultSet.getLong("requested_by_user_id"),
                    resultSet.getString("requester_nickname"),
                    resultSet.getBigDecimal("amount"),
                    resultSet.getString("refund_type"),
                    resultSet.getString("reason"),
                    RefundOrderStatus.valueOf(resultSet.getString("status")),
                    statusBeforeRefund == null ? null : TradeOrderStatus.valueOf(statusBeforeRefund),
                    resultSet.getObject("decision_by_admin_id", Long.class),
                    resultSet.getString("decision_note"),
                    nullableInstant(resultSet, "reviewed_at"),
                    nullableInstant(resultSet, "processed_at"),
                    resultSet.getString("provider_refund_no"),
                    resultSet.getString("failure_reason"),
                    longArray(resultSet, "evidence_file_ids"),
                    resultSet.getTimestamp("created_at").toInstant()
            );
        }
    }
}
