package com.campusresale.files;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FileRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public FileRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public StoredFileRecord create(
            String storageBucket,
            String storageKey,
            String originalName,
            String contentType,
            long byteSize,
            String checksum,
            FileKind fileKind,
            VisibilityScope visibilityScope,
            long ownerUserId
    ) {
        Long fileId = jdbcTemplate.queryForObject("""
                        INSERT INTO stored_files (
                            storage_bucket,
                            storage_key,
                            original_name,
                            content_type,
                            byte_size,
                            checksum,
                            file_kind,
                            visibility_scope,
                            owner_user_id,
                            audit_status,
                            created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                        RETURNING id
                        """,
                Long.class,
                storageBucket,
                storageKey,
                originalName,
                contentType,
                byteSize,
                checksum,
                fileKind.name(),
                visibilityScope.name(),
                ownerUserId,
                Timestamp.from(Instant.now())
        );
        return findById(fileId).orElseThrow(() -> new IllegalStateException("Created file cannot be loaded"));
    }

    public Optional<StoredFileRecord> findById(long fileId) {
        List<StoredFileRecord> files = jdbcTemplate.query("""
                        SELECT id,
                               storage_bucket,
                               storage_key,
                               original_name,
                               content_type,
                               byte_size,
                               checksum,
                               file_kind,
                               visibility_scope,
                               owner_user_id,
                               business_type,
                               business_id,
                               audit_status,
                               created_at
                        FROM stored_files
                        WHERE id = ?
                          AND deleted_at IS NULL
                        """,
                new StoredFileRowMapper(),
                fileId
        );
        return files.stream().findFirst();
    }

    public List<StoredFileRecord> findAllByIds(Collection<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        return namedParameterJdbcTemplate.query("""
                        SELECT id,
                               storage_bucket,
                               storage_key,
                               original_name,
                               content_type,
                               byte_size,
                               checksum,
                               file_kind,
                               visibility_scope,
                               owner_user_id,
                               business_type,
                               business_id,
                               audit_status,
                               created_at
                        FROM stored_files
                        WHERE id IN (:ids)
                          AND deleted_at IS NULL
                        """,
                Map.of("ids", fileIds),
                new StoredFileRowMapper()
        );
    }

    public void attachToBusiness(Collection<Long> fileIds, String businessType, long businessId) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        namedParameterJdbcTemplate.update("""
                        UPDATE stored_files
                        SET business_type = :businessType,
                            business_id = :businessId
                        WHERE id IN (:ids)
                        """,
                new MapSqlParameterSource()
                        .addValue("businessType", businessType)
                        .addValue("businessId", businessId)
                        .addValue("ids", fileIds)
        );
    }

    public void updateAuditStatus(Collection<Long> fileIds, FileAuditStatus auditStatus) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        namedParameterJdbcTemplate.update("""
                        UPDATE stored_files
                        SET audit_status = :auditStatus
                        WHERE id IN (:ids)
                        """,
                new MapSqlParameterSource()
                        .addValue("auditStatus", auditStatus.name())
                        .addValue("ids", fileIds)
        );
    }

    public void updateVisibilityScope(Collection<Long> fileIds, VisibilityScope visibilityScope) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        namedParameterJdbcTemplate.update("""
                        UPDATE stored_files
                        SET visibility_scope = :visibilityScope
                        WHERE id IN (:ids)
                        """,
                new MapSqlParameterSource()
                        .addValue("visibilityScope", visibilityScope.name())
                        .addValue("ids", fileIds)
        );
    }

    public void updateCampusAuthMaterialAuditStatus(long campusAuthId, FileAuditStatus auditStatus) {
        jdbcTemplate.update("""
                        UPDATE stored_files sf
                        SET audit_status = ?
                        FROM campus_auth_factor_files caff
                        JOIN campus_auth_factors caf ON caf.id = caff.campus_auth_factor_id
                        WHERE sf.id = caff.stored_file_id
                          AND caf.campus_auth_id = ?
                          AND sf.file_kind = 'CAMPUS_AUTH_MATERIAL'
                        """,
                auditStatus.name(),
                campusAuthId
        );
    }

    private static class StoredFileRowMapper implements RowMapper<StoredFileRecord> {

        @Override
        public StoredFileRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new StoredFileRecord(
                    resultSet.getLong("id"),
                    resultSet.getString("storage_bucket"),
                    resultSet.getString("storage_key"),
                    resultSet.getString("original_name"),
                    resultSet.getString("content_type"),
                    resultSet.getLong("byte_size"),
                    resultSet.getString("checksum"),
                    FileKind.valueOf(resultSet.getString("file_kind")),
                    VisibilityScope.valueOf(resultSet.getString("visibility_scope")),
                    resultSet.getObject("owner_user_id", Long.class),
                    resultSet.getString("business_type"),
                    resultSet.getObject("business_id", Long.class),
                    FileAuditStatus.valueOf(resultSet.getString("audit_status")),
                    resultSet.getTimestamp("created_at").toInstant()
            );
        }
    }
}
