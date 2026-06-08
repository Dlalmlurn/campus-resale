package com.campusresale.conversation;

import java.time.Instant;

public record MessageRecord(
        long id,
        long conversationId,
        Long senderId,
        String senderNickname,
        String messageType,
        String status,
        String textContent,
        Long cardId,
        Instant sentAt
) {
}
