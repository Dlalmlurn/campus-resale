// 文件功能：集中保存身份会话模块使用的 Cookie 名和角色编码常量。
package com.campusresale.platform.security;

/**
 * 身份会话相关常量，集中保存 Cookie 名和角色名，减少字符串散落。
 */
public final class SecurityProperties {

    /** 登录 Cookie 名，浏览器保存真实 session token。 */
    public static final String SESSION_COOKIE_NAME = "CR_SESSION";

    /** 普通注册用户角色。 */
    public static final String REGISTERED_USER_ROLE = "REGISTERED_USER";

    /** 已认证学生角色；M1 过渡期用于推导 canTrade=true。 */
    public static final String VERIFIED_STUDENT_ROLE = "VERIFIED_STUDENT";

    /** 内容管理员角色；按用户要求允许多端登录。 */
    public static final String CONTENT_ADMIN_ROLE = "CONTENT_ADMIN";

    /** 超级管理员角色；按用户要求执行单端登录。 */
    public static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    /**
     * 常量类不需要实例化。
     */
    private SecurityProperties() {
    }
}
