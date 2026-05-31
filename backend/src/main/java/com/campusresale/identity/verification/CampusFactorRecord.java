package com.campusresale.identity.verification;

import java.time.Instant;
import java.util.List;

public record CampusFactorRecord(
        long id,
        long campusAuthId,
        CampusFactorType factorType,
        int scoreValue,
        CampusFactorStatus status,
        String submittedValue,
        Long storedFileId,
        String rejectedReason,
        int submitCount24h,
        Instant submitWindowStartedAt,
        List<Long> fileIds
) {
}
