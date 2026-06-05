export interface PageResponse<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}

export interface CurrentUser {
  id: number;
  username: string;
  nickname: string;
  roles: string[];
  verificationStatus: string;
  canTrade: boolean;
}

export interface StoredFileSummary {
  id: number;
  originalName: string;
  contentType: string;
  byteSize: number;
  fileKind: string;
  visibilityScope: string;
  auditStatus: string;
  createdAt: string;
}

export interface CampusFactor {
  factorType: string;
  status: string;
  scoreValue: number;
  fileIds?: number[];
}

export interface CampusVerification {
  id: number | null;
  realName: string | null;
  studentNo: string | null;
  department: string | null;
  campusEmail: string | null;
  score: number;
  status: string;
  factors: CampusFactor[];
  failureReason?: string | null;
  updatedAt?: string | null;
}

export interface SellerSummary {
  id: number;
  nickname: string;
}

export interface CategorySummary {
  id: number;
  code: string;
  name: string;
  parentId?: number | null;
}

export interface TagSummary {
  id: number;
  name: string;
  description?: string | null;
}

export interface CampusPlaceSummary {
  id: number;
  campus: string;
  name: string;
  detail?: string | null;
}

export interface GoodsImageSummary {
  id: number;
  url: string;
}

export interface GoodsSummary {
  id: number;
  title: string;
  description: string;
  conditionLevel: string;
  listPrice: string;
  status: string;
  auditStatus: string;
  seller: SellerSummary;
  category: CategorySummary;
  primaryImage?: GoodsImageSummary | null;
  publishedAt?: string | null;
}

export interface GoodsUpsertRequest {
  title: string;
  description: string;
  categoryId: number | null;
  conditionLevel: string;
  listPrice: string;
  tradePlaceId: number | null;
  tradePlaceDetail: string;
  availableTimeText: string;
  imageFileIds: number[];
  tagIds: number[];
}

export interface OrderParticipant {
  id: number;
  nickname: string;
}

export interface OrderSummary {
  id: number;
  orderNo: string;
  goodsId: number;
  goodsTitle: string;
  primaryImageFileId?: number | null;
  buyer: OrderParticipant;
  seller: OrderParticipant;
  frozenAmount: string;
  status: string;
  tradePlaceId?: number | null;
  tradePlaceName?: string | null;
  tradePlaceDetail?: string | null;
  meetupTime?: string | null;
  buyerNote?: string | null;
  createdAt: string;
  updatedAt: string;
  closedAt?: string | null;
}

export interface CreateOrderRequest {
  goodsId: number;
  tradePlaceId: number | null;
  tradePlaceDetail: string;
  meetupTime: string | null;
  note: string;
}

export interface PaymentSummary {
  id: number;
  paymentNo: string;
  orderId: number;
  amount: string;
  status: string;
  provider: string;
  createdAt: string;
  paidAt?: string | null;
  closedAt?: string | null;
}

export interface CompletionRequestSummary {
  id: number;
  orderId: number;
  status: string;
  windowStartsAt: string;
  windowEndsAt: string;
  confirmedAt?: string | null;
  createdAt: string;
}

export interface ReviewSummary {
  id: number;
  orderId: number;
  reviewerId: number;
  reviewedUserId: number;
  rating: number;
  content: string;
  status: string;
  submittedAt: string;
  modifiedUntil?: string | null;
  visibleAt?: string | null;
}
