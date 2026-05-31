// 文件功能：定义“必须登录”的权限注解，标在 Controller 类或方法上。
package com.campusresale.platform.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记接口必须登录，权限拦截器会在 Controller 执行前检查当前请求是否有有效 session。
 */
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireLogin {
}
