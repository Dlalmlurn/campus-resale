package com.campusresale.goods;

import com.campusresale.platform.api.ApiExceptions;
import java.util.Arrays;
import java.util.Map;

public enum GoodsAuditStatus {
    NOT_SUBMITTED,
    PENDING,
    APPROVED,
    REJECTED;

    public static GoodsAuditStatus parseFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.name().equals(value))
                .findFirst()
                .orElseThrow(() -> ApiExceptions.validation("商品审核状态不支持", Map.of("field", "auditStatus")));
    }
}
