package com.campusresale.conversation;

import java.math.RoundingMode;
import java.time.Instant;

public record BargainCardResponse(
        long id,
        long conversationId,
        String amount,
        String note,
        String actionStatus,
        long createdByUserId,
        Long actedByUserId,
        Instant createdAt,
        Instant expiresAt,
        Instant actedAt
) {

    public static BargainCardResponse from(BargainCardRecord record) {
        return new BargainCardResponse(
                record.id(),
                record.conversationId(),
                record.amount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                record.note(),
                record.actionStatus(),
                record.createdByUserId(),
                record.actedByUserId(),
                record.createdAt(),
                record.expiresAt(),
                record.actedAt()
        );
    }
}
