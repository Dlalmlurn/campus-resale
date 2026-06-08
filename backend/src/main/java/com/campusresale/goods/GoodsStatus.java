package com.campusresale.goods;

import com.campusresale.platform.api.ApiExceptions;
import java.util.Arrays;
import java.util.Map;

public enum GoodsStatus {
    DRAFT,
    PENDING_REVIEW,
    ON_SALE,
    RESERVED,
    SOLD,
    OFF_SHELF,
    DELETED;

    public static GoodsStatus parseFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.name().equals(value))
                .findFirst()
                .orElseThrow(() -> ApiExceptions.validation("商品状态不支持", Map.of("field", "status")));
    }
}
