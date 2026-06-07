package com.campusresale.conversation;

import java.time.Instant;
import java.util.List;

public record MessageResponse(
        long id,
        long conversationId,
        ConversationParticipant sender,
        String messageType,
        String status,
        String textContent,
        Long cardId,
        List<MessageAttachmentResponse> attachments,
        Instant sentAt
) {

    public static MessageResponse from(MessageRecord record) {
        return from(record, List.of());
    }

    public static MessageResponse from(MessageRecord record, List<MessageAttachmentResponse> attachments) {
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
                attachments == null ? List.of() : attachments,
                record.sentAt()
        );
    }
}
