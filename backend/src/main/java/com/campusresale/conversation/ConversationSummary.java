package com.campusresale.conversation;

import java.time.Instant;

public record ConversationSummary(
        long id,
        long goodsId,
        String goodsTitle,
        Long primaryImageFileId,
        ConversationParticipant buyer,
        ConversationParticipant seller,
        String status,
        Long lastMessageId,
        String lastMessageText,
        Instant lastMessageAt,
        long unreadCount,
        boolean archived,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt
) {

    public static ConversationSummary from(ConversationRecord record) {
        return new ConversationSummary(
                record.id(),
                record.goodsId(),
                record.goodsTitle(),
                record.primaryImageFileId(),
                new ConversationParticipant(record.buyerId(), record.buyerNickname()),
                new ConversationParticipant(record.sellerId(), record.sellerNickname()),
                record.status(),
                record.lastMessageId(),
                record.lastMessageText(),
                record.lastMessageAt(),
                record.unreadCount(),
                record.archivedAt() != null,
                record.deletedAt() != null,
                record.createdAt(),
                record.updatedAt()
        );
    }
}
