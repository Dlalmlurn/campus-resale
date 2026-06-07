package com.campusresale.goods;

import java.time.Instant;

public record GoodsSummary(
        long id,
        String title,
        String description,
        String conditionLevel,
        String listPrice,
        String status,
        String auditStatus,
        SellerSummary seller,
        CategorySummary category,
        GoodsImageSummary primaryImage,
        String recommendationReason,
        Instant publishedAt
) {
}
