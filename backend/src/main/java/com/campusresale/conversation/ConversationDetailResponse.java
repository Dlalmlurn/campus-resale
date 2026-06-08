package com.campusresale.conversation;

import java.util.List;

public record ConversationDetailResponse(
        ConversationSummary conversation,
        List<MessageResponse> messages,
        List<BargainCardResponse> bargainCards
) {
}
