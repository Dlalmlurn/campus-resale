import { apiRequest } from "./client";

export interface GoodsAssistResponse {
  requestId: number;
  optimizedTitle: string;
  optimizedDescription: string;
  suggestedCategoryCode: string;
  suggestedTags: string[];
  riskLevel: string;
  riskReasons: string[];
  recommendationReason: string;
  auditReminder: string;
}

export function assistGoods(body: { title: string; description: string; price: string }) {
  return apiRequest<GoodsAssistResponse>("/api/intelligence/goods-assist", {
    method: "POST",
    body: JSON.stringify(body)
  });
}
