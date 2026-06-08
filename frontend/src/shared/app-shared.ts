// 文件功能：App 各页面共享的路由类型、目录类型、文案映射、纯函数工具与本地草稿/浏览记录存储。
// 这些原本集中在 App.tsx，拆分页面后抽到此处复用，避免在多个 pages/*.tsx 里重复定义。
import type { CampusPlaceSummary, CategorySummary, CurrentUser, GoodsSummary, GoodsUpsertRequest, TagSummary } from "../api/types";

export type Route =
  | { name: "market" }
  | { name: "goods"; id: number }
  | { name: "conversations" }
  | { name: "conversation"; id: number }
  | { name: "orders" }
  | { name: "order"; id: number }
  | { name: "profile" }
  | { name: "notifications" }
  | { name: "auth" }
  | { name: "verification" }
  | { name: "seller" }
  | { name: "governance" }
  | { name: "admin" };

export type Catalog = {
  categories: CategorySummary[];
  tags: TagSummary[];
  places: CampusPlaceSummary[];
};

export const emptyCatalog: Catalog = { categories: [], tags: [], places: [] };

export type Notify = (tone: "success" | "error", text: string) => void;

export type ViewedGoods = {
  id: number;
  title: string;
  listPrice: string;
  viewedAt: string;
};

export const conditionLabels: Record<string, string> = {
  NEW: "全新",
  LIKE_NEW: "几乎全新",
  LIGHTLY_USED: "轻度使用",
  NOTICEABLY_USED: "明显使用"
};

export const goodsStatusLabels: Record<string, string> = {
  DRAFT: "草稿",
  PENDING_REVIEW: "待审核",
  ON_SALE: "在售",
  RESERVED: "已预订",
  SOLD: "已售",
  OFF_SHELF: "已下架",
  DELETED: "已删除"
};

export const auditStatusLabels: Record<string, string> = {
  NOT_SUBMITTED: "未提交",
  PENDING: "待审核",
  APPROVED: "已通过",
  REJECTED: "已驳回"
};

export const verificationStatusLabels: Record<string, string> = {
  NONE: "未认证",
  DRAFT: "草稿",
  ACCUMULATING: "资料积累中",
  PENDING_REVIEW: "待审核",
  APPROVED: "已通过",
  REJECTED: "已驳回",
  INVALID: "已失效"
};

export const marketSortLabels: Record<string, string> = {
  RECOMMENDED: "推荐优先",
  LATEST: "最新发布",
  PRICE_ASC: "价格从低到高",
  PRICE_DESC: "价格从高到低"
};

export const sellerStatusFilters: Array<{ value: string; label: string }> = [
  { value: "", label: "全部" },
  { value: "DRAFT", label: "草稿" },
  { value: "PENDING_REVIEW", label: "待审核" },
  { value: "ON_SALE", label: "在售" },
  { value: "RESERVED", label: "已预订" },
  { value: "SOLD", label: "已售" },
  { value: "OFF_SHELF", label: "已下架" }
];

const sellerDraftStorageKey = "campus-resale:seller-draft";
const viewedGoodsStorageKey = "campus-resale:viewed-goods";

