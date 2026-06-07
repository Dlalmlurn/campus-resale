package com.campusresale.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusresale.conversation.ConversationRequests.CreateBargainRequest;
import com.campusresale.conversation.ConversationRequests.CreateConversationRequest;
import com.campusresale.conversation.ConversationRequests.SendMessageRequest;
import com.campusresale.goods.ConditionLevel;
import com.campusresale.goods.GoodsAuditStatus;
import com.campusresale.goods.GoodsRecord;
import com.campusresale.goods.GoodsRepository;
import com.campusresale.goods.GoodsStatus;
import com.campusresale.notification.NotificationService;
import com.campusresale.platform.api.ApiException;
import com.campusresale.platform.security.CurrentPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConversationServiceTest {

    private final ConversationRepository conversationRepository = org.mockito.Mockito.mock(ConversationRepository.class);
    private final GoodsRepository goodsRepository = org.mockito.Mockito.mock(GoodsRepository.class);
    private final NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);
    private final ConversationService service = new ConversationService(conversationRepository, goodsRepository, notificationService);

    @Test
    void createConversationRejectsOwnGoods() {
        when(goodsRepository.findById(100L)).thenReturn(Optional.of(goods()));

        assertThatThrownBy(() -> service.createOrGet(new CreateConversationRequest(100L), principal(1L)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("CONFLICT"));
    }

    @Test
    void createConversationReturnsExistingConversationDetail() {
        when(goodsRepository.findById(100L)).thenReturn(Optional.of(goods()));
        when(conversationRepository.createOrGet(eq(100L), eq(2L), eq(1L), any())).thenReturn(20L);
        when(conversationRepository.findById(20L)).thenReturn(Optional.of(conversation()));
        when(conversationRepository.listMessages(20L)).thenReturn(List.of());
        when(conversationRepository.listBargains(20L)).thenReturn(List.of());

        ConversationDetailResponse response = service.createOrGet(new CreateConversationRequest(100L), principal(2L));

        assertThat(response.conversation().id()).isEqualTo(20L);
        assertThat(response.conversation().buyer().id()).isEqualTo(2L);
    }

    @Test
    void sendTextRequiresParticipantAndNotifiesOtherSide() {
        when(conversationRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(conversation()));
        when(conversationRepository.createTextMessage(eq(20L), eq(2L), eq("还在吗？"), any())).thenReturn(30L);
        when(conversationRepository.findMessageById(30L)).thenReturn(Optional.of(message()));

        MessageResponse response = service.sendText(20L, new SendMessageRequest(" 还在吗？ "), principal(2L));

        assertThat(response.textContent()).isEqualTo("还在吗？");
        verify(notificationService).notifyMessageReceived(1L, 20L, "九成新显示器");
    }

    @Test
    void buyerCreatesBargainCard() {
        when(conversationRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(conversation()));
        when(goodsRepository.findById(100L)).thenReturn(Optional.of(goods()));
        when(conversationRepository.hasOpenOrderForGoods(100L)).thenReturn(false);
        when(conversationRepository.createBargainCard(eq(20L), eq(2L), eq(new BigDecimal("360.00")), eq("预算有限"), any(), any()))
                .thenReturn(40L);
        when(conversationRepository.findBargainById(40L)).thenReturn(Optional.of(bargain("PENDING", null)));

        BargainCardResponse response = service.createBargain(
                20L,
                new CreateBargainRequest("360", "预算有限"),
                principal(2L)
        );

        assertThat(response.amount()).isEqualTo("360.00");
        verify(notificationService).notifyBargainOffered(1L, 20L, "九成新显示器");
    }

    @Test
    void sellerAcceptsPendingBargain() {
        when(conversationRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(conversation()));
        when(conversationRepository.findBargainByIdForUpdate(40L)).thenReturn(Optional.of(bargain("PENDING", null)));
        when(conversationRepository.hasOpenOrderForGoods(100L)).thenReturn(false);
        when(conversationRepository.markBargain(eq(40L), eq("ACCEPTED"), eq(1L), any())).thenReturn(1);
        when(conversationRepository.findBargainById(40L)).thenReturn(Optional.of(bargain("ACCEPTED", 1L)));

        BargainCardResponse response = service.acceptBargain(20L, 40L, principal(1L));

        assertThat(response.actionStatus()).isEqualTo("ACCEPTED");
        verify(conversationRepository).createBargainDecisionMessage(eq(20L), eq(40L), eq("卖家已接受议价"), any());
        verify(notificationService).notifyBargainAccepted(2L, 20L, "九成新显示器");
    }

    @Test
    void validatesAcceptedBargainForOrder() {
        when(conversationRepository.findBargainById(40L)).thenReturn(Optional.of(bargain("ACCEPTED", 1L)));
        when(conversationRepository.findById(20L)).thenReturn(Optional.of(conversation()));

        AcceptedBargainQuote quote = service.validateAcceptedBargainForOrder(100L, 2L, 40L);

        assertThat(quote.conversationId()).isEqualTo(20L);
        assertThat(quote.amount()).isEqualByComparingTo("360.00");
    }

    private GoodsRecord goods() {
        return new GoodsRecord(
                100L,
                1L,
                "卖家",
                1L,
                "DIGITAL",
                "数码电子",
                "九成新显示器",
                "自用显示器，配件齐全。",
                ConditionLevel.LIKE_NEW,
                new BigDecimal("399.00"),
                1L,
                "图书馆门口",
                "工作日晚上",
                GoodsStatus.ON_SALE,
                GoodsAuditStatus.APPROVED,
                10L,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z")
        );
    }

    private ConversationRecord conversation() {
        return new ConversationRecord(
                20L,
                100L,
                "九成新显示器",
                10L,
                2L,
                "买家",
                1L,
                "卖家",
                "NORMAL",
                null,
                null,
                null,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z")
        );
    }

    private MessageRecord message() {
        return new MessageRecord(
                30L,
                20L,
                2L,
                "买家",
                "TEXT",
                "SENT",
                "还在吗？",
                null,
                Instant.parse("2026-06-01T00:10:00Z")
        );
    }

    private BargainCardRecord bargain(String status, Long actedByUserId) {
        return new BargainCardRecord(
                40L,
                20L,
                new BigDecimal("360.00"),
                "预算有限",
                status,
                2L,
                actedByUserId,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60),
                actedByUserId == null ? null : Instant.now()
        );
    }

    private CurrentPrincipal principal(long id) {
        return new CurrentPrincipal(
                id,
                "user" + id,
                "User " + id,
                "ACTIVE",
                Set.of("REGISTERED_USER", "VERIFIED_STUDENT"),
                10L,
                Instant.now().plusSeconds(60),
                Instant.now().plusSeconds(120)
        );
    }
}
