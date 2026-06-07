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
  conversationId?: number | null;
  acceptedBargainCardId?: number | null;
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
  acceptedBargainCardId?: number | null;
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

export interface ConversationParticipant {
  id: number;
  nickname: string;
}

export interface ConversationSummary {
  id: number;
  goodsId: number;
  goodsTitle: string;
  primaryImageFileId?: number | null;
  buyer: ConversationParticipant;
  seller: ConversationParticipant;
  status: string;
  lastMessageId?: number | null;
  lastMessageText?: string | null;
  lastMessageAt?: string | null;
  unreadCount: number;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface MessageAttachmentSummary {
  id: number;
  fileId: number;
  originalName: string;
  contentType: string;
  byteSize: number;
  url: string;
}

export interface MessageSummary {
  id: number;
  conversationId: number;
  sender?: ConversationParticipant | null;
  messageType: string;
  status: string;
  textContent?: string | null;
  cardId?: number | null;
  attachments: MessageAttachmentSummary[];
  sentAt: string;
}

export interface BargainCardSummary {
  id: number;
  conversationId: number;
  amount: string;
  note?: string | null;
  actionStatus: string;
  createdByUserId: number;
  actedByUserId?: number | null;
  createdAt: string;
  expiresAt?: string | null;
  actedAt?: string | null;
}

export interface ConversationDetail {
  conversation: ConversationSummary;
  messages: MessageSummary[];
  bargainCards: BargainCardSummary[];
}

export interface ConversationRealtimeEvent {
  type: "MESSAGE_RECEIVED" | "BARGAIN_OFFERED" | "BARGAIN_ACCEPTED" | "BARGAIN_REJECTED" | string;
  conversationId: number;
  message?: MessageSummary | null;
  bargainCard?: BargainCardSummary | null;
  conversation?: ConversationSummary | null;
  receiverUserId?: number | null;
  occurredAt: string;
}
