// 文件功能：超级管理员账号管理 API（分页查询、状态调整、角色授予/撤销）。
// 对应后端 AdminUserController（/api/admin/users，类级 @RequireRole("SUPER_ADMIN")）。
import { apiRequest, queryString } from "./client";
import type { PageResponse } from "./types";

export interface AdminUser {
  id: number;
  username: string;
  nickname: string;
  personalEmail: string | null;
  accountStatus: string;
  disabledAt: string | null;
  createdAt: string;
  updatedAt: string;
  roles: string[];
}

export function getAdminUsers(params: {
  keyword?: string;
  accountStatus?: string;
  roleCode?: string;
  page?: number;
  pageSize?: number;
} = {}) {
  return apiRequest<PageResponse<AdminUser>>(`/api/admin/users${queryString(params)}`);
}

export function updateAdminUserStatus(id: number, accountStatus: string, reason?: string) {
  return apiRequest<AdminUser>(`/api/admin/users/${id}/status`, {
    method: "POST",
    body: JSON.stringify({ accountStatus, reason })
  });
}

export function assignAdminUserRole(id: number, roleCode: string, reason?: string) {
  return apiRequest<AdminUser>(`/api/admin/users/${id}/roles`, {
    method: "POST",
    body: JSON.stringify({ roleCode, reason })
  });
}

export function removeAdminUserRole(id: number, roleCode: string, reason?: string) {
  return apiRequest<AdminUser>(`/api/admin/users/${id}/roles/${roleCode}${queryString({ reason })}`, {
    method: "DELETE"
  });
}
