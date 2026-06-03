package com.campusresale.order;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentOrderRecord(
        long id,
        String paymentNo,
        long orderId,
        BigDecimal amount,
        PaymentOrderStatus status,
        String provider,
        Instant createdAt,
        Instant paidAt,
        Instant closedAt
) {
}
