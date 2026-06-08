package com.campusresale.conversation;

import com.campusresale.conversation.ConversationRequests.CreateBargainRequest;
import com.campusresale.conversation.ConversationRequests.CreateConversationRequest;
import com.campusresale.conversation.ConversationRequests.SendMessageRequest;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.CurrentPrincipalContext;
import com.campusresale.platform.security.RequireLogin;
import com.campusresale.platform.security.RequireTradeEligible;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @RequireTradeEligible
    @PostMapping
    public ConversationDetailResponse create(
            @RequestBody CreateConversationRequest request,
            HttpServletRequest servletRequest
    ) {
        return conversationService.createOrGet(request, principal(servletRequest));
    }

    @RequireLogin
    @GetMapping
    public List<ConversationSummary> list(HttpServletRequest servletRequest) {
        return conversationService.list(principal(servletRequest));
    }

    @RequireLogin
    @GetMapping("/{id}")
    public ConversationDetailResponse detail(@PathVariable long id, HttpServletRequest servletRequest) {
        return conversationService.detail(id, principal(servletRequest));
    }

    @RequireLogin
    @GetMapping("/{id}/messages")
    public List<MessageResponse> messagesAfter(
            @PathVariable long id,
            @RequestParam(defaultValue = "0") long afterId,
            HttpServletRequest servletRequest
    ) {
        return conversationService.messagesAfter(id, afterId, principal(servletRequest));
    }

    @RequireTradeEligible
    @PostMapping("/{id}/messages")
    public MessageResponse sendMessage(
            @PathVariable long id,
            @RequestBody SendMessageRequest request,
            HttpServletRequest servletRequest
    ) {
        return conversationService.sendText(id, request, principal(servletRequest));
    }

    @RequireTradeEligible
    @PostMapping("/{id}/image-messages")
    public MessageResponse sendImageMessage(
            @PathVariable long id,
            @RequestBody SendMessageRequest request,
            HttpServletRequest servletRequest
    ) {
        return conversationService.sendImage(id, request, principal(servletRequest));
    }

    @RequireTradeEligible
    @PostMapping("/{id}/bargain-cards")
    public BargainCardResponse createBargain(
            @PathVariable long id,
            @RequestBody CreateBargainRequest request,
            HttpServletRequest servletRequest
    ) {
        return conversationService.createBargain(id, request, principal(servletRequest));
    }

    @RequireTradeEligible
    @PostMapping("/{id}/bargain-cards/{cardId}/accept")
    public BargainCardResponse acceptBargain(
            @PathVariable long id,
            @PathVariable long cardId,
            HttpServletRequest servletRequest
    ) {
        return conversationService.acceptBargain(id, cardId, principal(servletRequest));
    }

    @RequireTradeEligible
    @PostMapping("/{id}/bargain-cards/{cardId}/reject")
    public BargainCardResponse rejectBargain(
            @PathVariable long id,
            @PathVariable long cardId,
            HttpServletRequest servletRequest
    ) {
        return conversationService.rejectBargain(id, cardId, principal(servletRequest));
    }

    @RequireLogin
    @PostMapping("/{id}/archive")
    public ConversationSummary archive(@PathVariable long id, HttpServletRequest servletRequest) {
        return conversationService.archive(id, principal(servletRequest));
    }

    @RequireLogin
    @PostMapping("/{id}/unarchive")
    public ConversationSummary unarchive(@PathVariable long id, HttpServletRequest servletRequest) {
        return conversationService.unarchive(id, principal(servletRequest));
    }

    @RequireLogin
    @PostMapping("/{id}/block")
    public ConversationSummary block(@PathVariable long id, HttpServletRequest servletRequest) {
        return conversationService.block(id, principal(servletRequest));
    }

    private CurrentPrincipal principal(HttpServletRequest request) {
        return CurrentPrincipalContext.get(request)
                .orElseThrow(ApiExceptions::authRequired);
    }
}
