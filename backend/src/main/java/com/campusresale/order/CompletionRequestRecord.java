package com.campusresale.order;

import java.time.Instant;

public record CompletionRequestRecord(
        long id,
        long orderId,
        long sellerId,
        long buyerId,
        CompletionRequestStatus status,
        Instant windowStartsAt,
        Instant windowEndsAt,
        Instant confirmedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
