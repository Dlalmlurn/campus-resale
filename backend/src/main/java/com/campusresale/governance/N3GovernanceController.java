package com.campusresale.governance;

import com.campusresale.governance.N3Requests.CreateRefundRequest;
import com.campusresale.governance.N3Requests.SubmitAppealRequest;
import com.campusresale.governance.N3Requests.SubmitReportRequest;
import com.campusresale.governance.N3Responses.AppealResponse;
import com.campusresale.governance.N3Responses.CreditSummaryResponse;
import com.campusresale.governance.N3Responses.FavoriteResponse;
import com.campusresale.governance.N3Responses.FollowResponse;
import com.campusresale.governance.N3Responses.GovernanceOverviewResponse;
import com.campusresale.governance.N3Responses.RefundResponse;
import com.campusresale.governance.N3Responses.ReportResponse;
import com.campusresale.governance.N3Responses.ToggleResponse;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.CurrentPrincipalContext;
import com.campusresale.platform.security.RequireLogin;
import com.campusresale.platform.security.RequireTradeEligible;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireLogin
@RequestMapping("/api/n3")
public class N3GovernanceController {

    private final N3GovernanceService service;

    public N3GovernanceController(N3GovernanceService service) {
        this.service = service;
    }

    @GetMapping("/governance-overview")
    public GovernanceOverviewResponse overview(HttpServletRequest request) {
        return service.overview(principal(request));
    }

    @PostMapping("/reports")
    public ReportResponse submitReport(@RequestBody SubmitReportRequest body, HttpServletRequest request) {
        return service.submitReport(body, principal(request));
    }

    @GetMapping("/reports")
    public List<ReportResponse> myReports(HttpServletRequest request) {
        return service.myReports(principal(request));
    }

    @PostMapping("/appeals")
    public AppealResponse submitAppeal(@RequestBody SubmitAppealRequest body, HttpServletRequest request) {
        return service.submitAppeal(body, principal(request));
    }

    @GetMapping("/appeals")
    public List<AppealResponse> myAppeals(HttpServletRequest request) {
        return service.myAppeals(principal(request));
    }

    @RequireTradeEligible
    @PostMapping("/refunds")
    public RefundResponse createRefund(@RequestBody CreateRefundRequest body, HttpServletRequest request) {
        return service.createRefund(body, principal(request));
    }

    @GetMapping("/refunds")
    public List<RefundResponse> myRefunds(HttpServletRequest request) {
        return service.myRefunds(principal(request));
    }

    @GetMapping("/credit/me")
    public CreditSummaryResponse myCredit(HttpServletRequest request) {
        return service.myCredit(principal(request));
    }

    @PostMapping("/favorites/{goodsId}")
    public ToggleResponse favorite(@PathVariable long goodsId, HttpServletRequest request) {
        return service.favorite(goodsId, principal(request));
    }

    @DeleteMapping("/favorites/{goodsId}")
    public ToggleResponse unfavorite(@PathVariable long goodsId, HttpServletRequest request) {
        return service.unfavorite(goodsId, principal(request));
    }

    @GetMapping("/favorites")
    public List<FavoriteResponse> favorites(HttpServletRequest request) {
        return service.myFavorites(principal(request));
    }

    @PostMapping("/follows/{userId}")
    public ToggleResponse follow(@PathVariable long userId, HttpServletRequest request) {
        return service.follow(userId, principal(request));
    }

    @DeleteMapping("/follows/{userId}")
    public ToggleResponse unfollow(@PathVariable long userId, HttpServletRequest request) {
        return service.unfollow(userId, principal(request));
    }

    @GetMapping("/follows")
    public List<FollowResponse> follows(HttpServletRequest request) {
        return service.myFollows(principal(request));
    }

    private CurrentPrincipal principal(HttpServletRequest request) {
        return CurrentPrincipalContext.get(request)
                .orElseThrow(ApiExceptions::authRequired);
    }
}
