package com.campusresale.identity.verification;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CampusVerificationResponse(
        Long id,
        String realName,
        String studentNo,
        String department,
        String campusEmail,
        int score,
        String status,
        List<CampusFactorResponse> factors,
        String failureReason,
        Instant updatedAt
) {

    public static CampusVerificationResponse none() {
        return new CampusVerificationResponse(null, null, null, null, null, 0, "NONE", List.of(), null, null);
    }
}
