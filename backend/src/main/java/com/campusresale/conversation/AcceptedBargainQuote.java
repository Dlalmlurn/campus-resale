package com.campusresale.conversation;

import java.math.BigDecimal;

public record AcceptedBargainQuote(
        long conversationId,
        long cardId,
        BigDecimal amount
) {
}
