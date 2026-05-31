package com.campusresale.files;

import com.campusresale.platform.api.ApiExceptions;
import java.util.Arrays;
import java.util.Map;

public enum VisibilityScope {
    PUBLIC,
    PRIVATE,
    PARTICIPANTS,
    ADMIN_ONLY;

    public static VisibilityScope parseOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(scope -> scope.name().equals(value))
                .findFirst()
                .orElseThrow(() -> ApiExceptions.validation("文件可见范围不支持", Map.of("field", "visibilityScope")));
    }
}
