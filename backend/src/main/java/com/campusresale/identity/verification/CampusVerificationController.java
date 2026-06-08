// 文件功能：学生侧校园认证接口，提供本人认证资料读取、草稿保存和提交审核。
package com.campusresale.identity.verification;

import com.campusresale.identity.verification.CampusVerificationRequests.UpsertRequest;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.CurrentPrincipalContext;
import com.campusresale.platform.security.RequireLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequireLogin
@RestController
@RequestMapping("/api/verifications")
public class CampusVerificationController {

    private final CampusVerificationService campusVerificationService;

    public CampusVerificationController(CampusVerificationService campusVerificationService) {
        this.campusVerificationService = campusVerificationService;
    }

    /**
     * 查询当前用户认证快照；没有认证记录时返回 NONE，方便前端直接渲染初始表单。
     */
    @GetMapping("/me")
    public CampusVerificationResponse me(HttpServletRequest servletRequest) {
        return campusVerificationService.myVerification(principal(servletRequest));
    }

    /**
     * 保存认证资料草稿，包含姓名学号、院系邮箱和学生证/校园卡材料文件 id。
     */
    @PutMapping("/me")
    public CampusVerificationResponse updateMe(
            @Valid @RequestBody UpsertRequest request,
            HttpServletRequest servletRequest
    ) {
        return campusVerificationService.updateMyVerification(principal(servletRequest), request);
    }

    /**
     * 提交审核；必须已经保存有效证件材料并达到最低可信分。
     */
    @PostMapping("/me/submit")
    public CampusVerificationResponse submitMe(HttpServletRequest servletRequest) {
        return campusVerificationService.submitMyVerification(principal(servletRequest));
    }

    private CurrentPrincipal principal(HttpServletRequest request) {
        return CurrentPrincipalContext.get(request)
                .orElseThrow(ApiExceptions::authRequired);
    }
}
