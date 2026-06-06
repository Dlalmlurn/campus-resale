import { apiRequest } from "./client";

export interface GovernanceUser {
  id: number;
  nickname: string;
}

export interface ReportItem {
  id: number;
  reporter: GovernanceUser;
  targetType: string;
  targetId: number;
  reasonType: string;
  description: string;
  status: string;
  priority: string;
  handledByAdminId?: number | null;
  handledAt?: string | null;
  handlingNote?: string | null;
  evidenceFileIds: number[];
  createdAt: string;
}

export interface AppealItem {
  id: number;
  reportId: number;
  appellant: GovernanceUser;
  description: string;
  status: string;
  reviewedByAdminId?: number | null;
  reviewedAt?: string | null;
  reviewNote?: string | null;
  evidenceFileIds: number[];
  createdAt: string;
}

export interface RefundItem {
  id: number;
  refundNo: string;
  orderId: number;
  paymentOrderId?: number | null;
  requester: GovernanceUser;
  amount: string;
  refundType: string;
  reason: string;
  status: string;
  decisionByAdminId?: number | null;
  decisionNote?: string | null;
  processedAt?: string | null;
  createdAt: string;
}

export interface FavoriteItem {
  id: number;
  goodsId: number;
  goodsTitle: string;
  goodsPrice: string;
  seller: GovernanceUser;
  createdAt: string;
}

export interface FollowItem {
  id: number;
  followedUser: GovernanceUser;
  createdAt: string;
}

export interface PenaltyItem {
  id: number;
  user: GovernanceUser;
  reportId?: number | null;
  appealId?: number | null;
  penaltyType: string;
  reason: string;
  status: string;
  createdByAdminId: number;
  liftedByAdminId?: number | null;
  liftedAt?: string | null;
  createdAt: string;
}

export interface CreditRecordItem {
  id: number;
  sourceType: string;
  sourceId?: number | null;
  reason: string;
  internalDeltaValue: number;
  publicLabel: string;
  createdAt: string;
}

export interface CreditSummary {
  userId: number;
  fulfillmentCount: number;
  onTimeMeetupCount: number;
  positiveReviewCount: number;
  negativeEventCount: number;
  publicTags: string[];
  internalScore: number;
  internalLevel: string;
  recentRecords: CreditRecordItem[];
  updatedAt: string;
}

export interface AdminQueue {
  pendingReports: ReportItem[];
  pendingAppeals: AppealItem[];
  pendingRefunds: RefundItem[];
  activePenalties: PenaltyItem[];
}

export interface GovernanceOverview {
  reports: ReportItem[];
  appeals: AppealItem[];
  refunds: RefundItem[];
  favorites: FavoriteItem[];
  follows: FollowItem[];
  credit: CreditSummary;
  adminQueue?: AdminQueue | null;
}

export function getGovernanceOverview() {
  return apiRequest<GovernanceOverview>("/api/n3/governance-overview");
}

export function submitReport(body: {
  targetType: string;
  targetId: number;
  reasonType: string;
  description: string;
  evidenceFileIds: number[];
}) {
  return apiRequest<ReportItem>("/api/n3/reports", {
    method: "POST",
    body: JSON.stringify(body)
  });
}

export function submitAppeal(body: {
  reportId: number;
  description: string;
  evidenceFileIds: number[];
}) {
  return apiRequest<AppealItem>("/api/n3/appeals", {
    method: "POST",
    body: JSON.stringify(body)
  });
}

export function createRefund(body: {
  orderId: number;
  refundType: string;
  amount: string;
  reason: string;
}) {
  return apiRequest<RefundItem>("/api/n3/refunds", {
    method: "POST",
    body: JSON.stringify(body)
  });
}

export function addFavorite(goodsId: number) {
  return apiRequest<{ active: boolean }>(`/api/n3/favorites/${goodsId}`, { method: "POST" });
}

export function removeFavorite(goodsId: number) {
  return apiRequest<{ active: boolean }>(`/api/n3/favorites/${goodsId}`, { method: "DELETE" });
}

export function followUser(userId: number) {
  return apiRequest<{ active: boolean }>(`/api/n3/follows/${userId}`, { method: "POST" });
}

export function unfollowUser(userId: number) {
  return apiRequest<{ active: boolean }>(`/api/n3/follows/${userId}`, { method: "DELETE" });
}

export function handleReport(id: number, body: {
  status: string;
  handlingNote: string;
  penaltyUserId?: number | null;
  penaltyType?: string | null;
}) {
  return apiRequest<ReportItem>(`/api/admin/n3/reports/${id}/handle`, {
    method: "POST",
    body: JSON.stringify(body)
  });
}

export function reviewAppeal(id: number, body: { status: string; reviewNote: string }) {
  return apiRequest<AppealItem>(`/api/admin/n3/appeals/${id}/review`, {
    method: "POST",
    body: JSON.stringify(body)
  });
}

export function decideRefund(id: number, body: { status: string; decisionNote: string }) {
  return apiRequest<RefundItem>(`/api/admin/n3/refunds/${id}/decide`, {
    method: "POST",
    body: JSON.stringify(body)
  });
}

export function liftPenalty(id: number, reason: string) {
  return apiRequest<PenaltyItem>(`/api/admin/n3/penalties/${id}/lift`, {
    method: "POST",
    body: JSON.stringify({ reason })
  });
}
