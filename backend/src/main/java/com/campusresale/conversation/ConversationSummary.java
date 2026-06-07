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
        String lastMessageText,
        Instant lastMessageAt,
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
                record.lastMessageText(),
                record.lastMessageAt(),
                record.createdAt(),
                record.updatedAt()
        );
    }
}
