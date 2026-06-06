package com.campusresale.governance;

import com.campusresale.governance.N3Requests.ApplyPenaltyRequest;
import com.campusresale.governance.N3Requests.DecideRefundRequest;
import com.campusresale.governance.N3Requests.HandleReportRequest;
import com.campusresale.governance.N3Requests.LiftPenaltyRequest;
import com.campusresale.governance.N3Requests.ReviewAppealRequest;
import com.campusresale.governance.N3Responses.AdminQueueResponse;
import com.campusresale.governance.N3Responses.AppealResponse;
import com.campusresale.governance.N3Responses.PenaltyResponse;
import com.campusresale.governance.N3Responses.RefundResponse;
import com.campusresale.governance.N3Responses.ReportResponse;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.CurrentPrincipalContext;
import com.campusresale.platform.security.RequireRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})
@RequestMapping("/api/admin/n3")
public class AdminN3GovernanceController {

    private final N3GovernanceService service;

    public AdminN3GovernanceController(N3GovernanceService service) {
        this.service = service;
    }

    @GetMapping("/queue")
    public AdminQueueResponse queue() {
        return service.adminQueue();
    }

    @GetMapping("/reports")
    public List<ReportResponse> reports(HttpServletRequest request) {
        return service.adminReports(principal(request), ip(request));
    }

    @PostMapping("/reports/{id}/handle")
    public ReportResponse handleReport(
            @PathVariable long id,
            @RequestBody HandleReportRequest body,
            HttpServletRequest request
    ) {
        return service.handleReport(id, body, principal(request), ip(request));
    }

    @GetMapping("/appeals")
    public List<AppealResponse> appeals(HttpServletRequest request) {
        return service.adminAppeals(principal(request), ip(request));
    }

    @PostMapping("/appeals/{id}/review")
    public AppealResponse reviewAppeal(
            @PathVariable long id,
            @RequestBody ReviewAppealRequest body,
            HttpServletRequest request
    ) {
        return service.reviewAppeal(id, body, principal(request), ip(request));
    }

    @GetMapping("/refunds")
    public List<RefundResponse> refunds(HttpServletRequest request) {
        return service.adminRefunds(principal(request), ip(request));
    }

    @PostMapping("/refunds/{id}/decide")
    public RefundResponse decideRefund(
            @PathVariable long id,
            @RequestBody DecideRefundRequest body,
            HttpServletRequest request
    ) {
        return service.decideRefund(id, body, principal(request), ip(request));
    }

    @PostMapping("/penalties")
    public PenaltyResponse applyPenalty(
            @RequestBody ApplyPenaltyRequest body,
            HttpServletRequest request
    ) {
        return service.applyPenalty(body, principal(request), ip(request));
    }

    @PostMapping("/penalties/{id}/lift")
    public PenaltyResponse liftPenalty(
            @PathVariable long id,
            @RequestBody(required = false) LiftPenaltyRequest body,
            HttpServletRequest request
    ) {
        return service.liftPenalty(id, body, principal(request), ip(request));
    }

    private CurrentPrincipal principal(HttpServletRequest request) {
        return CurrentPrincipalContext.get(request)
                .orElseThrow(ApiExceptions::authRequired);
    }

    private String ip(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
