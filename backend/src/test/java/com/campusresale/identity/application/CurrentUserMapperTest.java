// 文件功能：验证 CurrentUser 的 M1 角色兜底交易权限推导逻辑。
package com.campusresale.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusresale.identity.api.CurrentUserResponse;
import com.campusresale.identity.domain.UserAccount;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CurrentUserMapperTest {

    private final CurrentUserMapper mapper = new CurrentUserMapper();

    @Test
    void treatsVerifiedStudentRoleAsTemporaryCanTradeFallback() {
        UserAccount user = new UserAccount(
                1L,
                "student_demo",
                "hash",
                "认证学生演示账号",
                "ACTIVE",
                Set.of("REGISTERED_USER", "VERIFIED_STUDENT")
        );

        CurrentUserResponse response = mapper.fromUser(user);

        assertThat(response.verificationStatus()).isEqualTo("APPROVED");
        assertThat(response.canTrade()).isTrue();
    }

    @Test
    void returnsNoneAndCannotTradeWithoutVerifiedStudentRole() {
        UserAccount user = new UserAccount(
                2L,
                "user_demo",
                "hash",
                "普通用户演示账号",
                "ACTIVE",
                Set.of("REGISTERED_USER")
        );

        CurrentUserResponse response = mapper.fromUser(user);

        assertThat(response.verificationStatus()).isEqualTo("NONE");
        assertThat(response.canTrade()).isFalse();
    }
}
