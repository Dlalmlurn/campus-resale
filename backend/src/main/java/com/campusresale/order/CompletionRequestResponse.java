package com.campusresale.order;

import java.time.Instant;

public record CompletionRequestResponse(
        long id,
        long orderId,
        String status,
        Instant windowStartsAt,
        Instant windowEndsAt,
        Instant confirmedAt,
        Instant createdAt
) {

    public static CompletionRequestResponse from(CompletionRequestRecord record) {
        return new CompletionRequestResponse(
                record.id(),
                record.orderId(),
                record.status().name(),
                record.windowStartsAt(),
                record.windowEndsAt(),
                record.confirmedAt(),
                record.createdAt()
        );
    }
}
