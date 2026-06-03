package com.campusresale.notification;

import java.time.Instant;

public record NotificationRecord(
        long id,
        long receiverUserId,
        NotificationType type,
        String title,
        String content,
        String relatedType,
        Long relatedId,
        String dedupeKey,
        Instant readAt,
        Instant createdAt
) {
}
