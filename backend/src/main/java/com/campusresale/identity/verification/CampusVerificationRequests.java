package com.campusresale.identity.verification;

import jakarta.validation.constraints.Size;
import java.util.List;

public final class CampusVerificationRequests {

    private CampusVerificationRequests() {
    }

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

    public record ReviewRequest(
            @Size(max = 500, message = "审核理由不能超过 500 个字符")
            String reason
    ) {
    }
}
