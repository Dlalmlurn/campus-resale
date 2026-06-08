import { apiRequest } from "./client";
import type {
  BargainCardSummary,
  ConversationDetail,
  ConversationSummary,
  MessageSummary
} from "./types";

export function createConversation(goodsId: number) {
  return apiRequest<ConversationDetail>("/api/conversations", {
    method: "POST",
    body: JSON.stringify({ goodsId })
  });
}

export function getConversations() {
  return apiRequest<ConversationSummary[]>("/api/conversations");
}

export function getConversationDetail(id: number) {
  return apiRequest<ConversationDetail>(`/api/conversations/${id}`);
}

export function getConversationMessagesAfter(id: number, afterId: number) {
  return apiRequest<MessageSummary[]>(`/api/conversations/${id}/messages?afterId=${afterId}`);
}

export function sendConversationMessage(id: number, textContent: string) {
  return apiRequest<MessageSummary>(`/api/conversations/${id}/messages`, {
    method: "POST",
    body: JSON.stringify({ textContent, attachmentFileIds: [] })
  });
}

export function sendConversationImageMessage(id: number, attachmentFileIds: number[], textContent = "") {
  return apiRequest<MessageSummary>(`/api/conversations/${id}/image-messages`, {
    method: "POST",
    body: JSON.stringify({ textContent, attachmentFileIds })
  });
}

export function createBargainCard(id: number, amount: string, note: string) {
  return apiRequest<BargainCardSummary>(`/api/conversations/${id}/bargain-cards`, {
    method: "POST",
    body: JSON.stringify({ amount, note })
  });
}

export function acceptBargainCard(conversationId: number, cardId: number) {
  return apiRequest<BargainCardSummary>(`/api/conversations/${conversationId}/bargain-cards/${cardId}/accept`, {
    method: "POST"
  });
}

export function rejectBargainCard(conversationId: number, cardId: number) {
  return apiRequest<BargainCardSummary>(`/api/conversations/${conversationId}/bargain-cards/${cardId}/reject`, {
    method: "POST"
  });
}

export function archiveConversation(id: number) {
  return apiRequest<ConversationSummary>(`/api/conversations/${id}/archive`, { method: "POST" });
}

export function blockConversation(id: number) {
  return apiRequest<ConversationSummary>(`/api/conversations/${id}/block`, { method: "POST" });
}
