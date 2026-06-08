package com.campusresale.goods;

public record CategorySummary(
        long id,
        String code,
        String name,
        Long parentId
) {
}
