// 文件功能：定义交易资格检查接口，让平台权限层不直接依赖具体校园认证表。
package com.campusresale.platform.security;

/**
 * 交易资格检查器。
 *
 * <p>平台安全层只关心“当前用户能不能交易”，具体规则由 identity.verification 模块实现。</p>
 */
public interface TradeEligibilityChecker {

    /**
     * 判断当前登录主体是否具备完整交易资格。
     *
     * @param principal 当前请求识别出的登录用户。
     * @return true 表示允许进入需要交易资格的接口；false 表示返回 403 FORBIDDEN。
     */
    boolean canTrade(CurrentPrincipal principal);
}
