package com.campusresale.identity.verification;

import java.util.Comparator;
import java.util.List;

public record CampusVerificationSnapshot(
        CampusAuthRecord auth,
        List<CampusFactorRecord> factors
) {

    public CampusVerificationResponse toResponse() {
        List<CampusFactorResponse> factorResponses = factors.stream()
                .sorted(Comparator.comparing(factor -> factor.factorType().name()))
                .map(factor -> new CampusFactorResponse(
                        factor.factorType().name(),
                        factor.status().name(),
                        factor.scoreValue(),
                        factor.fileIds()
                ))
                .toList();
        return new CampusVerificationResponse(
                auth.id(),
                auth.realName(),
                auth.studentNo(),
                auth.department(),
                auth.campusEmail(),
                auth.score(),
                auth.status().name(),
                factorResponses,
                auth.failureReason(),
                auth.updatedAt()
        );
    }
}
