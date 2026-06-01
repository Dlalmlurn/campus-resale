package com.campusresale.goods;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record GoodsRecord(
        long id,
        long sellerId,
        String sellerNickname,
        long categoryId,
        String categoryCode,
        String categoryName,
        String title,
        String description,
        ConditionLevel conditionLevel,
        BigDecimal listPrice,
        Long tradePlaceId,
        String tradePlaceDetail,
        String availableTimeText,
        GoodsStatus status,
        GoodsAuditStatus auditStatus,
        Long primaryImageFileId,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public GoodsSummary toSummary() {
        GoodsImageSummary primaryImage = primaryImageFileId == null
                ? null
                : new GoodsImageSummary(primaryImageFileId, "/api/files/" + primaryImageFileId + "/content");
        return new GoodsSummary(
                id,
                title,
                description,
                conditionLevel.name(),
                listPrice.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                status.name(),
                auditStatus.name(),
                new SellerSummary(sellerId, sellerNickname),
                new CategorySummary(categoryId, categoryCode, categoryName, null),
                primaryImage,
                publishedAt
        );
    }
}
