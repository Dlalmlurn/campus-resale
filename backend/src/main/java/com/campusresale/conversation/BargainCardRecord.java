package com.campusresale.conversation;

import java.math.BigDecimal;
import java.time.Instant;

public record BargainCardRecord(
        long id,
        long conversationId,
        BigDecimal amount,
        String note,
        String actionStatus,
        long createdByUserId,
        Long actedByUserId,
        Instant createdAt,
        Instant expiresAt,
        Instant actedAt
) {
}
