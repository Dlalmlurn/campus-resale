package com.campusresale.notification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id,
                   receiver_user_id,
                   type,
                   title,
                   content,
                   related_type,
                   related_id,
                   dedupe_key,
                   read_at,
                   created_at
            FROM notifications
            """;

    private final JdbcTemplate jdbcTemplate;

    public NotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public NotificationRecord create(
            long receiverUserId,
            NotificationType type,
            String title,
            String content,
            String relatedType,
            Long relatedId,
            String dedupeKey
    ) {
        if (dedupeKey == null) {
            Long notificationId = insert(receiverUserId, type, title, content, relatedType, relatedId, null);
            return findById(notificationId).orElseThrow(() -> new IllegalStateException("Created notification cannot be loaded"));
        }

        List<Long> insertedIds = jdbcTemplate.queryForList("""
                        INSERT INTO notifications (
                            receiver_user_id,
                            type,
                            title,
                            content,
                            related_type,
                            related_id,
                            dedupe_key,
                            created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (receiver_user_id, dedupe_key)
                            WHERE dedupe_key IS NOT NULL
                        DO NOTHING
                        RETURNING id
                        """,
                Long.class,
                receiverUserId,
                type.name(),
                title,
                content,
                relatedType,
                relatedId,
                dedupeKey,
                Timestamp.from(Instant.now())
        );
        if (!insertedIds.isEmpty()) {
            return findById(insertedIds.getFirst())
                    .orElseThrow(() -> new IllegalStateException("Created notification cannot be loaded"));
        }
        return findByReceiverAndDedupeKey(receiverUserId, dedupeKey)
                .orElseThrow(() -> new IllegalStateException("Deduplicated notification cannot be loaded"));
    }

    public List<NotificationRecord> findByReceiver(long receiverUserId, boolean unreadOnly, int limit, int offset) {
        return jdbcTemplate.query(
                buildListSql(unreadOnly),
                new NotificationRowMapper(),
                receiverUserId,
                limit,
                offset
        );
    }

    public long countByReceiver(long receiverUserId, boolean unreadOnly) {
        return jdbcTemplate.queryForObject(
                buildCountSql(unreadOnly),
                Long.class,
                receiverUserId
        );
    }

    /**
     * 拼接未读筛选列表 SQL。每段都自带前导空格，避免文本块相邻拼接漏空格导致 `IS NULLORDER BY` 这类非法语法。
     */
    static String buildListSql(boolean unreadOnly) {
        return SELECT_COLUMNS
                + " WHERE receiver_user_id = ?"
                + unreadClause(unreadOnly)
                + " ORDER BY created_at DESC, id DESC"
                + " LIMIT ? OFFSET ?";
    }

    /**
     * 拼接未读筛选计数 SQL，复用同一套未读条件，保证 count 与 list 口径一致。
     */
    static String buildCountSql(boolean unreadOnly) {
        return "SELECT COUNT(*) FROM notifications"
                + " WHERE receiver_user_id = ?"
                + unreadClause(unreadOnly);
    }

    private static String unreadClause(boolean unreadOnly) {
        return unreadOnly ? " AND read_at IS NULL" : "";
    }

    public long unreadCount(long receiverUserId) {
        return countByReceiver(receiverUserId, true);
    }

    public Optional<NotificationRecord> findByIdAndReceiver(long notificationId, long receiverUserId) {
        return jdbcTemplate.query(SELECT_COLUMNS + """
                        WHERE id = ?
                          AND receiver_user_id = ?
                        """,
                new NotificationRowMapper(),
                notificationId,
                receiverUserId
        ).stream().findFirst();
    }

    public Optional<NotificationRecord> findById(long notificationId) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = ?",
                new NotificationRowMapper(),
                notificationId
        ).stream().findFirst();
    }

    public Optional<NotificationRecord> findByReceiverAndDedupeKey(long receiverUserId, String dedupeKey) {
        return jdbcTemplate.query(SELECT_COLUMNS + """
                        WHERE receiver_user_id = ?
                          AND dedupe_key = ?
                        """,
                new NotificationRowMapper(),
                receiverUserId,
                dedupeKey
        ).stream().findFirst();
    }

    public void markRead(long notificationId, long receiverUserId, Instant readAt) {
        jdbcTemplate.update("""
                        UPDATE notifications
                        SET read_at = COALESCE(read_at, ?)
                        WHERE id = ?
                          AND receiver_user_id = ?
                        """,
                Timestamp.from(readAt),
                notificationId,
                receiverUserId
        );
    }

    public int markAllRead(long receiverUserId, Instant readAt) {
        return jdbcTemplate.update("""
                        UPDATE notifications
                        SET read_at = ?
                        WHERE receiver_user_id = ?
                          AND read_at IS NULL
                        """,
                Timestamp.from(readAt),
                receiverUserId
        );
    }

    public int markRelatedRead(long receiverUserId, String relatedType, long relatedId, Instant readAt) {
        return jdbcTemplate.update("""
                        UPDATE notifications
                        SET read_at = COALESCE(read_at, ?)
                        WHERE receiver_user_id = ?
                          AND related_type = ?
                          AND related_id = ?
                          AND read_at IS NULL
                        """,
                Timestamp.from(readAt),
                receiverUserId,
                relatedType,
                relatedId
        );
    }

    private Long insert(
            long receiverUserId,
            NotificationType type,
            String title,
            String content,
            String relatedType,
            Long relatedId,
            String dedupeKey
    ) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO notifications (
                            receiver_user_id,
                            type,
                            title,
                            content,
                            related_type,
                            related_id,
                            dedupe_key,
                            created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                receiverUserId,
                type.name(),
                title,
                content,
                relatedType,
                relatedId,
                dedupeKey,
                Timestamp.from(Instant.now())
        );
    }

    private static class NotificationRowMapper implements RowMapper<NotificationRecord> {

        @Override
        public NotificationRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            Timestamp readAt = resultSet.getTimestamp("read_at");
            return new NotificationRecord(
                    resultSet.getLong("id"),
                    resultSet.getLong("receiver_user_id"),
                    NotificationType.valueOf(resultSet.getString("type")),
                    resultSet.getString("title"),
                    resultSet.getString("content"),
                    resultSet.getString("related_type"),
                    resultSet.getObject("related_id", Long.class),
                    resultSet.getString("dedupe_key"),
                    readAt == null ? null : readAt.toInstant(),
                    resultSet.getTimestamp("created_at").toInstant()
            );
        }
    }
}
