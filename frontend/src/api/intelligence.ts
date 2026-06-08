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
  // 建议来源：LLM 表示由大模型生成，RULES 表示退回规则引擎。
  assistSource: string;
}

export function assistGoods(body: { title: string; description: string; price: string }) {
  return apiRequest<GoodsAssistResponse>("/api/intelligence/goods-assist", {
    method: "POST",
    body: JSON.stringify(body)
  });
}
