// 文件功能：把校园认证交易资格规则接入平台权限拦截器。
package com.campusresale.identity.verification;

import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.SecurityProperties;
import com.campusresale.platform.security.TradeEligibilityChecker;
import org.springframework.stereotype.Component;

/**
 * 校园交易资格检查器。
 *
 * <p>这个类是平台安全层和身份认证规则之间的适配层：拦截器只问 canTrade，
 * 具体的认证状态、分数、证件因子和角色判断仍由 CampusTradeEligibilityResolver 负责。</p>
 */
@Component
public class CampusTradeEligibilityChecker implements TradeEligibilityChecker {

    /**
     * 校园认证交易资格解析器，复用 /api/auth/me 已经使用的完整 canTrade 口径。
     */
    private final CampusTradeEligibilityResolver campusTradeEligibilityResolver;

    public CampusTradeEligibilityChecker(CampusTradeEligibilityResolver campusTradeEligibilityResolver) {
        this.campusTradeEligibilityResolver = campusTradeEligibilityResolver;
    }

    @Override
    public boolean canTrade(CurrentPrincipal principal) {
        // 先检查角色，能提前拒绝明显不是认证学生的账号，也和商品发布、订单交易规则保持一致。
        if (!principal.hasRole(SecurityProperties.VERIFIED_STUDENT_ROLE)) {
            return false;
        }

        // 再走完整校园认证规则：认证状态、分数、证件因子和角色都满足时才返回 true。
        CampusTradeEligibility eligibility = campusTradeEligibilityResolver.resolve(principal.id(), principal.roles());
        return eligibility.canTrade();
    }
}
