package com.campusresale.order;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentProviderCallback(
        String provider,
        String callbackNo,
        String transactionNo,
        long paymentOrderId,
        BigDecimal amount,
        String status,
        Instant occurredAt,
        String payloadSummary
) {
}
