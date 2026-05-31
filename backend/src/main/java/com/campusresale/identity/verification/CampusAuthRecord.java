package com.campusresale.identity.verification;

import java.time.Instant;

public record CampusAuthRecord(
        long id,
        long userId,
        String realName,
        String studentNo,
        String department,
        String campusEmail,
        int score,
        CampusVerificationStatus status,
        Long reviewedByAdminId,
        Instant reviewedAt,
        String failureReason,
        String identityClaimKey,
        Instant createdAt,
        Instant updatedAt
) {
}
