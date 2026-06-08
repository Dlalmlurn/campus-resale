package com.campusresale.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RefundOrderRecord(
        long id,
        String refundNo,
        long orderId,
        Long paymentOrderId,
        long requestedByUserId,
        String requesterNickname,
        BigDecimal amount,
        String refundType,
        String reason,
        RefundOrderStatus status,
        TradeOrderStatus statusBeforeRefund,
        Long decisionByAdminId,
        String decisionNote,
        Instant reviewedAt,
        Instant processedAt,
        String providerRefundNo,
        String failureReason,
        List<Long> evidenceFileIds,
        Instant createdAt
) {
}
