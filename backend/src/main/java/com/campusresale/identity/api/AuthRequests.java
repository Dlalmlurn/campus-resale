// 文件功能：定义 Auth API 的注册、登录、找回密码和注销请求体及字段校验规则。
package com.campusresale.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Auth API 请求体定义，集中声明注册、登录、找回密码和注销字段校验规则。
 */
public final class AuthRequests {

    /**
     * 用户名输入规则：允许大小写字母、数字和下划线；AuthService 会统一转成小写入库。
     */
    public static final String USERNAME_PATTERN = "^[A-Za-z0-9_]{3,20}$";

    /**
     * 用户名规则提示：同时用于注册和登录，保持前后端错误文案稳定。
     */
    public static final String USERNAME_MESSAGE = "用户名必须为 3 到 20 位，只能包含字母、数字和下划线";

    /**
     * 工具类不需要实例化；私有构造器用于阻止误创建对象。
     */
    private AuthRequests() {
    }

    /**
     * 注册请求体。
     *
     * @param username 用户名；3 到 20 位，只允许字母、数字和下划线，服务端保存为小写。
     * @param password 明文密码；只在请求处理中短暂存在，入库前会转为 BCrypt hash。
     * @param nickname 昵称；用于前端展示，不参与登录。
     * @param personalEmail 个人邮箱；可选字段，当前用于账号展示或后续通知扩展。
     */
    public record RegisterRequest(
            @NotBlank(message = "用户名不能为空")
            @Size(min = 3, max = 20, message = "用户名长度必须在 3 到 20 个字符之间")
            @Pattern(regexp = USERNAME_PATTERN, message = USERNAME_MESSAGE)
            String username,

            @NotBlank(message = "密码不能为空")
            @Size(min = 8, max = 120, message = "密码长度必须在 8 到 120 个字符之间")
            String password,

            @NotBlank(message = "昵称不能为空")
            @Size(min = 1, max = 80, message = "昵称长度必须在 1 到 80 个字符之间")
            String nickname,

            @Email(message = "个人邮箱格式不正确")
            @Size(max = 160, message = "个人邮箱不能超过 160 个字符")
            String personalEmail
    ) {
    }

    /**
     * 登录请求体。
     *
     * @param username 用户名；允许大小写输入，服务端会转成小写后查库。
     * @param password 明文密码；只用于和数据库 BCrypt hash 做一次校验。
     */
    public record LoginRequest(
            @NotBlank(message = "用户名不能为空")
            @Pattern(regexp = USERNAME_PATTERN, message = USERNAME_MESSAGE)
            String username,

            @NotBlank(message = "密码不能为空")
            String password
    ) {
    }

    /**
     * 发起密码找回请求体。
     *
     * @param email 注册时填写的个人邮箱；接口会统一返回受理，避免暴露邮箱是否存在。
     */
    public record PasswordResetRequest(
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 160, message = "邮箱不能超过 160 个字符")
            String email
    ) {
    }

    /**
     * 使用邮箱令牌重置密码请求体。
     *
     * @param token 邮件中的一次性重置令牌，服务端只保存其 SHA-256 hash。
     * @param newPassword 新密码，沿用注册密码长度规则。
     */
    public record PasswordResetConfirmRequest(
            @NotBlank(message = "重置令牌不能为空")
            String token,

            @NotBlank(message = "新密码不能为空")
            @Size(min = 8, max = 120, message = "密码长度必须在 8 到 120 个字符之间")
            String newPassword
    ) {
    }

    /**
     * 自助注销请求体。
     *
     * @param password 当前账号密码；用于防止他人借已登录浏览器误注销账号。
     */
    public record DeleteAccountRequest(
            @NotBlank(message = "密码不能为空")
            String password
    ) {
    }

    /**
     * 登录态直接修改密码请求体；无需邮箱，校验当前密码后设置新密码。
     *
     * @param currentPassword 当前密码，用于二次确认是本人操作。
     * @param newPassword 新密码，沿用注册密码长度规则。
     */
    public record ChangePasswordRequest(
            @NotBlank(message = "当前密码不能为空")
            String currentPassword,

            @NotBlank(message = "新密码不能为空")
            @Size(min = 8, max = 120, message = "密码长度必须在 8 到 120 个字符之间")
            String newPassword
    ) {
    }
}
