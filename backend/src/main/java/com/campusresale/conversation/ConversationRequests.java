package com.campusresale.conversation;

public final class ConversationRequests {

    private ConversationRequests() {
    }

    public record CreateConversationRequest(Long goodsId) {
    }

    public record SendMessageRequest(String textContent) {
    }

    public record CreateBargainRequest(String amount, String note) {
    }
}
