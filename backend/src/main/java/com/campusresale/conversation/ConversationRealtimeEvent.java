package com.campusresale.conversation;

import com.campusresale.notification.NotificationResponse;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.Set;

public record ConversationRealtimeEvent(
        String type,
        long conversationId,
        MessageResponse message,
        BargainCardResponse bargainCard,
        ConversationSummary conversation,
        NotificationResponse notification,
        Long receiverUserId,
        Instant occurredAt,
        @JsonIgnore Set<Long> targetUserIds
) {

    public static ConversationRealtimeEvent of(
            String type,
            long conversationId,
            MessageResponse message,
            BargainCardResponse bargainCard,
            ConversationSummary conversation,
            NotificationResponse notification,
            Long receiverUserId,
            Set<Long> targetUserIds
    ) {
        return new ConversationRealtimeEvent(
                type,
                conversationId,
                message,
                bargainCard,
                conversation,
                notification,
                receiverUserId,
                Instant.now(),
                targetUserIds == null ? Set.of() : Set.copyOf(targetUserIds)
        );
    }
}
