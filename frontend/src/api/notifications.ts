import { apiRequest, queryString } from "./client";
import type { PageResponse } from "./types";

export interface NotificationItem {
  id: number;
  type: string;
  title: string;
  content: string;
  relatedType: string | null;
  relatedId: number | null;
  read: boolean;
  readAt: string | null;
  createdAt: string;
}

export function getNotifications(filters: {
  unreadOnly?: boolean;
  page?: number;
  pageSize?: number;
} = {}) {
  return apiRequest<PageResponse<NotificationItem>>(`/api/notifications${queryString({
    unreadOnly: String(filters.unreadOnly ?? false),
    page: filters.page ?? 1,
    pageSize: filters.pageSize ?? 20
  })}`);
}

export function getUnreadNotificationCount() {
  return apiRequest<{ unreadCount: number }>("/api/notifications/unread-count");
}

export function markAllNotificationsRead() {
  return apiRequest<{ updatedCount: number }>("/api/notifications/read-all", { method: "POST" });
}
