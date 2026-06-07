package com.campusresale.conversation;

import com.campusresale.conversation.ConversationRequests.CreateBargainRequest;
import com.campusresale.conversation.ConversationRequests.CreateConversationRequest;
import com.campusresale.conversation.ConversationRequests.SendMessageRequest;
import com.campusresale.goods.GoodsAuditStatus;
import com.campusresale.goods.GoodsRecord;
import com.campusresale.goods.GoodsRepository;
import com.campusresale.goods.GoodsStatus;
import com.campusresale.notification.NotificationService;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.security.CurrentPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    public ConversationService(
            ConversationRepository conversationRepository,
            GoodsRepository goodsRepository,
            NotificationService notificationService
    ) {
        this.conversationRepository = conversationRepository;
        this.goodsRepository = goodsRepository;
        this.notificationService = notificationService;
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
        long conversationId = conversationRepository.createOrGet(goods.id(), buyer.id(), goods.sellerId(), Instant.now());
        return detail(conversationId, buyer);
    }

    public List<ConversationSummary> list(CurrentPrincipal principal) {
        return conversationRepository.listByParticipant(principal.id())
                .stream()
                .map(ConversationSummary::from)
                .toList();
    }

    public ConversationDetailResponse detail(long conversationId, CurrentPrincipal principal) {
        ConversationRecord conversation = requireParticipant(conversationId, principal);
        List<MessageResponse> messages = conversationRepository.listMessages(conversation.id())
                .stream()
                .map(MessageResponse::from)
                .toList();
        List<BargainCardResponse> cards = conversationRepository.listBargains(conversation.id())
                .stream()
                .map(BargainCardResponse::from)
                .toList();
        return new ConversationDetailResponse(ConversationSummary.from(conversation), messages, cards);
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
        notificationService.notifyMessageReceived(receiverId, conversation.id(), conversation.goodsTitle());
        return conversationRepository.findMessageById(messageId)
                .map(MessageResponse::from)
                .orElseThrow(ApiExceptions::internalError);
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
        notificationService.notifyBargainOffered(conversation.sellerId(), conversation.id(), conversation.goodsTitle());
        return conversationRepository.findBargainById(cardId)
                .map(BargainCardResponse::from)
                .orElseThrow(ApiExceptions::internalError);
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
        conversationRepository.createBargainDecisionMessage(conversation.id(), card.id(), "卖家已接受议价", now);
        notificationService.notifyBargainAccepted(conversation.buyerId(), conversation.id(), conversation.goodsTitle());
        return conversationRepository.findBargainById(card.id())
                .map(BargainCardResponse::from)
                .orElseThrow(ApiExceptions::internalError);
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
        conversationRepository.createBargainDecisionMessage(conversation.id(), card.id(), "卖家已拒绝议价", now);
        notificationService.notifyBargainRejected(conversation.buyerId(), conversation.id(), conversation.goodsTitle());
        return conversationRepository.findBargainById(card.id())
                .map(BargainCardResponse::from)
                .orElseThrow(ApiExceptions::internalError);
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

    private ConversationRecord requireParticipant(long conversationId, CurrentPrincipal principal) {
        ConversationRecord conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> ApiExceptions.notFound("会话不存在或不可见"));
        if (conversation.buyerId() != principal.id() && conversation.sellerId() != principal.id()) {
            throw ApiExceptions.notFound("会话不存在或不可见");
        }
        return conversation;
    }

    private ConversationRecord requireParticipantForUpdate(long conversationId, CurrentPrincipal principal) {
        ConversationRecord conversation = conversationRepository.findByIdForUpdate(conversationId)
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
            throw ApiExceptions.conflict("议价卡片已过期", Map.of("cardId", card.id()));
        }
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
}
