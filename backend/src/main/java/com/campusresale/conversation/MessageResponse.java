package com.campusresale.conversation;

import java.time.Instant;

public record MessageResponse(
        long id,
        long conversationId,
        ConversationParticipant sender,
        String messageType,
        String status,
        String textContent,
        Long cardId,
        Instant sentAt
) {

    public static MessageResponse from(MessageRecord record) {
        ConversationParticipant sender = record.senderId() == null
                ? null
                : new ConversationParticipant(record.senderId(), record.senderNickname());
        return new MessageResponse(
                record.id(),
                record.conversationId(),
                sender,
                record.messageType(),
                record.status(),
                record.textContent(),
                record.cardId(),
                record.sentAt()
        );
    }
}
