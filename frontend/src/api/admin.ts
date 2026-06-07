/**
 * N2 管理员后台 API：统计看板、操作日志、敏感访问日志。
 * 全部复用 apiRequest / queryString，不引入 axios 或其他额外依赖。
 */
import { apiRequest, queryString } from "./client";
import type { PageResponse, PaymentSummary, RefundSummary, SettlementSummary } from "./types";

// ─────────────────────────────────────────────
// 类型定义
// ─────────────────────────────────────────────

export interface OrderStats {
  totalOrders: number;
  pendingSellerConfirm: number;
  pendingPayment: number;
  paidPendingMeetup: number;
  completedPendingSettlement: number;
  completed: number;
  cancelled: number;
  closed: number;
  disputeProcessing: number;
  refundProcessing: number;
  activeFrozenAmount: string;
  completedAmount: string;
}

export interface PaymentStats {
  totalPayments: number;
  pending: number;
  processing: number;
  escrowed: number;
  failed: number;
  closed: number;
  escrowedAmount: string;
  totalProcessedAmount: string;
}

export interface SettlementStats {
  totalSettlements: number;
  pending: number;
  processing: number;
  settled: number;
  failed: number;
  closed: number;
  totalSettledAmount: string;
  pendingSettlementAmount: string;
}

export interface GoodsStats {
  totalGoods: number;
  draft: number;
  pendingReview: number;
  onSale: number;
  reserved: number;
  sold: number;
  offShelf: number;
  deleted: number;
  auditPending: number;
}

export interface ReviewStats {
  totalReviews: number;
  submitted: number;
  visible: number;
  hidden: number;
  excluded: number;
  avgRating: number | null;
  fiveStar: number;
  fourStar: number;
  threeStar: number;
  lowRating: number;
}

export interface UserStats {
  totalUsers: number;
  activeUsers: number;
  lockedUsers: number;
  disabledUsers: number;
  newThisMonth: number;
  newToday: number;
}

export interface CampusAuthStats {
  totalVerifications: number;
  draft: number;
  accumulating: number;
  pendingReview: number;
  approved: number;
  rejected: number;
  invalid: number;
}

export interface OperationLogStats {
  totalLogs: number;
  successCount: number;
  failureCount: number;
  partialCount: number;
  todayCount: number;
  thisMonthCount: number;
}

export interface DashboardStats {
  orders: OrderStats;
  payments: PaymentStats;
  settlements: SettlementStats;
  goods: GoodsStats;
  reviews: ReviewStats;
  users: UserStats;
  campusAuths: CampusAuthStats;
  operationLogs: OperationLogStats;
}

export interface OrderDailyTrendItem {
  statDate: string;      // 格式："2026-06-05"
  totalCreated: number;
  completedCount: number;
  cancelledCount: number;
}

export interface OperationLogItem {
  id: number;
  adminId: number | null;
  action: string;
  targetType: string;
  targetId: number | null;
  ipAddress: string | null;
  userAgent: string | null;
  requestPath: string | null;
  httpMethod: string | null;
  result: string;
  operatorType: string;
  createdAt: string;
}

export interface SensitiveAccessLogItem {
  id: number;
  adminId: number | null;
  targetType: string;
  targetId: number;
  reason: string;
  result: string;
  ipAddress: string | null;
  createdAt: string;
}

// ─────────────────────────────────────────────
// API 请求函数
// ─────────────────────────────────────────────

/** 获取后台统计看板聚合数据（8 个维度） */
export function getAdminDashboard(): Promise<DashboardStats> {
  return apiRequest<DashboardStats>("/api/admin/stats/dashboard");
}

/** 获取近 30 天每日订单趋势 */
export function getOrderTrend(): Promise<OrderDailyTrendItem[]> {
  return apiRequest<OrderDailyTrendItem[]>("/api/admin/stats/order-trend");
}

export interface OperationLogFilter {
  action?: string;
  result?: string;
  startTime?: string;
  endTime?: string;
  page?: number;
  pageSize?: number;
}

/** 分页查询操作日志 */
export function getOperationLogs(filter: OperationLogFilter = {}): Promise<PageResponse<OperationLogItem>> {
  const qs = queryString({
    action: filter.action,
    result: filter.result,
    startTime: filter.startTime,
    endTime: filter.endTime,
    page: filter.page ?? 1,
    pageSize: filter.pageSize ?? 20
  });
  return apiRequest<PageResponse<OperationLogItem>>(`/api/admin/operation-logs${qs}`);
}

export interface SensitiveAccessLogFilter {
  targetType?: string;
  adminId?: number;
  startTime?: string;
  endTime?: string;
  page?: number;
  pageSize?: number;
}

/** 分页查询敏感访问日志 */
export function getSensitiveAccessLogs(filter: SensitiveAccessLogFilter = {}): Promise<PageResponse<SensitiveAccessLogItem>> {
  const qs = queryString({
    targetType: filter.targetType,
    adminId: filter.adminId,
    startTime: filter.startTime,
    endTime: filter.endTime,
    page: filter.page ?? 1,
    pageSize: filter.pageSize ?? 20
  });
  return apiRequest<PageResponse<SensitiveAccessLogItem>>(`/api/admin/sensitive-access-logs${qs}`);
}

export function getAdminPayments(page = 1, pageSize = 20): Promise<PageResponse<PaymentSummary>> {
  return apiRequest<PageResponse<PaymentSummary>>(`/api/admin/payments${queryString({ page, pageSize })}`);
}

export function getAdminRefunds(page = 1, pageSize = 20): Promise<PageResponse<RefundSummary>> {
  return apiRequest<PageResponse<RefundSummary>>(`/api/admin/refunds${queryString({ page, pageSize })}`);
}

export function decideAdminRefund(id: number, decision: string, decisionNote: string): Promise<RefundSummary> {
  return apiRequest<RefundSummary>(`/api/admin/refunds/${id}/decide`, {
    method: "POST",
    body: JSON.stringify({ decision, decisionNote })
  });
}

export function getAdminSettlements(page = 1, pageSize = 20): Promise<PageResponse<SettlementSummary>> {
  return apiRequest<PageResponse<SettlementSummary>>(`/api/admin/settlements${queryString({ page, pageSize })}`);
}

export function advanceSettlement(id: number): Promise<SettlementSummary> {
  return apiRequest<SettlementSummary>(`/api/admin/settlements/${id}/advance`, { method: "POST" });
}

export function advanceDueSettlements(): Promise<SettlementSummary[]> {
  return apiRequest<SettlementSummary[]>("/api/admin/settlements/advance-due", { method: "POST" });
}
