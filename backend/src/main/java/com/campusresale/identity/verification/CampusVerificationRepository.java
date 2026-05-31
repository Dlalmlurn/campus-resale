package com.campusresale.identity.verification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CampusVerificationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public CampusVerificationRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Optional<CampusVerificationSnapshot> findByUserId(long userId) {
        return findAuth("""
                        SELECT id,
                               user_id,
                               real_name,
                               student_no,
                               department,
                               campus_email,
                               score,
                               status,
                               reviewed_by_admin_id,
                               reviewed_at,
                               failure_reason,
                               identity_claim_key,
                               created_at,
                               updated_at
                        FROM campus_auths
                        WHERE user_id = ?
                        """,
                userId
        );
    }

    public Optional<CampusVerificationSnapshot> findById(long authId) {
        return findAuth("""
                        SELECT id,
                               user_id,
                               real_name,
                               student_no,
                               department,
                               campus_email,
                               score,
                               status,
                               reviewed_by_admin_id,
                               reviewed_at,
                               failure_reason,
                               identity_claim_key,
                               created_at,
                               updated_at
                        FROM campus_auths
                        WHERE id = ?
                        """,
                authId
        );
    }

    public Optional<CampusVerificationSnapshot> findByIdForUpdate(long authId) {
        return findAuth("""
                        SELECT id,
                               user_id,
                               real_name,
                               student_no,
                               department,
                               campus_email,
                               score,
                               status,
                               reviewed_by_admin_id,
                               reviewed_at,
                               failure_reason,
                               identity_claim_key,
                               created_at,
                               updated_at
                        FROM campus_auths
                        WHERE id = ?
                        FOR UPDATE
                        """,
                authId
        );
    }

    public long upsertDraft(
            long userId,
            String realName,
            String studentNo,
            String department,
            String campusEmail,
            CampusVerificationStatus status
    ) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO campus_auths (
                            user_id,
                            real_name,
                            student_no,
                            department,
                            campus_email,
                            score,
                            status,
                            failure_reason,
                            reviewed_by_admin_id,
                            reviewed_at,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, 0, ?, NULL, NULL, NULL, ?, ?)
                        ON CONFLICT (user_id) DO UPDATE
                        SET real_name = EXCLUDED.real_name,
                            student_no = EXCLUDED.student_no,
                            department = EXCLUDED.department,
                            campus_email = EXCLUDED.campus_email,
                            status = EXCLUDED.status,
                            failure_reason = NULL,
                            reviewed_by_admin_id = NULL,
                            reviewed_at = NULL,
                            updated_at = EXCLUDED.updated_at
                        RETURNING id
                        """,
                Long.class,
                userId,
                realName,
                studentNo,
                department,
                campusEmail,
                status.name(),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now())
        );
    }

    public long upsertFactor(
            long campusAuthId,
            CampusFactorType factorType,
            int scoreValue,
            CampusFactorStatus status,
            String submittedValue
    ) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO campus_auth_factors (
                            campus_auth_id,
                            factor_type,
                            score_value,
                            status,
                            submitted_value,
                            rejected_reason,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
                        ON CONFLICT (campus_auth_id, factor_type) DO UPDATE
                        SET score_value = EXCLUDED.score_value,
                            status = EXCLUDED.status,
                            submitted_value = EXCLUDED.submitted_value,
                            rejected_reason = NULL,
                            updated_at = EXCLUDED.updated_at
                        RETURNING id
                        """,
                Long.class,
                campusAuthId,
                factorType.name(),
                scoreValue,
                status.name(),
                submittedValue,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now())
        );
    }

    public void deleteFactor(long campusAuthId, CampusFactorType factorType) {
        jdbcTemplate.update("""
                        DELETE FROM campus_auth_factors
                        WHERE campus_auth_id = ?
                          AND factor_type = ?
                        """,
                campusAuthId,
                factorType.name()
        );
    }

    public void replaceFactorFiles(long factorId, List<Long> fileIds) {
        jdbcTemplate.update("DELETE FROM campus_auth_factor_files WHERE campus_auth_factor_id = ?", factorId);
        for (int index = 0; index < fileIds.size(); index++) {
            jdbcTemplate.update("""
                            INSERT INTO campus_auth_factor_files (
                                campus_auth_factor_id,
                                stored_file_id,
                                sort_order,
                                created_at
                            )
                            VALUES (?, ?, ?, ?)
                            ON CONFLICT (campus_auth_factor_id, stored_file_id) DO UPDATE
                            SET sort_order = EXCLUDED.sort_order
                            """,
                    factorId,
                    fileIds.get(index),
                    index,
                    Timestamp.from(Instant.now())
            );
        }
    }

    public void attachFilesToCampusAuth(List<Long> fileIds, long campusAuthId) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        namedParameterJdbcTemplate.update("""
                        UPDATE stored_files
                        SET business_type = 'CAMPUS_AUTH',
                            business_id = :campusAuthId
                        WHERE id IN (:fileIds)
                        """,
                new MapSqlParameterSource()
                        .addValue("campusAuthId", campusAuthId)
                        .addValue("fileIds", fileIds)
        );
    }

    public int recalculateScore(long campusAuthId) {
        return jdbcTemplate.queryForObject("""
                        UPDATE campus_auths
                        SET score = COALESCE((
                                SELECT SUM(score_value)
                                FROM campus_auth_factors
                                WHERE campus_auth_id = ?
                                  AND status = 'VERIFIED'
                            ), 0),
                            updated_at = ?
                        WHERE id = ?
                        RETURNING score
                        """,
                Integer.class,
                campusAuthId,
                Timestamp.from(Instant.now()),
                campusAuthId
        );
    }

    public boolean incrementSubmitCount(long factorId, Instant now, int limit) {
        Instant windowThreshold = now.minusSeconds(24 * 60 * 60);
        int updated = jdbcTemplate.update("""
                        UPDATE campus_auth_factors
                        SET submit_count_24h = CASE
                                WHEN submit_window_started_at IS NULL OR submit_window_started_at <= ? THEN 1
                                ELSE submit_count_24h + 1
                            END,
                            submit_window_started_at = CASE
                                WHEN submit_window_started_at IS NULL OR submit_window_started_at <= ? THEN ?
                                ELSE submit_window_started_at
                            END,
                            updated_at = ?
                        WHERE id = ?
                          AND (
                                submit_window_started_at IS NULL
                                OR submit_window_started_at <= ?
                                OR submit_count_24h < ?
                          )
                        """,
                Timestamp.from(windowThreshold),
                Timestamp.from(windowThreshold),
                Timestamp.from(now),
                Timestamp.from(now),
                factorId,
                Timestamp.from(windowThreshold),
                limit
        );
        return updated > 0;
    }

    public void markSubmitted(long campusAuthId) {
        jdbcTemplate.update("""
                        UPDATE campus_auths
                        SET status = 'PENDING_REVIEW',
                            updated_at = ?
                        WHERE id = ?
                        """,
                Timestamp.from(Instant.now()),
                campusAuthId
        );
    }

    public void approveDocumentFactors(long campusAuthId, long adminId, Instant now) {
        jdbcTemplate.update("""
                        UPDATE campus_auth_factors
                        SET status = 'VERIFIED',
                            score_value = 40,
                            reviewed_by_admin_id = ?,
                            reviewed_at = ?,
                            rejected_reason = NULL,
                            updated_at = ?
                        WHERE campus_auth_id = ?
                          AND factor_type IN ('STUDENT_CARD', 'CAMPUS_CARD')
                        """,
                adminId,
                Timestamp.from(now),
                Timestamp.from(now),
                campusAuthId
        );
    }

    public void markApproved(long campusAuthId, long adminId, String identityClaimKey, Instant now) {
        jdbcTemplate.update("""
                        UPDATE campus_auths
                        SET score = COALESCE((
                                SELECT SUM(score_value)
                                FROM campus_auth_factors
                                WHERE campus_auth_id = ?
                                  AND status = 'VERIFIED'
                            ), 0),
                            status = 'APPROVED',
                            reviewed_by_admin_id = ?,
                            reviewed_at = ?,
                            failure_reason = NULL,
                            identity_claim_key = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                campusAuthId,
                adminId,
                Timestamp.from(now),
                identityClaimKey,
                Timestamp.from(now),
                campusAuthId
        );
    }

    public void rejectDocumentFactors(long campusAuthId, long adminId, String reason, Instant now) {
        jdbcTemplate.update("""
                        UPDATE campus_auth_factors
                        SET status = 'REJECTED',
                            score_value = 0,
                            reviewed_by_admin_id = ?,
                            reviewed_at = ?,
                            rejected_reason = ?,
                            updated_at = ?
                        WHERE campus_auth_id = ?
                          AND factor_type IN ('STUDENT_CARD', 'CAMPUS_CARD')
                          AND status = 'PENDING'
                        """,
                adminId,
                Timestamp.from(now),
                reason,
                Timestamp.from(now),
                campusAuthId
        );
    }

    public void markRejected(long campusAuthId, long adminId, String reason, Instant now) {
        jdbcTemplate.update("""
                        UPDATE campus_auths
                        SET score = COALESCE((
                                SELECT SUM(score_value)
                                FROM campus_auth_factors
                                WHERE campus_auth_id = ?
                                  AND status = 'VERIFIED'
                            ), 0),
                            status = 'REJECTED',
                            reviewed_by_admin_id = ?,
                            reviewed_at = ?,
                            failure_reason = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                campusAuthId,
                adminId,
                Timestamp.from(now),
                reason,
                Timestamp.from(now),
                campusAuthId
        );
    }

    public List<CampusVerificationSnapshot> list(String status, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("status", status)
                .addValue("limit", pageSize)
                .addValue("offset", offset);
        List<CampusAuthRecord> auths = namedParameterJdbcTemplate.query("""
                        SELECT id,
                               user_id,
                               real_name,
                               student_no,
                               department,
                               campus_email,
                               score,
                               status,
                               reviewed_by_admin_id,
                               reviewed_at,
                               failure_reason,
                               identity_claim_key,
                               created_at,
                               updated_at
                        FROM campus_auths
                        WHERE (:status IS NULL OR status = :status)
                        ORDER BY updated_at DESC, id DESC
                        LIMIT :limit OFFSET :offset
                        """,
                parameters,
                new CampusAuthRowMapper()
        );
        return auths.stream()
                .map(auth -> new CampusVerificationSnapshot(auth, loadFactors(auth.id())))
                .toList();
    }

    public long count(String status) {
        Long total = namedParameterJdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM campus_auths
                        WHERE (:status IS NULL OR status = :status)
                        """,
                new MapSqlParameterSource().addValue("status", status),
                Long.class
        );
        return total == null ? 0 : total;
    }

    public Optional<CampusTradeEligibility> tradeEligibility(long userId, boolean hasVerifiedStudentRole) {
        Optional<CampusVerificationSnapshot> snapshot = findByUserId(userId);
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        CampusAuthRecord auth = snapshot.get().auth();
        boolean hasVerifiedDocument = snapshot.get().factors().stream()
                .anyMatch(factor -> factor.factorType().isDocumentType()
                        && factor.status() == CampusFactorStatus.VERIFIED);
        boolean canTrade = auth.status() == CampusVerificationStatus.APPROVED
                && auth.score() >= 60
                && hasVerifiedDocument
                && hasVerifiedStudentRole;
        return Optional.of(new CampusTradeEligibility(auth.status().name(), canTrade));
    }

    private Optional<CampusVerificationSnapshot> findAuth(String sql, long id) {
        List<CampusAuthRecord> auths = jdbcTemplate.query(sql, new CampusAuthRowMapper(), id);
        return auths.stream()
                .findFirst()
                .map(auth -> new CampusVerificationSnapshot(auth, loadFactors(auth.id())));
    }

    private List<CampusFactorRecord> loadFactors(long campusAuthId) {
        List<CampusFactorRecord> factors = jdbcTemplate.query("""
                        SELECT id,
                               campus_auth_id,
                               factor_type,
                               score_value,
                               status,
                               submitted_value,
                               stored_file_id,
                               rejected_reason,
                               submit_count_24h,
                               submit_window_started_at
                        FROM campus_auth_factors
                        WHERE campus_auth_id = ?
                        ORDER BY factor_type
                        """,
                new CampusFactorRowMapper(),
                campusAuthId
        );
        if (factors.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> fileIdsByFactor = loadFileIds(factors);
        List<CampusFactorRecord> withFiles = new ArrayList<>();
        for (CampusFactorRecord factor : factors) {
            withFiles.add(new CampusFactorRecord(
                    factor.id(),
                    factor.campusAuthId(),
                    factor.factorType(),
                    factor.scoreValue(),
                    factor.status(),
                    factor.submittedValue(),
                    factor.storedFileId(),
                    factor.rejectedReason(),
                    factor.submitCount24h(),
                    factor.submitWindowStartedAt(),
                    fileIdsByFactor.getOrDefault(factor.id(), List.of())
            ));
        }
        return withFiles;
    }

    private Map<Long, List<Long>> loadFileIds(List<CampusFactorRecord> factors) {
        List<Long> factorIds = factors.stream().map(CampusFactorRecord::id).toList();
        Map<Long, List<Long>> result = new HashMap<>();
        namedParameterJdbcTemplate.query("""
                        SELECT campus_auth_factor_id,
                               stored_file_id
                        FROM campus_auth_factor_files
                        WHERE campus_auth_factor_id IN (:factorIds)
                        ORDER BY campus_auth_factor_id, sort_order, id
                        """,
                Map.of("factorIds", factorIds),
                rs -> {
                    long factorId = rs.getLong("campus_auth_factor_id");
                    result.computeIfAbsent(factorId, ignored -> new ArrayList<>())
                            .add(rs.getLong("stored_file_id"));
                }
        );
        return result;
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static class CampusAuthRowMapper implements RowMapper<CampusAuthRecord> {

        @Override
        public CampusAuthRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new CampusAuthRecord(
                    resultSet.getLong("id"),
                    resultSet.getLong("user_id"),
                    resultSet.getString("real_name"),
                    resultSet.getString("student_no"),
                    resultSet.getString("department"),
                    resultSet.getString("campus_email"),
                    resultSet.getInt("score"),
                    CampusVerificationStatus.valueOf(resultSet.getString("status")),
                    resultSet.getObject("reviewed_by_admin_id", Long.class),
                    nullableInstant(resultSet, "reviewed_at"),
                    resultSet.getString("failure_reason"),
                    resultSet.getString("identity_claim_key"),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant()
            );
        }
    }

    private static class CampusFactorRowMapper implements RowMapper<CampusFactorRecord> {

        @Override
        public CampusFactorRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new CampusFactorRecord(
                    resultSet.getLong("id"),
                    resultSet.getLong("campus_auth_id"),
                    CampusFactorType.valueOf(resultSet.getString("factor_type")),
                    resultSet.getInt("score_value"),
                    CampusFactorStatus.valueOf(resultSet.getString("status")),
                    resultSet.getString("submitted_value"),
                    resultSet.getObject("stored_file_id", Long.class),
                    resultSet.getString("rejected_reason"),
                    resultSet.getInt("submit_count_24h"),
                    nullableInstant(resultSet, "submit_window_started_at"),
                    List.of()
            );
        }
    }
}
