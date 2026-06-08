package com.campusresale.order;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentTransactionRecord(
        long id,
        long paymentOrderId,
        String transactionNo,
        BigDecimal amount,
        String status,
        String provider,
        Instant occurredAt
) {
}
