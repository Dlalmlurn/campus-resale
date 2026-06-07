import { apiRequest, queryString } from "./client";
import type {
  CompletionRequestSummary,
  CreateOrderRequest,
  OrderSummary,
  PageResponse,
  PaymentSummary,
  PaymentTransactionSummary,
  RefundSummary,
  ReviewSummary,
  SettlementSummary
} from "./types";

export function createOrder(request: CreateOrderRequest) {
  return apiRequest<OrderSummary>("/api/orders", {
    method: "POST",
    body: JSON.stringify(request)
  });
}

export function getOrders(filters: { status?: string; page?: number; pageSize?: number } = {}) {
  return apiRequest<PageResponse<OrderSummary>>(`/api/orders${queryString({ page: 1, pageSize: 20, ...filters })}`);
}

export function getOrderDetail(id: number) {
  return apiRequest<OrderSummary>(`/api/orders/${id}`);
}

export function sellerConfirmOrder(id: number) {
  return apiRequest<OrderSummary>(`/api/orders/${id}/seller-confirm`, { method: "POST" });
}

export function sellerRejectOrder(id: number, reason: string) {
  return apiRequest<OrderSummary>(`/api/orders/${id}/seller-reject`, {
    method: "POST",
    body: JSON.stringify({ reason })
  });
}

export function buyerCancelOrder(id: number, reason: string) {
  return apiRequest<OrderSummary>(`/api/orders/${id}/buyer-cancel`, {
    method: "POST",
    body: JSON.stringify({ reason })
  });
}

export function simulatePayment(id: number) {
  return apiRequest<PaymentSummary>(`/api/orders/${id}/payments/simulate`, { method: "POST" });
}

export function getPaymentStatus(id: number) {
  return apiRequest<PaymentSummary>(`/api/orders/${id}/payment`);
}

export function getPaymentTransactions(id: number) {
  return apiRequest<PaymentTransactionSummary[]>(`/api/orders/${id}/payment/transactions`);
}

export function getSettlementStatus(id: number) {
  return apiRequest<SettlementSummary>(`/api/orders/${id}/settlement`);
}

export function getOrderRefunds(id: number) {
  return apiRequest<RefundSummary[]>(`/api/orders/${id}/refunds`);
}

export function createRefund(id: number, request: { refundType: string; amount: string; reason: string; evidenceFileIds: number[] }) {
  return apiRequest<RefundSummary>(`/api/orders/${id}/refunds`, {
    method: "POST",
    body: JSON.stringify(request)
  });
}

export function requestCompletion(id: number) {
  return apiRequest<CompletionRequestSummary>(`/api/orders/${id}/completion-requests`, { method: "POST" });
}

export function confirmCompletion(id: number, requestId: number) {
  return apiRequest<OrderSummary>(`/api/orders/${id}/completion-requests/${requestId}/confirm`, { method: "POST" });
}

export function getOrderReviews(id: number) {
  return apiRequest<ReviewSummary[]>(`/api/orders/${id}/reviews`);
}

export function createReview(id: number, rating: number, content: string) {
  return apiRequest<ReviewSummary>(`/api/orders/${id}/reviews`, {
    method: "POST",
    body: JSON.stringify({ rating, content })
  });
}
