// 文件功能：定义“必须拥有指定角色”的权限注解，适合管理员和认证学生接口。
package com.campusresale.platform.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记接口需要至少一个指定角色；例如管理员接口可写 @RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@RequireLogin
public @interface RequireRole {

    String[] value();
}
