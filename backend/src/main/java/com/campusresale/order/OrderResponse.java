package com.campusresale.order;

import java.math.RoundingMode;
import java.time.Instant;

public record OrderResponse(
        long id,
        String orderNo,
        long goodsId,
        String goodsTitle,
        Long primaryImageFileId,
        Long conversationId,
        Long acceptedBargainCardId,
        ParticipantSummary buyer,
        ParticipantSummary seller,
        String frozenAmount,
        String status,
        Long tradePlaceId,
        String tradePlaceName,
        String tradePlaceDetail,
        Instant meetupTime,
        String buyerNote,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt
) {

    public static OrderResponse from(TradeOrderRecord record) {
        return new OrderResponse(
                record.id(),
                record.orderNo(),
                record.goodsId(),
                record.goodsTitle(),
                record.primaryImageFileId(),
                record.conversationId(),
                record.acceptedBargainCardId(),
                new ParticipantSummary(record.buyerId(), record.buyerNickname()),
                new ParticipantSummary(record.sellerId(), record.sellerNickname()),
                record.frozenAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                record.status().name(),
                record.tradePlaceId(),
                record.tradePlaceName(),
                record.tradePlaceDetail(),
                record.meetupTime(),
                record.buyerNote(),
                record.createdAt(),
                record.updatedAt(),
                record.closedAt()
        );
    }

    public record ParticipantSummary(long id, String nickname) {
    }
}
