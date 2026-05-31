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

    @GetMapping("/me")
    public CampusVerificationResponse me(HttpServletRequest servletRequest) {
        return campusVerificationService.myVerification(principal(servletRequest));
    }

    @PutMapping("/me")
    public CampusVerificationResponse updateMe(
            @Valid @RequestBody UpsertRequest request,
            HttpServletRequest servletRequest
    ) {
        return campusVerificationService.updateMyVerification(principal(servletRequest), request);
    }

    @PostMapping("/me/submit")
    public CampusVerificationResponse submitMe(HttpServletRequest servletRequest) {
        return campusVerificationService.submitMyVerification(principal(servletRequest));
    }

    private CurrentPrincipal principal(HttpServletRequest request) {
        return CurrentPrincipalContext.get(request)
                .orElseThrow(ApiExceptions::authRequired);
    }
}
