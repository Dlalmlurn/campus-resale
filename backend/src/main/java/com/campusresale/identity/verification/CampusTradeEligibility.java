package com.campusresale.identity.verification;

public record CampusTradeEligibility(
        String verificationStatus,
        boolean canTrade
) {
}
