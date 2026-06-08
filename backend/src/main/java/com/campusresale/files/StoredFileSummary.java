package com.campusresale.files;

import java.time.Instant;

public record StoredFileSummary(
        long id,
        String originalName,
        String contentType,
        long byteSize,
        String fileKind,
        String visibilityScope,
        String auditStatus,
        Instant createdAt
) {

    public static StoredFileSummary from(StoredFileRecord record) {
        return new StoredFileSummary(
                record.id(),
                record.originalName(),
                record.contentType(),
                record.byteSize(),
                record.fileKind().name(),
                record.visibilityScope().name(),
                record.auditStatus().name(),
                record.createdAt()
        );
    }
}
