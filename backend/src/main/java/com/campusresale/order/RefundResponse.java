package com.campusresale.order;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

public record RefundResponse(
        long id,
        String refundNo,
        long orderId,
        Long paymentOrderId,
        long requestedByUserId,
        String requesterNickname,
        String amount,
        String refundType,
        String reason,
        String status,
        String statusBeforeRefund,
        Long decisionByAdminId,
        String decisionNote,
        Instant reviewedAt,
        Instant processedAt,
        String providerRefundNo,
        String failureReason,
        List<Long> evidenceFileIds,
        Instant createdAt
) {

    public static RefundResponse from(RefundOrderRecord record) {
        return new RefundResponse(
                record.id(),
                record.refundNo(),
                record.orderId(),
                record.paymentOrderId(),
                record.requestedByUserId(),
                record.requesterNickname(),
                record.amount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                record.refundType(),
                record.reason(),
                record.status().name(),
                record.statusBeforeRefund() == null ? null : record.statusBeforeRefund().name(),
                record.decisionByAdminId(),
                record.decisionNote(),
                record.reviewedAt(),
                record.processedAt(),
                record.providerRefundNo(),
                record.failureReason(),
                record.evidenceFileIds(),
                record.createdAt()
        );
    }
}
