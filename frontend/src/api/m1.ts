// 文件功能：封装 M1 身份、商品目录、校园认证、文件上传和后台审核相关 API。
import { apiRequest, queryString } from "./client";
import type {
  CampusPlaceSummary,
  CampusVerification,
  CategorySummary,
  CurrentUser,
  GoodsSummary,
  GoodsUpsertRequest,
  PageResponse,
  StoredFileSummary,
  TagSummary
} from "./types";

export function getCurrentUser() {
  return apiRequest<CurrentUser>("/api/auth/me");
}

export function login(username: string, password: string) {
  return apiRequest<CurrentUser>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password })
  });
}

export function register(username: string, password: string, nickname: string, personalEmail: string) {
  return apiRequest<CurrentUser>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify({ username, password, nickname, personalEmail: personalEmail || null })
  });
}

export function logout() {
  return apiRequest<{ success: boolean }>("/api/auth/logout", { method: "POST" });
}

// 商品发布与筛选依赖分类、标签和校区地点，页面启动时一次性并发加载。
export async function getCatalog() {
  const [categories, tags, places] = await Promise.all([
    apiRequest<CategorySummary[]>("/api/categories"),
    apiRequest<TagSummary[]>("/api/tags"),
    apiRequest<CampusPlaceSummary[]>("/api/campus-places")
  ]);
  return { categories, tags, places };
}

export function getPublicGoods(filters: Record<string, string | number | null | undefined> = {}) {
  return apiRequest<PageResponse<GoodsSummary>>(`/api/goods${queryString(filters)}`);
}

export function getGoodsDetail(id: number) {
  return apiRequest<GoodsSummary>(`/api/goods/${id}`);
}

export function getMyGoods(filters: Record<string, string | number | null | undefined> = {}) {
  return apiRequest<PageResponse<GoodsSummary>>(`/api/goods/mine${queryString(filters)}`);
}

export function createGoodsDraft(request: GoodsUpsertRequest) {
  return apiRequest<GoodsSummary>("/api/goods/drafts", {
    method: "POST",
    body: JSON.stringify(request)
  });
}

export function submitGoods(id: number) {
  return apiRequest<GoodsSummary>(`/api/goods/${id}/submit`, { method: "POST" });
}

export function getVerification() {
  return apiRequest<CampusVerification>("/api/verifications/me");
}

// 保存本人校园认证草稿；材料文件 id 必须先通过 uploadFile 上传为 CAMPUS_AUTH_MATERIAL。
export function updateVerification(request: {
  realName: string;
  studentNo: string;
  department: string;
  campusEmail: string;
  documentType: string;
  documentFileIds: number[];
}) {
  return apiRequest<CampusVerification>("/api/verifications/me", {
    method: "PUT",
    body: JSON.stringify(request)
  });
}

export function submitVerification() {
  return apiRequest<CampusVerification>("/api/verifications/me/submit", { method: "POST" });
}

export function uploadAvatar(file: File) {
  const body = new FormData();
  body.set("file", file);
  return apiRequest<CurrentUser>("/api/auth/me/avatar", { method: "POST", body });
}

export function changePassword(currentPassword: string, newPassword: string) {
  return apiRequest<CurrentUser>("/api/auth/me/password", {
    method: "POST",
    body: JSON.stringify({ currentPassword, newPassword })
  });
}

export function uploadFile(file: File, fileKind: "AVATAR" | "GOODS_IMAGE" | "CAMPUS_AUTH_MATERIAL" | "ORDER_EVIDENCE" | "REPORT_EVIDENCE" | "APPEAL_EVIDENCE" | "MESSAGE_IMAGE") {
  // FormData 由浏览器自动补充 multipart boundary，apiRequest 不会强行设置 JSON Content-Type。
  const body = new FormData();
  body.set("file", file);
  body.set("fileKind", fileKind);
  return apiRequest<StoredFileSummary>("/api/files", { method: "POST", body });
}

export function getAdminVerifications(status = "") {
  return apiRequest<PageResponse<CampusVerification>>(`/api/admin/verifications${queryString({ status })}`);
}

// 管理员审核校园认证；后端会同步证件材料审核状态、操作日志和可交易学生角色。
export function reviewVerification(id: number, action: "approve" | "reject", reason: string) {
  return apiRequest<CampusVerification>(`/api/admin/verifications/${id}/${action}`, {
    method: "POST",
    body: JSON.stringify({ reason })
  });
}

export function getAdminGoods(status = "", auditStatus = "") {
  return apiRequest<PageResponse<GoodsSummary>>(`/api/admin/goods${queryString({ status, auditStatus })}`);
}

export function reviewGoods(id: number, action: "approve" | "reject", reason: string) {
  return apiRequest<GoodsSummary>(`/api/admin/goods/${id}/${action}`, {
    method: "POST",
    body: JSON.stringify({ reason })
  });
}
