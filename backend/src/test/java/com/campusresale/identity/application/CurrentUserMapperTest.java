// 文件功能：验证 CurrentUser 的 M1 角色兜底交易权限推导逻辑。
package com.campusresale.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.campusresale.identity.api.CurrentUserResponse;
import com.campusresale.identity.domain.UserAccount;
import com.campusresale.identity.verification.CampusTradeEligibility;
import com.campusresale.identity.verification.CampusTradeEligibilityResolver;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CurrentUserMapperTest {

    private final CampusTradeEligibilityResolver campusTradeEligibilityResolver = org.mockito.Mockito.mock(CampusTradeEligibilityResolver.class);
    private final CurrentUserMapper mapper = new CurrentUserMapper(campusTradeEligibilityResolver);

    @Test
    void usesResolvedVerificationStatusAndCanTradeRule() {
        UserAccount user = new UserAccount(
                1L,
                "student_demo",
                "hash",
                "认证学生演示账号",
                44L,
                "ACTIVE",
                Set.of("REGISTERED_USER", "VERIFIED_STUDENT")
        );
        when(campusTradeEligibilityResolver.resolve(user.id(), user.roles()))
                .thenReturn(new CampusTradeEligibility("APPROVED", true));

        CurrentUserResponse response = mapper.fromUser(user);

        assertThat(response.verificationStatus()).isEqualTo("APPROVED");
        assertThat(response.canTrade()).isTrue();
        assertThat(response.avatarUrl()).isEqualTo("/api/files/44/content");
    }

    @Test
    void returnsNoneAndCannotTradeWithoutVerifiedStudentRole() {
        UserAccount user = new UserAccount(
                2L,
                "user_demo",
                "hash",
                "普通用户演示账号",
                null,
                "ACTIVE",
                Set.of("REGISTERED_USER")
        );
        when(campusTradeEligibilityResolver.resolve(user.id(), user.roles()))
                .thenReturn(new CampusTradeEligibility("NONE", false));

        CurrentUserResponse response = mapper.fromUser(user);

        assertThat(response.verificationStatus()).isEqualTo("NONE");
        assertThat(response.canTrade()).isFalse();
        assertThat(response.avatarUrl()).isNull();
    }
}
