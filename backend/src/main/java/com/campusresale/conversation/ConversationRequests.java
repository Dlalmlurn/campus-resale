package com.campusresale.conversation;

import java.util.List;

public final class ConversationRequests {

    private ConversationRequests() {
    }

    public record CreateConversationRequest(Long goodsId) {
    }

    public record SendMessageRequest(String textContent, List<Long> attachmentFileIds) {
    }

    public record CreateBargainRequest(String amount, String note) {
    }
}
