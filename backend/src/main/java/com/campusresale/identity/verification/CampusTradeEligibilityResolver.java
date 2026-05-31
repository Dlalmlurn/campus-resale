package com.campusresale.identity.verification;

import com.campusresale.platform.security.SecurityProperties;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CampusTradeEligibilityResolver {

    private final CampusVerificationRepository campusVerificationRepository;

    public CampusTradeEligibilityResolver(CampusVerificationRepository campusVerificationRepository) {
        this.campusVerificationRepository = campusVerificationRepository;
    }

    public CampusTradeEligibility resolve(long userId, Set<String> roles) {
        boolean hasVerifiedStudentRole = roles.contains(SecurityProperties.VERIFIED_STUDENT_ROLE);
        return campusVerificationRepository.tradeEligibility(userId, hasVerifiedStudentRole)
                .orElseGet(() -> new CampusTradeEligibility("NONE", false));
    }
}