export function parseRoute(): Route {
  const value = window.location.hash.replace(/^#\/?/, "") || "market";
  if (value.startsWith("goods/")) return { name: "goods", id: Number(value.split("/")[1]) };
  if (value.startsWith("conversations/")) return { name: "conversation", id: Number(value.split("/")[1]) };
  if (value.startsWith("orders/")) return { name: "order", id: Number(value.split("/")[1]) };
  if (["market", "conversations", "orders", "profile", "notifications", "auth", "verification", "seller", "governance", "admin"].includes(value)) return { name: value as Route["name"] } as Route;
  return { name: "market" };
}

export function routeHash(route: Route) {
  if (route.name === "goods") return `#/goods/${route.id}`;
  if (route.name === "conversation") return `#/conversations/${route.id}`;
  if (route.name === "order") return `#/orders/${route.id}`;
  return `#/${route.name}`;
}

export function isAdmin(user: CurrentUser | null) {
  return Boolean(user?.roles.some((role) => ["CONTENT_ADMIN", "SUPER_ADMIN"].includes(role)));
}

export function isSuperAdmin(user: CurrentUser | null) {
  return Boolean(user?.roles.includes("SUPER_ADMIN"));
}

export function messageOf(error: unknown) {
  return error instanceof Error ? error.message : "请求失败，请稍后重试";
}

export function factorLabel(value: string) {
  return ({ NAME_STUDENT_NO: "姓名与学号", DEPARTMENT: "院系信息", CAMPUS_EMAIL: "校园邮箱", STUDENT_CARD: "学生证", CAMPUS_CARD: "校园卡" } as Record<string, string>)[value] ?? value;
}

export function notificationTypeLabel(value: string) {
  return ({
    ORDER_CREATED: "订单创建",
    ORDER_SELLER_CONFIRMED: "卖家确认",
    PAYMENT_ESCROWED: "支付托管",
    COMPLETION_REQUESTED: "完成确认",
    ORDER_COMPLETED: "订单完成",
    REVIEW_SUBMITTED: "评价更新",
    SETTLEMENT_STATUS_CHANGED: "结算状态",
    MESSAGE_RECEIVED: "私信消息",
    BARGAIN_OFFERED: "收到议价",
    BARGAIN_ACCEPTED: "议价接受",
    BARGAIN_REJECTED: "议价拒绝",
    AI_REVIEW_REMINDER: "AI 风险提醒"
  } as Record<string, string>)[value] ?? value;
}

export function formatDate(value?: string | null) {
  return value ? new Date(value).toLocaleString("zh-CN") : "尚未发布";
}

export function formatBytes(value: number) {
  return value < 1024 * 1024 ? `${Math.ceil(value / 1024)} KB` : `${(value / 1024 / 1024).toFixed(1)} MB`;
}

export function emptyGoodsDraft(): GoodsUpsertRequest {
  return {
    title: "",
    description: "",
    categoryId: null,
    conditionLevel: "LIKE_NEW",
    listPrice: "",
    tradePlaceId: null,
    tradePlaceDetail: "",
    availableTimeText: "",
    imageFileIds: [],
    tagIds: []
  };
}

export function loadSellerDraft(): GoodsUpsertRequest {
  try {
    const raw = window.localStorage.getItem(sellerDraftStorageKey);
    return raw ? { ...emptyGoodsDraft(), ...JSON.parse(raw) } : emptyGoodsDraft();
  } catch {
    return emptyGoodsDraft();
  }
}

export function saveSellerDraft(form: GoodsUpsertRequest) {
  try {
    window.localStorage.setItem(sellerDraftStorageKey, JSON.stringify(form));
  } catch {
    // 浏览器隐私模式或存储配额异常时，表单仍可正常提交。
  }
}

export function clearSellerDraft() {
  try {
    window.localStorage.removeItem(sellerDraftStorageKey);
  } catch {
    // 清理失败不影响草稿创建结果。
  }
}

export function loadViewedGoods(): ViewedGoods[] {
  try {
    const raw = window.localStorage.getItem(viewedGoodsStorageKey);
    return raw ? JSON.parse(raw) as ViewedGoods[] : [];
  } catch {
    return [];
  }
}

export function recordViewedGoods(item: GoodsSummary) {
  try {
    const current = loadViewedGoods().filter((entry) => entry.id !== item.id);
    const next = [{ id: item.id, title: item.title, listPrice: item.listPrice, viewedAt: new Date().toISOString() }, ...current].slice(0, 20);
    window.localStorage.setItem(viewedGoodsStorageKey, JSON.stringify(next));
  } catch {
    // 最近浏览只是体验增强，存储失败不影响商品详情查看。
  }
}
