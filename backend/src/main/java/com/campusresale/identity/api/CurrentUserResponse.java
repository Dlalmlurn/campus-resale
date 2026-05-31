// 文件功能：定义登录成功和 /api/auth/me 返回的当前用户响应结构。
package com.campusresale.identity.api;

import java.util.List;

/**
 * 当前登录用户响应 DTO，对齐 docs/阶段契约/m1_contracts.md 中的 CurrentUser 结构。
 */
public record CurrentUserResponse(
        /** 用户 id，对应 users.id。 */
        long id,
        /** 登录用户名，已按系统规则规范化。 */
        String username,
        /** 前端展示昵称。 */
        String nickname,
        /** 当前用户角色 code 列表，已排序方便前端稳定展示。 */
        List<String> roles,
        /** 校园认证状态；B 成员认证表合并前由 VERIFIED_STUDENT 角色过渡推导。 */
        String verificationStatus,
        /** 是否具备完整交易权限；M1 过渡期 VERIFIED_STUDENT 即为 true。 */
        boolean canTrade
) {
}
