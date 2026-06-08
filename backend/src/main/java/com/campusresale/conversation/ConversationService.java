package com.campusresale.conversation;

import com.campusresale.conversation.ConversationRequests.CreateBargainRequest;
import com.campusresale.conversation.ConversationRequests.CreateConversationRequest;
import com.campusresale.conversation.ConversationRequests.SendMessageRequest;
import com.campusresale.files.FileKind;
import com.campusresale.files.FileRepository;
import com.campusresale.files.StoredFileRecord;
import com.campusresale.files.VisibilityScope;
import com.campusresale.goods.GoodsAuditStatus;
import com.campusresale.goods.GoodsRecord;
import com.campusresale.goods.GoodsRepository;
import com.campusresale.goods.GoodsStatus;
import com.campusresale.notification.NotificationRecord;
import com.campusresale.notification.NotificationResponse;
import com.campusresale.notification.NotificationService;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.audit.AuditLogRepository;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.SecurityProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_BARGAIN_NOTE_LENGTH = 240;
    private static final long BARGAIN_TTL_SECONDS = 24 * 60 * 60;

    private final ConversationRepository conversationRepository;
    private final GoodsRepository goodsRepository;
    private final NotificationService notificationService;
    private final FileRepository fileRepository;
    private final AuditLogRepository auditLogRepository;
    private final ConversationRealtimeGateway realtimeGateway;

    public ConversationService(
            ConversationRepository conversationRepository,
            GoodsRepository goodsRepository,
            NotificationService notificationService,
            FileRepository fileRepository,
            AuditLogRepository auditLogRepository,
            ConversationRealtimeGateway realtimeGateway
    ) {
        this.conversationRepository = conversationRepository;
        this.goodsRepository = goodsRepository;
        this.notificationService = notificationService;
        this.fileRepository = fileRepository;
        this.auditLogRepository = auditLogRepository;
        this.realtimeGateway = realtimeGateway;
    }

    @Transactional
    public ConversationDetailResponse createOrGet(CreateConversationRequest request, CurrentPrincipal buyer) {
        if (request == null || request.goodsId() == null || request.goodsId() <= 0) {
            throw ApiExceptions.validation("请选择要联系的商品", Map.of("field", "goodsId"));
        }
        GoodsRecord goods = goodsRepository.findById(request.goodsId())
                .orElseThrow(() -> ApiExceptions.notFound("商品不存在或不可见"));
        if (goods.sellerId() == buyer.id()) {
            throw ApiExceptions.conflict("不能和自己发布的商品建立会话", Map.of("goodsId", goods.id()));
        }
        if (goods.status() != GoodsStatus.ON_SALE || goods.auditStatus() != GoodsAuditStatus.APPROVED) {
            throw ApiExceptions.conflict("商品当前不可联系", Map.of("status", goods.status().name()));
        }
        Instant now = Instant.now();
        long conversationId = conversationRepository.createOrGet(goods.id(), buyer.id(), goods.sellerId(), now);
        conversationRepository.restoreVisibilityForParticipant(conversationId, buyer.id(), now);
        return detail(conversationId, buyer);
    }

    public List<ConversationSummary> list(CurrentPrincipal principal, boolean archivedOnly) {
        expireVisibleBargains(principal.id(), archivedOnly);
        return conversationRepository.listByParticipant(principal.id(), archivedOnly)
                .stream()
                .map(ConversationSummary::from)
                .toList();
    }

    public ConversationDetailResponse detail(long conversationId, CurrentPrincipal principal) {
        ConversationRecord conversation = requireParticipant(conversationId, principal);
        expireExpiredBargains(conversation.id());
        conversationRepository.markMessagesRead(conversation.id(), principal.id(), Instant.now());
        notificationService.markConversationRead(principal.id(), conversation.id());
        ConversationRecord viewerConversation = conversationRepository.findByIdForViewer(conversation.id(), principal.id())
                .orElse(conversation);
        List<MessageResponse> messages = toMessageResponses(conversationRepository.listMessages(conversation.id()));
        List<BargainCardResponse> cards = conversationRepository.listBargains(conversation.id())
                .stream()
                .map(BargainCardResponse::from)
                .toList();
        return new ConversationDetailResponse(ConversationSummary.from(viewerConversation), messages, cards);
    }

    public List<MessageResponse> messagesAfter(long conversationId, long afterId, CurrentPrincipal principal) {
        if (afterId < 0) {
            throw ApiExceptions.validation("lastMessageId 不能小于 0", Map.of("field", "lastMessageId"));
        }
        ConversationRecord conversation = requireParticipant(conversationId, principal);
        List<MessageResponse> messages = toMessageResponses(conversationRepository.listMessagesAfterId(conversation.id(), afterId));
        conversationRepository.markMessagesRead(conversation.id(), principal.id(), Instant.now());
        notificationService.markConversationRead(principal.id(), conversation.id());
        return messages;
    }

    @Transactional
    public MessageResponse sendText(long conversationId, SendMessageRequest request, CurrentPrincipal sender) {
        ConversationRecord conversation = requireParticipantForUpdate(conversationId, sender);
        if (!"NORMAL".equals(conversation.status())) {
            throw ApiExceptions.conflict("会话当前不可发送消息", Map.of("status", conversation.status()));
        }
        String text = requiredText(request == null ? null : request.textContent(), MAX_MESSAGE_LENGTH, "textContent", "消息内容不能为空");
        Instant now = Instant.now();
        long messageId = conversationRepository.createTextMessage(conversation.id(), sender.id(), text, now);
        long receiverId = otherParticipant(conversation, sender.id());
        conversationRepository.restoreVisibilityForParticipant(conversation.id(), receiverId, now);
        NotificationRecord notification = notificationService.notifyMessageReceived(receiverId, conversation.id(), conversation.goodsTitle());
        MessageResponse response = conversationRepository.findMessageById(messageId)
                .map(record -> MessageResponse.from(record, List.of()))
                .orElseThrow(ApiExceptions::internalError);
        publishRealtime("MESSAGE_RECEIVED", conversation, response, null, notification, receiverId);
        return response;
    }

    @Transactional
    public MessageResponse sendImage(long conversationId, SendMessageRequest request, CurrentPrincipal sender) {
        ConversationRecord conversation = requireParticipantForUpdate(conversationId, sender);
        if (!"NORMAL".equals(conversation.status())) {
            throw ApiExceptions.conflict("会话当前不可发送图片", Map.of("status", conversation.status()));
        }
        List<Long> fileIds = normalizeAttachmentIds(request == null ? null : request.attachmentFileIds());
        if (fileIds.isEmpty()) {
            throw ApiExceptions.validation("请选择要发送的图片", Map.of("field", "attachmentFileIds"));
        }
        validateMessageImages(fileIds, sender.id());
        String text = optionalText(request == null ? null : request.textContent(), MAX_MESSAGE_LENGTH, "textContent", "消息内容最多 2000 个字符");
        Instant now = Instant.now();
        long messageId = conversationRepository.createImageMessage(conversation.id(), sender.id(), text, now);
        conversationRepository.createMessageAttachments(messageId, fileIds);
        fileRepository.attachToBusiness(fileIds, "MESSAGE_ATTACHMENT", messageId);
        long receiverId = otherParticipant(conversation, sender.id());
        conversationRepository.restoreVisibilityForParticipant(conversation.id(), receiverId, now);
        NotificationRecord notification = notificationService.notifyMessageReceived(receiverId, conversation.id(), conversation.goodsTitle());
        MessageRecord record = conversationRepository.findMessageById(messageId)
                .orElseThrow(ApiExceptions::internalError);
        MessageResponse response = toMessageResponses(List.of(record)).getFirst();
        publishRealtime("MESSAGE_RECEIVED", conversation, response, null, notification, receiverId);
        return response;
    }

    @Transactional
    public BargainCardResponse createBargain(long conversationId, CreateBargainRequest request, CurrentPrincipal buyer) {
        ConversationRecord conversation = requireParticipantForUpdate(conversationId, buyer);
        if (buyer.id() != conversation.buyerId()) {
            throw ApiExceptions.forbidden("只有买家可以发起议价");
        }
        if (!"NORMAL".equals(conversation.status())) {
            throw ApiExceptions.conflict("会话当前不可发起议价", Map.of("status", conversation.status()));
        }
        GoodsRecord goods = goodsRepository.findById(conversation.goodsId())
                .orElseThrow(() -> ApiExceptions.notFound("商品不存在或不可见"));
        if (goods.status() != GoodsStatus.ON_SALE || goods.auditStatus() != GoodsAuditStatus.APPROVED) {
            throw ApiExceptions.conflict("商品当前不可议价", Map.of("status", goods.status().name()));
        }
        if (conversationRepository.hasOpenOrderForGoods(goods.id())) {
            throw ApiExceptions.conflict("商品已有进行中的订单，暂不能继续议价", Map.of("goodsId", goods.id()));
        }
        BigDecimal amount = parseMoney(request == null ? null : request.amount(), "amount");
        if (amount.compareTo(goods.listPrice()) > 0) {
            throw ApiExceptions.validation("议价金额不能高于商品挂牌价", Map.of("field", "amount"));
        }
        String note = optionalText(request == null ? null : request.note(), MAX_BARGAIN_NOTE_LENGTH, "note", "议价备注最多 240 个字符");
        Instant now = Instant.now();
        long cardId = conversationRepository.createBargainCard(conversation.id(), buyer.id(), amount, note, now, now.plusSeconds(BARGAIN_TTL_SECONDS));
        conversationRepository.restoreVisibilityForParticipant(conversation.id(), conversation.sellerId(), now);
        NotificationRecord notification = notificationService.notifyBargainOffered(conversation.sellerId(), conversation.id(), conversation.goodsTitle());
        BargainCardResponse response = conversationRepository.findBargainById(cardId)
                .map(BargainCardResponse::from)
                .orElseThrow(ApiExceptions::internalError);
        MessageResponse message = conversationRepository.findMessageByCardId(cardId)
                .map(record -> MessageResponse.from(record, List.of()))
                .orElse(null);
        publishRealtime("BARGAIN_OFFERED", conversation, message, response, notification, conversation.sellerId());
        return response;
    }

    @Transactional
    public BargainCardResponse acceptBargain(long conversationId, long cardId, CurrentPrincipal seller) {
        ConversationRecord conversation = requireParticipantForUpdate(conversationId, seller);
        if (seller.id() != conversation.sellerId()) {
            throw ApiExceptions.forbidden("只有卖家可以接受议价");
        }
        BargainCardRecord card = requireBargainForUpdate(conversation.id(), cardId);
        ensurePendingAndFresh(card);
        if (conversationRepository.hasOpenOrderForGoods(conversation.goodsId())) {
            throw ApiExceptions.conflict("商品已有进行中的订单，暂不能接受议价", Map.of("goodsId", conversation.goodsId()));
        }
        Instant now = Instant.now();
        if (conversationRepository.markBargain(card.id(), "ACCEPTED", seller.id(), now) != 1) {
            throw ApiExceptions.conflict("议价状态已变化，请刷新后重试", Map.of("cardId", card.id()));
        }
        long messageId = conversationRepository.createBargainDecisionMessageReturningId(conversation.id(), card.id(), "卖家已接受议价", now);
        conversationRepository.restoreVisibilityForParticipant(conversation.id(), conversation.buyerId(), now);
        NotificationRecord notification = notificationService.notifyBargainAccepted(conversation.buyerId(), conversation.id(), conversation.goodsTitle());
        BargainCardResponse response = conversationRepository.findBargainById(card.id())
                .map(BargainCardResponse::from)
                .orElseThrow(ApiExceptions::internalError);
        MessageResponse message = conversationRepository.findMessageById(messageId)
                .map(record -> MessageResponse.from(record, List.of()))
                .orElse(null);
        publishRealtime("BARGAIN_ACCEPTED", conversation, message, response, notification, conversation.buyerId());
        return response;
    }

    @Transactional
    public BargainCardResponse rejectBargain(long conversationId, long cardId, CurrentPrincipal seller) {
        ConversationRecord conversation = requireParticipantForUpdate(conversationId, seller);
        if (seller.id() != conversation.sellerId()) {
            throw ApiExceptions.forbidden("只有卖家可以拒绝议价");
        }
        BargainCardRecord card = requireBargainForUpdate(conversation.id(), cardId);
        ensurePendingAndFresh(card);
        Instant now = Instant.now();
        if (conversationRepository.markBargain(card.id(), "REJECTED", seller.id(), now) != 1) {
            throw ApiExceptions.conflict("议价状态已变化，请刷新后重试", Map.of("cardId", card.id()));
        }
        long messageId = conversationRepository.createBargainDecisionMessageReturningId(conversation.id(), card.id(), "卖家已拒绝议价", now);
        conversationRepository.restoreVisibilityForParticipant(conversation.id(), conversation.buyerId(), now);
        NotificationRecord notification = notificationService.notifyBargainRejected(conversation.buyerId(), conversation.id(), conversation.goodsTitle());
        BargainCardResponse response = conversationRepository.findBargainById(card.id())
                .map(BargainCardResponse::from)
                .orElseThrow(ApiExceptions::internalError);
        MessageResponse message = conversationRepository.findMessageById(messageId)
                .map(record -> MessageResponse.from(record, List.of()))
                .orElse(null);
        publishRealtime("BARGAIN_REJECTED", conversation, message, response, notification, conversation.buyerId());
        return response;
    }

    @Transactional
    public ConversationSummary archive(long conversationId, CurrentPrincipal principal) {
        requireParticipantForUpdate(conversationId, principal);
        conversationRepository.archive(conversationId, principal.id(), Instant.now());
        return conversationRepository.findByIdForViewer(conversationId, principal.id())
                .map(ConversationSummary::from)
                .orElseThrow(() -> ApiExceptions.notFound("会话不存在或不可见"));
    }

    @Transactional
    public ConversationSummary unarchive(long conversationId, CurrentPrincipal principal) {
        requireParticipantForUpdate(conversationId, principal);
        conversationRepository.unarchive(conversationId, principal.id(), Instant.now());
        return conversationRepository.findByIdForViewer(conversationId, principal.id())
                .map(ConversationSummary::from)
                .orElseThrow(() -> ApiExceptions.notFound("会话不存在或不可见"));
    }

    @Transactional
    public void delete(long conversationId, CurrentPrincipal principal) {
        requireParticipantForUpdate(conversationId, principal);
        conversationRepository.deleteForParticipant(conversationId, principal.id(), Instant.now());
    }

    @Transactional
    public ConversationSummary block(long conversationId, CurrentPrincipal principal) {
        requireParticipantForUpdate(conversationId, principal);
        conversationRepository.block(conversationId, principal.id(), Instant.now());
        return conversationRepository.findByIdForViewer(conversationId, principal.id())
                .map(ConversationSummary::from)
                .orElseThrow(() -> ApiExceptions.notFound("会话不存在或不可见"));
    }

    public ConversationDetailResponse adminDetail(long conversationId, CurrentPrincipal admin, String reason, String ipAddress) {
        if (!isAdmin(admin)) {
            throw ApiExceptions.forbidden("仅管理员可以查看私信内容");
        }
        String accessReason = reason == null || reason.isBlank() ? "查看私信内容" : reason.trim();
        try {
            ConversationRecord conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> ApiExceptions.notFound("会话不存在或不可见"));
            List<MessageResponse> messages = toMessageResponses(conversationRepository.listMessages(conversation.id()));
            List<BargainCardResponse> cards = conversationRepository.listBargains(conversation.id())
                    .stream()
                    .map(BargainCardResponse::from)
                    .toList();
            auditLogRepository.recordSensitiveAccess(admin.id(), "PRIVATE_MESSAGE", conversationId, accessReason, "ALLOWED", ipAddress);
            return new ConversationDetailResponse(ConversationSummary.from(conversation), messages, cards);
        } catch (RuntimeException exception) {
            auditLogRepository.recordSensitiveAccess(admin.id(), "PRIVATE_MESSAGE", conversationId, accessReason, "FAILED", ipAddress);
            throw exception;
        }
    }

    public AcceptedBargainQuote validateAcceptedBargainForOrder(long goodsId, long buyerId, long cardId) {
        BargainCardRecord card = conversationRepository.findBargainById(cardId)
                .orElseThrow(() -> ApiExceptions.notFound("议价卡片不存在或不可见"));
        ConversationRecord conversation = conversationRepository.findById(card.conversationId())
                .orElseThrow(() -> ApiExceptions.notFound("会话不存在或不可见"));
        if (conversation.goodsId() != goodsId || conversation.buyerId() != buyerId) {
            throw ApiExceptions.notFound("议价卡片不存在或不可见");
        }
        if (!"ACCEPTED".equals(card.actionStatus())) {
            throw ApiExceptions.conflict("只能使用已接受的议价下单", Map.of("status", card.actionStatus()));
        }
        if (card.expiresAt() != null && Instant.now().isAfter(card.expiresAt())) {
            throw ApiExceptions.conflict("议价卡片已过期", Map.of("cardId", card.id()));
        }
        return new AcceptedBargainQuote(conversation.id(), card.id(), card.amount());
    }

    public void notifyReviewSubmitted(Long conversationId, long reviewerId, long receiverId, long orderId) {
        if (conversationId == null) {
            return;
        }
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            if ((conversation.buyerId() != reviewerId && conversation.sellerId() != reviewerId)
                    || (conversation.buyerId() != receiverId && conversation.sellerId() != receiverId)) {
                return;
            }
            Instant now = Instant.now();
            String text = "对方已完成评价，点击查看订单 #" + orderId + " 的评价。";
            long messageId = conversationRepository.createSystemNoticeMessage(conversation.id(), text, orderId, now);
            conversationRepository.restoreVisibilityForParticipant(conversation.id(), receiverId, now);
            NotificationRecord notification = notificationService.notifyReviewSubmitted(receiverId, orderId, conversation.goodsTitle());
            MessageResponse message = conversationRepository.findMessageById(messageId)
                    .map(record -> MessageResponse.from(record, List.of()))
                    .orElse(null);
            publishRealtime("REVIEW_SUBMITTED", conversation, message, null, notification, receiverId);
        });
    }

    private ConversationRecord requireParticipant(long conversationId, CurrentPrincipal principal) {
        ConversationRecord conversation = conversationRepository.findByIdForViewer(conversationId, principal.id())
                .orElseThrow(() -> ApiExceptions.notFound("会话不存在或不可见"));
        if (conversation.buyerId() != principal.id() && conversation.sellerId() != principal.id()) {
            throw ApiExceptions.notFound("会话不存在或不可见");
        }
        return conversation;
    }

    private ConversationRecord requireParticipantForUpdate(long conversationId, CurrentPrincipal principal) {
        ConversationRecord conversation = conversationRepository.findByIdForViewerForUpdate(conversationId, principal.id())
                .orElseThrow(() -> ApiExceptions.notFound("会话不存在或不可见"));
        if (conversation.buyerId() != principal.id() && conversation.sellerId() != principal.id()) {
            throw ApiExceptions.notFound("会话不存在或不可见");
        }
        return conversation;
    }

    private BargainCardRecord requireBargainForUpdate(long conversationId, long cardId) {
        BargainCardRecord card = conversationRepository.findBargainByIdForUpdate(cardId)
                .orElseThrow(() -> ApiExceptions.notFound("议价卡片不存在或不可见"));
        if (card.conversationId() != conversationId) {
            throw ApiExceptions.notFound("议价卡片不存在或不可见");
        }
        return card;
    }

    private void ensurePendingAndFresh(BargainCardRecord card) {
        if (!"PENDING".equals(card.actionStatus())) {
            throw ApiExceptions.conflict("议价卡片已处理", Map.of("status", card.actionStatus()));
        }
        if (card.expiresAt() != null && Instant.now().isAfter(card.expiresAt())) {
            conversationRepository.expireExpiredBargains(card.conversationId(), Instant.now());
            throw ApiExceptions.conflict("议价卡片已过期", Map.of("cardId", card.id()));
        }
    }

    private List<MessageResponse> toMessageResponses(List<MessageRecord> records) {
        List<Long> messageIds = records.stream().map(MessageRecord::id).toList();
        Map<Long, List<MessageAttachmentResponse>> byMessageId = conversationRepository
                .listAttachmentsByMessageIds(messageIds)
                .stream()
                .collect(Collectors.groupingBy(
                        MessageAttachmentRecord::messageId,
                        Collectors.mapping(MessageAttachmentResponse::from, Collectors.toList())
                ));
        return records.stream()
                .map(record -> MessageResponse.from(record, byMessageId.getOrDefault(record.id(), List.of())))
                .toList();
    }

    private List<Long> normalizeAttachmentIds(Collection<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> normalized = fileIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.size() > 4) {
            throw ApiExceptions.validation("一次最多发送 4 张图片", Map.of("field", "attachmentFileIds"));
        }
        return List.copyOf(normalized);
    }

    private void validateMessageImages(List<Long> fileIds, long ownerUserId) {
        List<StoredFileRecord> files = fileRepository.findAllByIds(fileIds);
        if (files.size() != fileIds.size()) {
            throw ApiExceptions.validation("私信图片不存在", Map.of("field", "attachmentFileIds"));
        }
        for (StoredFileRecord file : files) {
            if (!Long.valueOf(ownerUserId).equals(file.ownerUserId())
                    || file.fileKind() != FileKind.MESSAGE_IMAGE
                    || file.visibilityScope() != VisibilityScope.PARTICIPANTS
                    || file.businessId() != null) {
                throw ApiExceptions.validation("私信图片必须由当前用户上传且尚未绑定消息", Map.of("field", "attachmentFileIds"));
            }
        }
    }

    private void publishRealtime(
            String type,
            ConversationRecord conversation,
            MessageResponse message,
            BargainCardResponse bargainCard,
            NotificationRecord notification,
            Long receiverUserId
    ) {
        realtimeGateway.publishAfterCommit(ConversationRealtimeEvent.of(
                type,
                conversation.id(),
                message,
                bargainCard,
                ConversationSummary.from(conversation),
                notification == null ? null : NotificationResponse.from(notification),
                receiverUserId,
                Set.of(conversation.buyerId(), conversation.sellerId())
        ));
    }

    private void expireVisibleBargains(long userId, boolean archivedOnly) {
        conversationRepository.listByParticipant(userId, archivedOnly)
                .forEach(conversation -> expireExpiredBargains(conversation.id()));
    }

    private void expireExpiredBargains(long conversationId) {
        conversationRepository.expireExpiredBargains(conversationId, Instant.now());
    }

    private long otherParticipant(ConversationRecord conversation, long userId) {
        return userId == conversation.buyerId() ? conversation.sellerId() : conversation.buyerId();
    }

    private String requiredText(String value, int maxLength, String field, String message) {
        String text = optionalText(value, maxLength, field, message);
        if (text == null) {
            throw ApiExceptions.validation(message, Map.of("field", field));
        }
        return text;
    }

    private String optionalText(String value, int maxLength, String field, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        if (text.length() > maxLength) {
            throw ApiExceptions.validation(message, Map.of("field", field));
        }
        return text;
    }

    private BigDecimal parseMoney(String value, String field) {
        if (value == null || value.isBlank()) {
            throw ApiExceptions.validation("金额不能为空", Map.of("field", field));
        }
        try {
            BigDecimal amount = new BigDecimal(value.trim()).setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(new BigDecimal("0.01")) < 0) {
                throw ApiExceptions.validation("金额必须大于等于 0.01", Map.of("field", field));
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw ApiExceptions.validation("金额格式不正确", Map.of("field", field));
        }
    }

    private boolean isAdmin(CurrentPrincipal principal) {
        return principal.hasAnyRole(new String[]{
                SecurityProperties.CONTENT_ADMIN_ROLE,
                SecurityProperties.SUPER_ADMIN_ROLE
        });
    }
}
