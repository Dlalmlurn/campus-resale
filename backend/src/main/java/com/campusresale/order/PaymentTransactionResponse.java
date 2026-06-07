package com.campusresale.order;

import java.math.RoundingMode;
import java.time.Instant;

public record PaymentTransactionResponse(
        long id,
        long paymentOrderId,
        String transactionNo,
        String amount,
        String status,
        String provider,
        Instant occurredAt
) {

    public static PaymentTransactionResponse from(PaymentTransactionRecord record) {
        return new PaymentTransactionResponse(
                record.id(),
                record.paymentOrderId(),
                record.transactionNo(),
                record.amount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                record.status(),
                record.provider(),
                record.occurredAt()
        );
    }
}
