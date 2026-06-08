package com.campusresale.identity.verification;

import com.campusresale.platform.api.ApiExceptions;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public enum CampusFactorType {
    NAME_STUDENT_NO,
    DEPARTMENT,
    CAMPUS_EMAIL,
    STUDENT_CARD,
    CAMPUS_CARD;

    private static final Set<CampusFactorType> DOCUMENT_TYPES = Set.of(STUDENT_CARD, CAMPUS_CARD);

    public static CampusFactorType parseDocumentType(String value) {
        CampusFactorType type = Arrays.stream(values())
                .filter(candidate -> candidate.name().equals(value))
                .findFirst()
                .orElseThrow(() -> ApiExceptions.validation("证件类型不支持", Map.of("field", "documentType")));
        if (!DOCUMENT_TYPES.contains(type)) {
            throw ApiExceptions.validation("证件类型只能是 STUDENT_CARD 或 CAMPUS_CARD", Map.of("field", "documentType"));
        }
        return type;
    }

    public boolean isDocumentType() {
        return DOCUMENT_TYPES.contains(this);
    }
}
