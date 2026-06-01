package com.campusresale.goods;

import com.campusresale.platform.api.ApiExceptions;
import java.util.Arrays;
import java.util.Map;

public enum ConditionLevel {
    NEW,
    LIKE_NEW,
    LIGHTLY_USED,
    NOTICEABLY_USED;

    public static ConditionLevel parse(String value) {
        return Arrays.stream(values())
                .filter(level -> level.name().equals(value))
                .findFirst()
                .orElseThrow(() -> ApiExceptions.validation("商品成色不支持", Map.of("field", "conditionLevel")));
    }
}
