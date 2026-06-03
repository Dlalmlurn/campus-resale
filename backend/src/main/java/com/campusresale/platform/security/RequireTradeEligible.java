// 文件功能：定义“必须具备完整交易资格”的权限注解，供 N1 订单、支付和评价接口复用。
package com.campusresale.platform.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记接口需要当前用户具备完整交易资格。
 *
 * <p>完整交易资格由 TradeEligibilityChecker 统一判断，通常要求用户已登录、
 * 拥有 VERIFIED_STUDENT 角色，并且校园认证规则计算出的 canTrade=true。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@RequireLogin
public @interface RequireTradeEligible {
}
