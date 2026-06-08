// 文件功能：把用户账号或当前主体转换成前端需要的 CurrentUser 响应。
package com.campusresale.identity.application;

import com.campusresale.identity.api.CurrentUserResponse;
import com.campusresale.identity.domain.UserAccount;
import com.campusresale.identity.verification.CampusTradeEligibility;
import com.campusresale.identity.verification.CampusTradeEligibilityResolver;
import com.campusresale.platform.security.CurrentPrincipal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * CurrentUser 响应组装器，集中处理角色排序和 M1 认证状态过渡逻辑。
 */
@Component
public class CurrentUserMapper {

    private final CampusTradeEligibilityResolver campusTradeEligibilityResolver;

    public CurrentUserMapper(CampusTradeEligibilityResolver campusTradeEligibilityResolver) {
        this.campusTradeEligibilityResolver = campusTradeEligibilityResolver;
    }

    /**
     * 根据数据库用户对象组装 CurrentUser。
     *
     * @param userAccount 已加载角色的用户账号对象。
     * @return 前端需要的当前用户响应。
     */
    public CurrentUserResponse fromUser(UserAccount userAccount) {
        return toResponse(
                userAccount.id(),
                userAccount.username(),
                userAccount.nickname(),
                userAccount.roles()
        );
    }

    /**
     * 根据请求上下文中的当前登录主体组装 CurrentUser。
     *
     * @param principal Filter 从 session 中识别出的当前用户。
     * @return 前端需要的当前用户响应。
     */
    public CurrentUserResponse fromPrincipal(CurrentPrincipal principal) {
        return toResponse(
                principal.id(),
                principal.username(),
                principal.nickname(),
                principal.roles()
        );
    }

    /**
     * CurrentUser 统一组装入口。
     *
     * @param id 用户 id。
     * @param username 登录用户名。
     * @param nickname 展示昵称。
     * @param roleSet 用户角色集合。
     * @return 排序后的角色列表和认证交易状态。
     */
    private CurrentUserResponse toResponse(long id, String username, String nickname, java.util.Set<String> roleSet) {
        // 复制成 List 后排序，保证接口响应稳定，方便前端比较和测试断言。
        List<String> roles = new ArrayList<>(roleSet);
        roles.sort(Comparator.naturalOrder());

        CampusTradeEligibility eligibility = campusTradeEligibilityResolver.resolve(id, roleSet);

        return new CurrentUserResponse(id, username, nickname, roles, eligibility.verificationStatus(), eligibility.canTrade());
    }
}
