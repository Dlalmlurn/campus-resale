package com.campusresale.order;

import java.time.Instant;

public record ReviewResponse(
        long id,
        long orderId,
        long reviewerId,
        long reviewedUserId,
        int rating,
        String content,
        String status,
        Instant submittedAt,
        Instant modifiedUntil,
        Instant visibleAt
) {

    public static ReviewResponse from(ReviewRecord record) {
        return new ReviewResponse(
                record.id(),
                record.orderId(),
                record.reviewerId(),
                record.reviewedUserId(),
                record.rating(),
                record.content(),
                record.status().name(),
                record.submittedAt(),
                record.modifiedUntil(),
                record.visibleAt()
        );
    }
}
