package com.campusresale.order;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeOrderRecord(
        long id,
        String orderNo,
        long goodsId,
        String goodsTitle,
        Long primaryImageFileId,
        Long conversationId,
        Long acceptedBargainCardId,
        long buyerId,
        String buyerNickname,
        long sellerId,
        String sellerNickname,
        BigDecimal frozenAmount,
        TradeOrderStatus status,
        Long tradePlaceId,
        String tradePlaceName,
        String tradePlaceDetail,
        Instant meetupTime,
        String buyerNote,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt
) {
}
