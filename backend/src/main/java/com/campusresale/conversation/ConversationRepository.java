// 文件功能：封装会话、消息、附件和系统卡片的数据访问，支撑实时消息补偿与用户侧归档删除。
package com.campusresale.conversation;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConversationRepository {

    private static final String CONVERSATION_SELECT = """
            SELECT c.id,
                   c.goods_id,
                   g.title AS goods_title,
                   pi.file_id AS primary_image_file_id,
                   c.buyer_id,
                   buyer.nickname AS buyer_nickname,
                   c.seller_id,
                   seller.nickname AS seller_nickname,
                   c.status,
                   c.last_message_id,
                   lm.text_content AS last_message_text,
                   c.last_message_at,
                   0::bigint AS unread_count,
                   NULL::timestamptz AS archived_at,
                   NULL::timestamptz AS deleted_at,
                   c.created_at,
                   c.updated_at
            FROM conversations c
            JOIN goods g ON g.id = c.goods_id
            JOIN users buyer ON buyer.id = c.buyer_id
            JOIN users seller ON seller.id = c.seller_id
            LEFT JOIN messages lm ON lm.id = c.last_message_id
            LEFT JOIN LATERAL (
                SELECT gi.file_id
                FROM goods_images gi
                WHERE gi.goods_id = c.goods_id
                ORDER BY gi.is_primary DESC, gi.sort_order, gi.id
                LIMIT 1
            ) pi ON TRUE
            """;

    private static final String CONVERSATION_SELECT_FOR_VIEWER = """
            SELECT c.id,
                   c.goods_id,
                   g.title AS goods_title,
                   pi.file_id AS primary_image_file_id,
                   c.buyer_id,
                   buyer.nickname AS buyer_nickname,
                   c.seller_id,
                   seller.nickname AS seller_nickname,
                   c.status,
                   c.last_message_id,
                   lm.text_content AS last_message_text,
                   c.last_message_at,
                   (
                       SELECT COUNT(*)
                       FROM messages unread_messages
                       WHERE unread_messages.conversation_id = c.id
                         AND unread_messages.sender_id IS DISTINCT FROM ?
                         AND NOT EXISTS (
                             SELECT 1
                             FROM message_read_states mrs
                             WHERE mrs.message_id = unread_messages.id
                               AND mrs.user_id = ?
                         )
                   ) AS unread_count,
                   CASE
                       WHEN c.buyer_id = ? THEN c.buyer_archived_at
                       WHEN c.seller_id = ? THEN c.seller_archived_at
                       ELSE NULL
                   END AS archived_at,
                   CASE
                       WHEN c.buyer_id = ? THEN c.buyer_deleted_at
                       WHEN c.seller_id = ? THEN c.seller_deleted_at
                       ELSE NULL
                   END AS deleted_at,
                   c.created_at,
                   c.updated_at
            FROM conversations c
            JOIN goods g ON g.id = c.goods_id
            JOIN users buyer ON buyer.id = c.buyer_id
            JOIN users seller ON seller.id = c.seller_id
            LEFT JOIN messages lm ON lm.id = c.last_message_id
            LEFT JOIN LATERAL (
                SELECT gi.file_id
                FROM goods_images gi
                WHERE gi.goods_id = c.goods_id
                ORDER BY gi.is_primary DESC, gi.sort_order, gi.id
                LIMIT 1
            ) pi ON TRUE
            """;

    private static final String MESSAGE_SELECT = """
            SELECT m.id,
                   m.conversation_id,
                   m.sender_id,
                   sender.nickname AS sender_nickname,
                   m.message_type,
                   m.status,
                   m.text_content,
                   m.card_id,
                   m.sent_at
            FROM messages m
            LEFT JOIN users sender ON sender.id = m.sender_id
            """;

    private static final String BARGAIN_SELECT = """
            SELECT id,
                   conversation_id,
                   amount,
                   payload_json ->> 'note' AS note,
                   action_status,
                   created_by_user_id,
                   acted_by_user_id,
                   created_at,
                   expires_at,
                   acted_at
            FROM system_message_cards
            WHERE card_type = 'BARGAIN_OFFER'
            """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public ConversationRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public long createOrGet(long goodsId, long buyerId, long sellerId, Instant now) {
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO conversations (
                            goods_id,
                            buyer_id,
                            seller_id,
                            status,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, 'NORMAL', ?, ?)
                        ON CONFLICT (goods_id, buyer_id, seller_id) DO UPDATE
                        SET updated_at = conversations.updated_at
                        RETURNING id
                        """,
                Long.class,
                goodsId,
                buyerId,
                sellerId,
                Timestamp.from(now),
                Timestamp.from(now)
        );
        return id == null ? 0 : id;
    }

    public Optional<ConversationRecord> findById(long conversationId) {
        return jdbcTemplate.query(CONVERSATION_SELECT + " WHERE c.id = ?",
                new ConversationRowMapper(),
                conversationId
        ).stream().findFirst();
    }

    public Optional<ConversationRecord> findByIdForViewer(long conversationId, long viewerId) {
        return jdbcTemplate.query(CONVERSATION_SELECT_FOR_VIEWER + """
                        WHERE c.id = ?
                          AND (
                              (c.buyer_id = ? AND c.buyer_deleted_at IS NULL)
                              OR (c.seller_id = ? AND c.seller_deleted_at IS NULL)
                          )
                        """,
                new ConversationRowMapper(),
                viewerId,
                viewerId,
                viewerId,
                viewerId,
                viewerId,
                viewerId,
                conversationId,
                viewerId,
                viewerId
        ).stream().findFirst();
    }

    public Optional<ConversationRecord> findByIdForViewerForUpdate(long conversationId, long viewerId) {
        return jdbcTemplate.query(CONVERSATION_SELECT_FOR_VIEWER + """
                        WHERE c.id = ?
                          AND (
                              (c.buyer_id = ? AND c.buyer_deleted_at IS NULL)
                              OR (c.seller_id = ? AND c.seller_deleted_at IS NULL)
                          )
                        FOR UPDATE OF c
                        """,
                new ConversationRowMapper(),
                viewerId,
                viewerId,
                viewerId,
                viewerId,
                viewerId,
                viewerId,
                conversationId,
                viewerId,
                viewerId
        ).stream().findFirst();
    }

    public List<ConversationRecord> listByParticipant(long userId, boolean archivedOnly) {
        String buyerArchivedClause = archivedOnly ? "c.buyer_archived_at IS NOT NULL" : "c.buyer_archived_at IS NULL";
        String sellerArchivedClause = archivedOnly ? "c.seller_archived_at IS NOT NULL" : "c.seller_archived_at IS NULL";
        return jdbcTemplate.query(CONVERSATION_SELECT_FOR_VIEWER + """
                        WHERE (c.buyer_id = ? AND c.buyer_deleted_at IS NULL AND %s)
                           OR (c.seller_id = ? AND c.seller_deleted_at IS NULL AND %s)
                        ORDER BY COALESCE(c.last_message_at, c.created_at) DESC, c.id DESC
                        """.formatted(buyerArchivedClause, sellerArchivedClause),
                new ConversationRowMapper(),
                userId,
                userId,
                userId,
                userId,
                userId,
                userId,
                userId,
                userId
        );
    }

    public long createTextMessage(long conversationId, long senderId, String textContent, Instant now) {
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO messages (
                            conversation_id,
                            sender_id,
                            message_type,
                            status,
                            text_content,
                            sent_at
                        )
                        VALUES (?, ?, 'TEXT', 'SENT', ?, ?)
                        RETURNING id
                        """,
                Long.class,
                conversationId,
                senderId,
                textContent,
                Timestamp.from(now)
        );
        updateLastMessage(conversationId, id == null ? 0 : id, now);
        return id == null ? 0 : id;
    }

    public long createImageMessage(long conversationId, long senderId, String textContent, Instant now) {
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO messages (
                            conversation_id,
                            sender_id,
                            message_type,
                            status,
                            text_content,
                            sent_at
                        )
                        VALUES (?, ?, 'IMAGE', 'SENT', ?, ?)
                        RETURNING id
                        """,
                Long.class,
                conversationId,
                senderId,
                textContent,
                Timestamp.from(now)
        );
        updateLastMessage(conversationId, id == null ? 0 : id, now);
        return id == null ? 0 : id;
    }

    public long createBargainCard(long conversationId, long createdByUserId, BigDecimal amount, String note, Instant now, Instant expiresAt) {
        Long cardId = jdbcTemplate.queryForObject("""
                        INSERT INTO system_message_cards (
                            conversation_id,
                            card_type,
                            amount,
                            payload_json,
                            action_status,
                            created_by_user_id,
                            created_at,
                            expires_at
                        )
                        VALUES (?, 'BARGAIN_OFFER', ?, jsonb_build_object('note', ?), 'PENDING', ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                conversationId,
                amount,
                note,
                createdByUserId,
                Timestamp.from(now),
                Timestamp.from(expiresAt)
        );
        long messageId = createSystemCardMessage(
                conversationId,
                cardId == null ? 0 : cardId,
                "买家发起议价 ¥" + amount,
                now
        );
        updateLastMessage(conversationId, messageId, now);
        return cardId == null ? 0 : cardId;
    }

    public Optional<BargainCardRecord> findBargainById(long cardId) {
        return jdbcTemplate.query(BARGAIN_SELECT + " AND id = ?",
                new BargainCardRowMapper(),
                cardId
        ).stream().findFirst();
    }

    public Optional<BargainCardRecord> findBargainByIdForUpdate(long cardId) {
        return jdbcTemplate.query(BARGAIN_SELECT + " AND id = ? FOR UPDATE",
                new BargainCardRowMapper(),
                cardId
        ).stream().findFirst();
    }

    public List<BargainCardRecord> listBargains(long conversationId) {
        return jdbcTemplate.query(BARGAIN_SELECT + """
                        AND conversation_id = ?
                        ORDER BY created_at ASC, id ASC
                        """,
                new BargainCardRowMapper(),
                conversationId
        );
    }

    public int markBargain(long cardId, String status, long actedByUserId, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE system_message_cards
                        SET action_status = ?,
                            acted_by_user_id = ?,
                            acted_at = ?
                        WHERE id = ?
                          AND card_type = 'BARGAIN_OFFER'
                          AND action_status = 'PENDING'
                        """,
                status,
                actedByUserId,
                Timestamp.from(now),
                cardId
        );
    }

    public void createBargainDecisionMessage(long conversationId, long cardId, String text, Instant now) {
        long messageId = createSystemCardMessage(conversationId, cardId, text, now);
        updateLastMessage(conversationId, messageId, now);
    }

    public long createBargainDecisionMessageReturningId(long conversationId, long cardId, String text, Instant now) {
        long messageId = createSystemCardMessage(conversationId, cardId, text, now);
        updateLastMessage(conversationId, messageId, now);
        return messageId;
    }

    public long createSystemNoticeMessage(long conversationId, String text, long orderId, Instant now) {
        // SYSTEM_NOTICE 复用系统卡片事实表，payload 保留 orderId 供后续前端扩展结构化跳转。
        Long cardId = jdbcTemplate.queryForObject("""
                        INSERT INTO system_message_cards (
                            conversation_id,
                            card_type,
                            payload_json,
                            action_status,
                            created_by_system,
                            created_at
                        )
                        VALUES (?, 'SYSTEM_NOTICE', jsonb_build_object('orderId', ?), 'PENDING', TRUE, ?)
                        RETURNING id
                        """,
                Long.class,
                conversationId,
                orderId,
                Timestamp.from(now)
        );
        long messageId = createSystemCardMessage(conversationId, cardId == null ? 0 : cardId, text, now);
        updateLastMessage(conversationId, messageId, now);
        return messageId;
    }

    public List<MessageRecord> listMessages(long conversationId) {
        return jdbcTemplate.query(MESSAGE_SELECT + """
                        WHERE m.conversation_id = ?
                        ORDER BY m.sent_at ASC, m.id ASC
                        """,
                new MessageRowMapper(),
                conversationId
        );
    }

    public List<MessageRecord> listMessagesAfterId(long conversationId, long afterId) {
        return jdbcTemplate.query(MESSAGE_SELECT + """
                        WHERE m.conversation_id = ?
                          AND m.id > ?
                        ORDER BY m.sent_at ASC, m.id ASC
                        """,
                new MessageRowMapper(),
                conversationId,
                afterId
        );
    }

    public Optional<MessageRecord> findMessageById(long messageId) {
        return jdbcTemplate.query(MESSAGE_SELECT + " WHERE m.id = ?",
                new MessageRowMapper(),
                messageId
        ).stream().findFirst();
    }

    public Optional<MessageRecord> findMessageByCardId(long cardId) {
        return jdbcTemplate.query(MESSAGE_SELECT + """
                        WHERE m.card_id = ?
                        ORDER BY m.id DESC
                        LIMIT 1
                        """,
                new MessageRowMapper(),
                cardId
        ).stream().findFirst();
    }

    public void createMessageAttachments(long messageId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        for (int index = 0; index < fileIds.size(); index++) {
            jdbcTemplate.update("""
                            INSERT INTO message_attachments (message_id, file_id, sort_order, created_at)
                            VALUES (?, ?, ?, ?)
                            """,
                    messageId,
                    fileIds.get(index),
                    index,
                    Timestamp.from(Instant.now())
            );
        }
    }

    public List<MessageAttachmentRecord> listAttachmentsByMessageIds(Collection<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return List.of();
        }
        return namedParameterJdbcTemplate.query("""
                        SELECT ma.id,
                               ma.message_id,
                               ma.file_id,
                               sf.original_name,
                               sf.content_type,
                               sf.byte_size,
                               ma.sort_order
                        FROM message_attachments ma
                        JOIN stored_files sf ON sf.id = ma.file_id
                        WHERE ma.message_id IN (:messageIds)
                          AND sf.deleted_at IS NULL
                        ORDER BY ma.message_id, ma.sort_order, ma.id
                        """,
                Map.of("messageIds", messageIds),
                new MessageAttachmentRowMapper()
        );
    }

    public int markMessagesRead(long conversationId, long userId, Instant readAt) {
        return jdbcTemplate.update("""
                        INSERT INTO message_read_states (message_id, user_id, read_at)
                        SELECT m.id, ?, ?
                        FROM messages m
                        WHERE m.conversation_id = ?
                          AND m.sender_id IS DISTINCT FROM ?
                        ON CONFLICT (message_id, user_id) DO NOTHING
                        """,
                userId,
                Timestamp.from(readAt),
                conversationId,
                userId
        );
    }

    public int archive(long conversationId, long userId, Instant archivedAt) {
        return jdbcTemplate.update("""
                        UPDATE conversations
                        SET buyer_archived_at = CASE WHEN buyer_id = ? THEN ? ELSE buyer_archived_at END,
                            seller_archived_at = CASE WHEN seller_id = ? THEN ? ELSE seller_archived_at END,
                            updated_at = ?
                        WHERE id = ?
                          AND (buyer_id = ? OR seller_id = ?)
                        """,
                userId,
                Timestamp.from(archivedAt),
                userId,
                Timestamp.from(archivedAt),
                Timestamp.from(archivedAt),
                conversationId,
                userId,
                userId
        );
    }

    public int deleteForParticipant(long conversationId, long userId, Instant deletedAt) {
        return jdbcTemplate.update("""
                        UPDATE conversations
                        SET buyer_deleted_at = CASE WHEN buyer_id = ? THEN ? ELSE buyer_deleted_at END,
                            seller_deleted_at = CASE WHEN seller_id = ? THEN ? ELSE seller_deleted_at END,
                            updated_at = ?
                        WHERE id = ?
                          AND (buyer_id = ? OR seller_id = ?)
                        """,
                userId,
                Timestamp.from(deletedAt),
                userId,
                Timestamp.from(deletedAt),
                Timestamp.from(deletedAt),
                conversationId,
                userId,
                userId
        );
    }

    public int unarchive(long conversationId, long userId, Instant updatedAt) {
        return jdbcTemplate.update("""
                        UPDATE conversations
                        SET buyer_archived_at = CASE WHEN buyer_id = ? THEN NULL ELSE buyer_archived_at END,
                            seller_archived_at = CASE WHEN seller_id = ? THEN NULL ELSE seller_archived_at END,
                            updated_at = ?
                        WHERE id = ?
                          AND (buyer_id = ? OR seller_id = ?)
                        """,
                userId,
                userId,
                Timestamp.from(updatedAt),
                conversationId,
                userId,
                userId
        );
    }

    public int restoreVisibilityForParticipant(long conversationId, long userId, Instant updatedAt) {
        return jdbcTemplate.update("""
                        UPDATE conversations
                        SET buyer_archived_at = CASE WHEN buyer_id = ? THEN NULL ELSE buyer_archived_at END,
                            seller_archived_at = CASE WHEN seller_id = ? THEN NULL ELSE seller_archived_at END,
                            buyer_deleted_at = CASE WHEN buyer_id = ? THEN NULL ELSE buyer_deleted_at END,
                            seller_deleted_at = CASE WHEN seller_id = ? THEN NULL ELSE seller_deleted_at END,
                            updated_at = ?
                        WHERE id = ?
                          AND (buyer_id = ? OR seller_id = ?)
                        """,
                userId,
                userId,
                userId,
                userId,
                Timestamp.from(updatedAt),
                conversationId,
                userId,
                userId
        );
    }

    public int block(long conversationId, long userId, Instant blockedAt) {
        return jdbcTemplate.update("""
                        UPDATE conversations
                        SET status = 'BLOCKED',
                            updated_at = ?
                        WHERE id = ?
                          AND (buyer_id = ? OR seller_id = ?)
                          AND status <> 'BLOCKED'
                        """,
                Timestamp.from(blockedAt),
                conversationId,
                userId,
                userId
        );
    }

    public int expireExpiredBargains(long conversationId, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE system_message_cards
                        SET action_status = 'EXPIRED',
                            acted_at = ?
                        WHERE conversation_id = ?
                          AND card_type = 'BARGAIN_OFFER'
                          AND action_status = 'PENDING'
                          AND expires_at IS NOT NULL
                          AND expires_at < ?
                        """,
                Timestamp.from(now),
                conversationId,
                Timestamp.from(now)
        );
    }

    public boolean hasOpenOrderForGoods(long goodsId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM trade_orders
                            WHERE goods_id = ?
                              AND status NOT IN ('CANCELLED', 'CLOSED', 'COMPLETED')
                        )
                        """,
                Boolean.class,
                goodsId
        );
        return Boolean.TRUE.equals(exists);
    }

    private long createSystemCardMessage(long conversationId, long cardId, String text, Instant now) {
        Long messageId = jdbcTemplate.queryForObject("""
                        INSERT INTO messages (
                            conversation_id,
                            message_type,
                            status,
                            text_content,
                            card_id,
                            sent_at
                        )
                        VALUES (?, 'SYSTEM_CARD', 'SENT', ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                conversationId,
                text,
                cardId,
                Timestamp.from(now)
        );
        return messageId == null ? 0 : messageId;
    }

    private void updateLastMessage(long conversationId, long messageId, Instant now) {
        jdbcTemplate.update("""
                        UPDATE conversations
                        SET last_message_id = ?,
                            last_message_at = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                messageId,
                Timestamp.from(now),
                Timestamp.from(now),
                conversationId
        );
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static class ConversationRowMapper implements RowMapper<ConversationRecord> {

        @Override
        public ConversationRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new ConversationRecord(
                    resultSet.getLong("id"),
                    resultSet.getLong("goods_id"),
                    resultSet.getString("goods_title"),
                    resultSet.getObject("primary_image_file_id", Long.class),
                    resultSet.getLong("buyer_id"),
                    resultSet.getString("buyer_nickname"),
                    resultSet.getLong("seller_id"),
                    resultSet.getString("seller_nickname"),
                    resultSet.getString("status"),
                    resultSet.getObject("last_message_id", Long.class),
                    resultSet.getString("last_message_text"),
                    nullableInstant(resultSet, "last_message_at"),
                    resultSet.getLong("unread_count"),
                    nullableInstant(resultSet, "archived_at"),
                    nullableInstant(resultSet, "deleted_at"),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant()
            );
        }
    }

    private static class MessageRowMapper implements RowMapper<MessageRecord> {

        @Override
        public MessageRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new MessageRecord(
                    resultSet.getLong("id"),
                    resultSet.getLong("conversation_id"),
                    resultSet.getObject("sender_id", Long.class),
                    resultSet.getString("sender_nickname"),
                    resultSet.getString("message_type"),
                    resultSet.getString("status"),
                    resultSet.getString("text_content"),
                    resultSet.getObject("card_id", Long.class),
                    resultSet.getTimestamp("sent_at").toInstant()
            );
        }
    }

    private static class BargainCardRowMapper implements RowMapper<BargainCardRecord> {

        @Override
        public BargainCardRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new BargainCardRecord(
                    resultSet.getLong("id"),
                    resultSet.getLong("conversation_id"),
                    resultSet.getBigDecimal("amount"),
                    resultSet.getString("note"),
                    resultSet.getString("action_status"),
                    resultSet.getLong("created_by_user_id"),
                    resultSet.getObject("acted_by_user_id", Long.class),
                    resultSet.getTimestamp("created_at").toInstant(),
                    nullableInstant(resultSet, "expires_at"),
                    nullableInstant(resultSet, "acted_at")
            );
        }
    }

    private static class MessageAttachmentRowMapper implements RowMapper<MessageAttachmentRecord> {

        @Override
        public MessageAttachmentRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new MessageAttachmentRecord(
                    resultSet.getLong("id"),
                    resultSet.getLong("message_id"),
                    resultSet.getLong("file_id"),
                    resultSet.getString("original_name"),
                    resultSet.getString("content_type"),
                    resultSet.getLong("byte_size"),
                    resultSet.getInt("sort_order")
            );
        }
    }
}
