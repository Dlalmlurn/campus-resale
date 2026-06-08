package com.campusresale.conversation;

import java.time.Instant;

public record ConversationRecord(
        long id,
        long goodsId,
        String goodsTitle,
        Long primaryImageFileId,
        long buyerId,
        String buyerNickname,
        long sellerId,
        String sellerNickname,
        String status,
        Long lastMessageId,
        String lastMessageText,
        Instant lastMessageAt,
        long unreadCount,
        Instant archivedAt,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
