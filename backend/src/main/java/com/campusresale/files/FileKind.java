package com.campusresale.files;

import com.campusresale.platform.api.ApiExceptions;
import java.util.Arrays;
import java.util.Map;

public enum FileKind {
    AVATAR,
    GOODS_IMAGE,
    CAMPUS_AUTH_MATERIAL,
    ORDER_EVIDENCE,
    REPORT_EVIDENCE,
    APPEAL_EVIDENCE,
    MESSAGE_IMAGE;

    public static FileKind parse(String value) {
        return Arrays.stream(values())
                .filter(kind -> kind.name().equals(value))
                .findFirst()
                .orElseThrow(() -> ApiExceptions.validation("文件用途不支持", Map.of("field", "fileKind")));
    }
}
