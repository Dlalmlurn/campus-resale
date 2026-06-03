package com.campusresale.notification;

import java.time.Instant;

public record NotificationResponse(
        long id,
        String type,
        String title,
        String content,
        String relatedType,
        Long relatedId,
        boolean read,
        Instant readAt,
        Instant createdAt
) {

    public static NotificationResponse from(NotificationRecord record) {
        return new NotificationResponse(
                record.id(),
                record.type().name(),
                record.title(),
                record.content(),
                record.relatedType(),
                record.relatedId(),
                record.readAt() != null,
                record.readAt(),
                record.createdAt()
        );
    }
}
