package com.campusresale.files;

import java.time.Instant;

public record StoredFileRecord(
        long id,
        String storageBucket,
        String storageKey,
        String originalName,
        String contentType,
        long byteSize,
        String checksum,
        FileKind fileKind,
        VisibilityScope visibilityScope,
        Long ownerUserId,
        String businessType,
        Long businessId,
        FileAuditStatus auditStatus,
        Instant createdAt
) {
}
