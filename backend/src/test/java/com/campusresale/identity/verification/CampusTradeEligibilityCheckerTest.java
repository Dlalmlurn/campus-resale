// 文件功能：验证校园交易资格检查器对角色和 canTrade 完整规则的判断。
package com.campusresale.identity.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.SecurityProperties;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CampusTradeEligibilityCheckerTest {

    private final CampusTradeEligibilityResolver campusTradeEligibilityResolver = mock(CampusTradeEligibilityResolver.class);
    private final CampusTradeEligibilityChecker checker = new CampusTradeEligibilityChecker(campusTradeEligibilityResolver);

    @Test
    void rejectsUserWithoutVerifiedStudentRole() {
        CurrentPrincipal principal = principal(Set.of("REGISTERED_USER"));

        assertThat(checker.canTrade(principal)).isFalse();

        // 没有认证学生角色时无需查询认证表，直接拒绝即可。
        verifyNoInteractions(campusTradeEligibilityResolver);
    }

    @Test
    void rejectsVerifiedStudentWhenResolverSaysCannotTrade() {
        CurrentPrincipal principal = principal(Set.of("REGISTERED_USER", SecurityProperties.VERIFIED_STUDENT_ROLE));
        when(campusTradeEligibilityResolver.resolve(principal.id(), principal.roles()))
                .thenReturn(new CampusTradeEligibility("APPROVED", false));

        assertThat(checker.canTrade(principal)).isFalse();
    }

    @Test
    void allowsVerifiedStudentWhenResolverSaysCanTrade() {
        CurrentPrincipal principal = principal(Set.of("REGISTERED_USER", SecurityProperties.VERIFIED_STUDENT_ROLE));
        when(campusTradeEligibilityResolver.resolve(principal.id(), principal.roles()))
                .thenReturn(new CampusTradeEligibility("APPROVED", true));

        assertThat(checker.canTrade(principal)).isTrue();
    }

    @Test
    void rejectsLockedAccountBeforeCampusEligibilityLookup() {
        CurrentPrincipal principal = principal("LOCKED", Set.of("REGISTERED_USER", SecurityProperties.VERIFIED_STUDENT_ROLE));

        assertThat(checker.canTrade(principal)).isFalse();

        verifyNoInteractions(campusTradeEligibilityResolver);
    }

    private CurrentPrincipal principal(Set<String> roles) {
        return principal("ACTIVE", roles);
    }

    private CurrentPrincipal principal(String accountStatus, Set<String> roles) {
        return new CurrentPrincipal(
                1L,
                "student_demo",
                "Student Demo",
                accountStatus,
                roles,
                1L,
                Instant.now().plusSeconds(60),
                Instant.now().plusSeconds(120)
        );
    }
}
