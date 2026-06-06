package com.campusresale.governance;

import java.time.Instant;
import java.util.List;

public final class N3Responses {

    private N3Responses() {
    }

    public record UserSummary(long id, String nickname) {
    }

    public record ReportResponse(
            long id,
            UserSummary reporter,
            String targetType,
            long targetId,
            String reasonType,
            String description,
            String status,
            String priority,
            Long handledByAdminId,
            Instant handledAt,
            String handlingNote,
            List<Long> evidenceFileIds,
            Instant createdAt
    ) {
    }

    public record AppealResponse(
            long id,
            long reportId,
            UserSummary appellant,
            String description,
            String status,
            Long reviewedByAdminId,
            Instant reviewedAt,
            String reviewNote,
            List<Long> evidenceFileIds,
            Instant createdAt
    ) {
    }

    public record RefundResponse(
            long id,
            String refundNo,
            long orderId,
            Long paymentOrderId,
            UserSummary requester,
            String amount,
            String refundType,
            String reason,
            String status,
            Long decisionByAdminId,
            String decisionNote,
            Instant processedAt,
            Instant createdAt
    ) {
    }

    public record FavoriteResponse(
            long id,
            long goodsId,
            String goodsTitle,
            String goodsPrice,
            UserSummary seller,
            Instant createdAt
    ) {
    }

    public record FollowResponse(
            long id,
            UserSummary followedUser,
            Instant createdAt
    ) {
    }

    public record PenaltyResponse(
            long id,
            UserSummary user,
            Long reportId,
            Long appealId,
            String penaltyType,
            String reason,
            String status,
            long createdByAdminId,
            Long liftedByAdminId,
            Instant liftedAt,
            Instant createdAt
    ) {
    }

    public record CreditRecordResponse(
            long id,
            String sourceType,
            Long sourceId,
            String reason,
            int internalDeltaValue,
            String publicLabel,
            Instant createdAt
    ) {
    }

    public record CreditSummaryResponse(
            long userId,
            int fulfillmentCount,
            int onTimeMeetupCount,
            int positiveReviewCount,
            int negativeEventCount,
            List<String> publicTags,
            int internalScore,
            String internalLevel,
            List<CreditRecordResponse> recentRecords,
            Instant updatedAt
    ) {
    }

    public record AdminQueueResponse(
            List<ReportResponse> pendingReports,
            List<AppealResponse> pendingAppeals,
            List<RefundResponse> pendingRefunds,
            List<PenaltyResponse> activePenalties
    ) {
    }

    public record GovernanceOverviewResponse(
            List<ReportResponse> reports,
            List<AppealResponse> appeals,
            List<RefundResponse> refunds,
            List<FavoriteResponse> favorites,
            List<FollowResponse> follows,
            CreditSummaryResponse credit,
            AdminQueueResponse adminQueue
    ) {
    }

    public record ToggleResponse(boolean active) {
    }
}
