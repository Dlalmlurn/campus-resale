package com.campusresale.identity.verification;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusFactorResponse(
        String factorType,
        String status,
        int scoreValue,
        List<Long> fileIds
) {
}
