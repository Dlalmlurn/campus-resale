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
