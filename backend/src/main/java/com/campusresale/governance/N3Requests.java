package com.campusresale.governance;

import java.util.List;

public final class N3Requests {

    private N3Requests() {
    }

    public record SubmitReportRequest(
            String targetType,
            Long targetId,
            String reasonType,
            String description,
            List<Long> evidenceFileIds
    ) {
    }

    public record HandleReportRequest(
            String status,
            String handlingNote,
            Long penaltyUserId,
            String penaltyType
    ) {
    }

    public record SubmitAppealRequest(
            Long reportId,
            String description,
            List<Long> evidenceFileIds
    ) {
    }

    public record ReviewAppealRequest(
            String status,
            String reviewNote
    ) {
    }

    public record CreateRefundRequest(
            Long orderId,
            String refundType,
            String amount,
            String reason
    ) {
    }

    public record DecideRefundRequest(
            String status,
            String decisionNote
    ) {
    }

    public record ApplyPenaltyRequest(
            Long userId,
            Long reportId,
            String penaltyType,
            String reason
    ) {
    }

    public record LiftPenaltyRequest(String reason) {
    }
}
