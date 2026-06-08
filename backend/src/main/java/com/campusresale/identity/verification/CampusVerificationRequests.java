// 文件功能：校园认证接口请求体定义，集中约束学生提交资料和管理员审核理由长度。
package com.campusresale.identity.verification;

import jakarta.validation.constraints.Size;
import java.util.List;

public final class CampusVerificationRequests {

    private CampusVerificationRequests() {
    }

    /**
     * 学生侧保存认证草稿的请求体；documentFileIds 必须来自当前用户上传的校园认证材料。
     */
    public record UpsertRequest(
            @Size(max = 80, message = "姓名不能超过 80 个字符")
            String realName,

            @Size(max = 80, message = "学号不能超过 80 个字符")
            String studentNo,

            @Size(max = 120, message = "院系不能超过 120 个字符")
            String department,

            @Size(max = 160, message = "校园邮箱不能超过 160 个字符")
            String campusEmail,

            String documentType,

            List<Long> documentFileIds
    ) {
    }

    /**
     * 管理员审核请求体；reason 可为空，服务层会补默认通过/驳回理由。
     */
    public record ReviewRequest(
            @Size(max = 500, message = "审核理由不能超过 500 个字符")
            String reason
    ) {
    }
}
