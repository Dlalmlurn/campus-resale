package com.campusresale.goods;

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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GoodsRepository {

    private static final String SUMMARY_SELECT = """
            SELECT g.id,
                   g.seller_id,
                   seller.nickname AS seller_nickname,
                   g.category_id,
                   c.code AS category_code,
                   c.name AS category_name,
                   g.title,
                   g.description,
                   g.condition_level,
                   g.list_price,
                   g.trade_place_id,
                   g.trade_place_detail,
                   g.available_time_text,
                   g.status,
                   g.audit_status,
                   pi.file_id AS primary_image_file_id,
                   CASE
                       WHEN c.code = 'BOOKS' THEN '教材资料匹配你的浏览偏好'
                       WHEN g.published_at >= now() - interval '7 days' THEN '近期上新，适合先看'
                       WHEN g.condition_level IN ('NEW', 'LIKE_NEW') THEN '成色较新，适合优先比较'
                       ELSE '校内闲置，支持线下面交'
                   END AS recommendation_reason,
                   g.published_at,
                   g.created_at,
                   g.updated_at
            FROM goods g
            JOIN users seller ON seller.id = g.seller_id
            JOIN categories c ON c.id = g.category_id
            LEFT JOIN LATERAL (
                SELECT gi.file_id
                FROM goods_images gi
                WHERE gi.goods_id = g.id
                ORDER BY gi.is_primary DESC, gi.sort_order, gi.id
                LIMIT 1
            ) pi ON TRUE
            """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public GoodsRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public long create(long sellerId, GoodsWriteData data) {
        Long goodsId = jdbcTemplate.queryForObject("""
                        INSERT INTO goods (
                            seller_id,
                            category_id,
                            title,
                            description,
                            condition_level,
                            list_price,
                            trade_place_id,
                            trade_place_detail,
                            available_time_text,
                            status,
                            audit_status,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', 'NOT_SUBMITTED', ?, ?)
                        RETURNING id
                        """,
                Long.class,
                sellerId,
                data.categoryId(),
                data.title(),
                data.description(),
                data.conditionLevel().name(),
                data.listPrice(),
                data.tradePlaceId(),
                data.tradePlaceDetail(),
                data.availableTimeText(),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now())
        );
        return goodsId;
    }

    public void updateCore(long goodsId, GoodsWriteData data) {
        jdbcTemplate.update("""
                        UPDATE goods
                        SET category_id = ?,
                            title = ?,
                            description = ?,
                            condition_level = ?,
                            list_price = ?,
                            trade_place_id = ?,
                            trade_place_detail = ?,
                            available_time_text = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                data.categoryId(),
                data.title(),
                data.description(),
                data.conditionLevel().name(),
                data.listPrice(),
                data.tradePlaceId(),
                data.tradePlaceDetail(),
                data.availableTimeText(),
                Timestamp.from(Instant.now()),
                goodsId
        );
    }

    public void replaceImages(long goodsId, List<Long> imageFileIds) {
        jdbcTemplate.update("DELETE FROM goods_images WHERE goods_id = ?", goodsId);
        for (int index = 0; index < imageFileIds.size(); index++) {
            jdbcTemplate.update("""
                            INSERT INTO goods_images (goods_id, file_id, sort_order, is_primary, created_at)
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    goodsId,
                    imageFileIds.get(index),
                    index,
                    index == 0,
                    Timestamp.from(Instant.now())
            );
        }
    }

    public void replaceTags(long goodsId, List<Long> tagIds) {
        jdbcTemplate.update("DELETE FROM goods_tags WHERE goods_id = ?", goodsId);
        for (Long tagId : tagIds) {
            jdbcTemplate.update("""
                            INSERT INTO goods_tags (goods_id, tag_id)
                            VALUES (?, ?)
                            ON CONFLICT (goods_id, tag_id) DO NOTHING
                            """,
                    goodsId,
                    tagId
            );
        }
    }

    public Optional<GoodsRecord> findById(long goodsId) {
        List<GoodsRecord> records = jdbcTemplate.query(SUMMARY_SELECT + " WHERE g.id = ? AND g.is_deleted = FALSE",
                new GoodsRecordRowMapper(),
                goodsId
        );
        return records.stream().findFirst();
    }

    public Optional<GoodsRecord> findByIdForUpdate(long goodsId) {
        List<GoodsRecord> records = jdbcTemplate.query(SUMMARY_SELECT + " WHERE g.id = ? AND g.is_deleted = FALSE FOR UPDATE OF g",
                new GoodsRecordRowMapper(),
                goodsId
        );
        return records.stream().findFirst();
    }

    public List<Long> imageFileIds(long goodsId) {
        return jdbcTemplate.queryForList("""
                        SELECT file_id
                        FROM goods_images
                        WHERE goods_id = ?
                        ORDER BY sort_order, id
                        """,
                Long.class,
                goodsId
        );
    }

    public List<Long> tagIds(long goodsId) {
        return jdbcTemplate.queryForList("""
                        SELECT tag_id
                        FROM goods_tags
                        WHERE goods_id = ?
                        ORDER BY tag_id
                        """,
                Long.class,
                goodsId
        );
    }

    public void markSubmitted(long goodsId) {
        jdbcTemplate.update("""
                        UPDATE goods
                        SET status = 'PENDING_REVIEW',
                            audit_status = 'PENDING',
                            updated_at = ?
                        WHERE id = ?
                        """,
                Timestamp.from(Instant.now()),
                goodsId
        );
    }

    public void markApproved(long goodsId, Instant now) {
        jdbcTemplate.update("""
                        UPDATE goods
                        SET status = 'ON_SALE',
                            audit_status = 'APPROVED',
                            published_at = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                Timestamp.from(now),
                Timestamp.from(now),
                goodsId
        );
    }

    public void markRejected(long goodsId) {
        jdbcTemplate.update("""
                        UPDATE goods
                        SET status = 'DRAFT',
                            audit_status = 'REJECTED',
                            updated_at = ?
                        WHERE id = ?
                        """,
                Timestamp.from(Instant.now()),
                goodsId
        );
    }

    public Long currentOccupiedOrderId(long goodsId) {
        return jdbcTemplate.queryForObject("""
                        SELECT current_occupied_order_id
                        FROM goods
                        WHERE id = ?
                        """,
                Long.class,
                goodsId
        );
    }

    public int reserveForOrder(long goodsId, long orderId) {
        return jdbcTemplate.update("""
                        UPDATE goods
                        SET status = 'RESERVED',
                            current_occupied_order_id = ?,
                            updated_at = ?
                        WHERE id = ?
                          AND status = 'ON_SALE'
                          AND audit_status = 'APPROVED'
                          AND current_occupied_order_id IS NULL
                          AND is_deleted = FALSE
                        """,
                orderId,
                Timestamp.from(Instant.now()),
                goodsId
        );
    }

    public int releaseReservation(long goodsId, long orderId) {
        return jdbcTemplate.update("""
                        UPDATE goods
                        SET status = 'ON_SALE',
                            current_occupied_order_id = NULL,
                            updated_at = ?
                        WHERE id = ?
                          AND status = 'RESERVED'
                          AND current_occupied_order_id = ?
                          AND is_deleted = FALSE
                        """,
                Timestamp.from(Instant.now()),
                goodsId,
                orderId
        );
    }

    public int markSoldFromOrder(long goodsId, long orderId) {
        return jdbcTemplate.update("""
                        UPDATE goods
                        SET status = 'SOLD',
                            current_occupied_order_id = NULL,
                            updated_at = ?
                        WHERE id = ?
                          AND status = 'RESERVED'
                          AND current_occupied_order_id = ?
                          AND is_deleted = FALSE
                        """,
                Timestamp.from(Instant.now()),
                goodsId,
                orderId
        );
    }

    public void insertAuditRecord(long goodsId, long adminId, AuditResult result, String reason, String ruleSummary) {
        jdbcTemplate.update("""
                        INSERT INTO audit_records (
                            target_type,
                            target_id,
                            admin_id,
                            result,
                            reason,
                            rule_summary,
                            created_at
                        )
                        VALUES ('GOODS', ?, ?, ?, ?, ?, ?)
                        """,
                goodsId,
                adminId,
                result.name(),
                reason,
                ruleSummary,
                Timestamp.from(Instant.now())
        );
    }

    public void insertRuleHit(long goodsId, ForbiddenTerm term, String matchedTextHash) {
        jdbcTemplate.update("""
                        INSERT INTO rule_hit_records (
                            target_type,
                            target_id,
                            rule_type,
                            rule_code,
                            matched_text_hash,
                            severity,
                            decision_hint,
                            created_at
                        )
                        VALUES ('GOODS', ?, ?, ?, ?, ?, 'REJECT', ?)
                        """,
                goodsId,
                term.termType(),
                term.term(),
                matchedTextHash,
                term.severity(),
                Timestamp.from(Instant.now())
        );
    }

    public List<ForbiddenTerm> enabledForbiddenTerms() {
        return jdbcTemplate.query("""
                        SELECT id, term, term_type, severity
                        FROM forbidden_terms
                        WHERE enabled = TRUE
                        ORDER BY severity DESC, id
                        """,
                (rs, rowNum) -> new ForbiddenTerm(
                        rs.getLong("id"),
                        rs.getString("term"),
                        rs.getString("term_type"),
                        rs.getString("severity")
                )
        );
    }

    public List<GoodsRecord> listMine(MineCriteria criteria, int page, int pageSize) {
        MapSqlParameterSource params = pageParams(page, pageSize).addValue("sellerId", criteria.sellerId());
        StringBuilder sql = new StringBuilder(SUMMARY_SELECT)
                .append(" WHERE g.seller_id = :sellerId AND g.is_deleted = FALSE");
        appendStatusFilters(sql, params, criteria.status(), criteria.auditStatus());
        sql.append(" ORDER BY g.updated_at DESC, g.id DESC LIMIT :limit OFFSET :offset");
        return namedParameterJdbcTemplate.query(sql.toString(), params, new GoodsRecordRowMapper());
    }

    public long countMine(MineCriteria criteria) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("sellerId", criteria.sellerId());
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM goods g WHERE g.seller_id = :sellerId AND g.is_deleted = FALSE");
        appendStatusFilters(sql, params, criteria.status(), criteria.auditStatus());
        return count(sql.toString(), params);
    }

    public List<GoodsRecord> listPublic(SearchCriteria criteria, int page, int pageSize) {
        MapSqlParameterSource params = pageParams(page, pageSize);
        StringBuilder sql = new StringBuilder(SUMMARY_SELECT)
                .append(" WHERE g.status = 'ON_SALE' AND g.audit_status = 'APPROVED' AND g.is_deleted = FALSE");
        appendSearchFilters(sql, params, criteria);
        appendSort(sql, criteria.sort());
        sql.append(" LIMIT :limit OFFSET :offset");
        return namedParameterJdbcTemplate.query(sql.toString(), params, new GoodsRecordRowMapper());
    }

    public long countPublic(SearchCriteria criteria) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM goods g
                WHERE g.status = 'ON_SALE'
                  AND g.audit_status = 'APPROVED'
                  AND g.is_deleted = FALSE
                """);
        appendSearchFilters(sql, params, criteria);
        return count(sql.toString(), params);
    }

    public List<GoodsRecord> listAdmin(AdminCriteria criteria, int page, int pageSize) {
        MapSqlParameterSource params = pageParams(page, pageSize);
        StringBuilder sql = new StringBuilder(SUMMARY_SELECT)
                .append(" WHERE g.is_deleted = FALSE");
        appendStatusFilters(sql, params, criteria.status(), criteria.auditStatus());
        sql.append(" ORDER BY g.updated_at DESC, g.id DESC LIMIT :limit OFFSET :offset");
        return namedParameterJdbcTemplate.query(sql.toString(), params, new GoodsRecordRowMapper());
    }

    public long countAdmin(AdminCriteria criteria) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM goods g WHERE g.is_deleted = FALSE");
        appendStatusFilters(sql, params, criteria.status(), criteria.auditStatus());
        return count(sql.toString(), params);
    }

    private MapSqlParameterSource pageParams(int page, int pageSize) {
        return new MapSqlParameterSource()
                .addValue("limit", pageSize)
                .addValue("offset", (page - 1) * pageSize);
    }

    private void appendStatusFilters(
            StringBuilder sql,
            MapSqlParameterSource params,
            GoodsStatus status,
            GoodsAuditStatus auditStatus
    ) {
        if (status != null) {
            sql.append(" AND g.status = :status");
            params.addValue("status", status.name());
        }
        if (auditStatus != null) {
            sql.append(" AND g.audit_status = :auditStatus");
            params.addValue("auditStatus", auditStatus.name());
        }
    }

    private void appendSearchFilters(StringBuilder sql, MapSqlParameterSource params, SearchCriteria criteria) {
        if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
            sql.append("""
                     AND (
                        g.search_vector @@ plainto_tsquery('simple', :keyword)
                        OR g.title ILIKE :keywordLike
                        OR g.description ILIKE :keywordLike
                     )
                    """);
            params.addValue("keyword", criteria.keyword().trim());
            params.addValue("keywordLike", "%" + criteria.keyword().trim() + "%");
        }
        if (criteria.categoryId() != null) {
            sql.append(" AND g.category_id = :categoryId");
            params.addValue("categoryId", criteria.categoryId());
        }
        if (criteria.minPrice() != null) {
            sql.append(" AND g.list_price >= :minPrice");
            params.addValue("minPrice", criteria.minPrice());
        }
        if (criteria.maxPrice() != null) {
            sql.append(" AND g.list_price <= :maxPrice");
            params.addValue("maxPrice", criteria.maxPrice());
        }
        if (criteria.conditionLevel() != null) {
            sql.append(" AND g.condition_level = :conditionLevel");
            params.addValue("conditionLevel", criteria.conditionLevel().name());
        }
        if (criteria.placeId() != null) {
            sql.append(" AND g.trade_place_id = :placeId");
            params.addValue("placeId", criteria.placeId());
        }
    }

    private void appendSort(StringBuilder sql, String sort) {
        if ("PRICE_ASC".equals(sort)) {
            sql.append(" ORDER BY g.list_price ASC, g.published_at DESC, g.id DESC");
        } else if ("PRICE_DESC".equals(sort)) {
            sql.append(" ORDER BY g.list_price DESC, g.published_at DESC, g.id DESC");
        } else if ("RECOMMENDED".equals(sort)) {
            sql.append("""
                     ORDER BY CASE
                         WHEN c.code = 'BOOKS' THEN 0
                         WHEN g.condition_level IN ('NEW', 'LIKE_NEW') THEN 1
                         ELSE 2
                     END,
                     g.published_at DESC,
                     g.id DESC
                    """);
        } else {
            sql.append(" ORDER BY g.published_at DESC, g.id DESC");
        }
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long total = namedParameterJdbcTemplate.queryForObject(sql, params, Long.class);
        return total == null ? 0 : total;
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record GoodsWriteData(
            String title,
            String description,
            long categoryId,
            ConditionLevel conditionLevel,
            BigDecimal listPrice,
            Long tradePlaceId,
            String tradePlaceDetail,
            String availableTimeText
    ) {
    }

    public record MineCriteria(
            long sellerId,
            GoodsStatus status,
            GoodsAuditStatus auditStatus
    ) {
    }

    public record SearchCriteria(
            String keyword,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            ConditionLevel conditionLevel,
            Long placeId,
            String sort
    ) {
    }

    public record AdminCriteria(
            GoodsStatus status,
            GoodsAuditStatus auditStatus
    ) {
    }

    public record ForbiddenTerm(
            long id,
            String term,
            String termType,
            String severity
    ) {
    }

    private static class GoodsRecordRowMapper implements RowMapper<GoodsRecord> {

        @Override
        public GoodsRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new GoodsRecord(
                    resultSet.getLong("id"),
                    resultSet.getLong("seller_id"),
                    resultSet.getString("seller_nickname"),
                    resultSet.getLong("category_id"),
                    resultSet.getString("category_code"),
                    resultSet.getString("category_name"),
                    resultSet.getString("title"),
                    resultSet.getString("description"),
                    ConditionLevel.valueOf(resultSet.getString("condition_level")),
                    resultSet.getBigDecimal("list_price"),
                    resultSet.getObject("trade_place_id", Long.class),
                    resultSet.getString("trade_place_detail"),
                    resultSet.getString("available_time_text"),
                    GoodsStatus.valueOf(resultSet.getString("status")),
                    GoodsAuditStatus.valueOf(resultSet.getString("audit_status")),
                    resultSet.getObject("primary_image_file_id", Long.class),
                    resultSet.getString("recommendation_reason"),
                    nullableInstant(resultSet, "published_at"),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant()
            );
        }
    }
}
