package com.campusresale.order;

import java.time.Instant;

public record ReviewRecord(
        long id,
        long orderId,
        long reviewerId,
        long reviewedUserId,
        int rating,
        String content,
        ReviewStatus status,
        Instant submittedAt,
        Instant modifiedUntil,
        Instant visibleAt
) {
}
