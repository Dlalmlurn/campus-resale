package com.campusresale.goods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusresale.files.FileAuditStatus;
import com.campusresale.files.FileRepository;
import com.campusresale.files.VisibilityScope;
import com.campusresale.goods.GoodsRepository.ForbiddenTerm;
import com.campusresale.goods.GoodsRequests.ReviewRequest;
import com.campusresale.identity.verification.CampusTradeEligibility;
import com.campusresale.identity.verification.CampusTradeEligibilityResolver;
import com.campusresale.platform.api.ApiException;
import com.campusresale.platform.security.CurrentPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GoodsServiceTest {

    private final GoodsRepository goodsRepository = org.mockito.Mockito.mock(GoodsRepository.class);
    private final CatalogRepository catalogRepository = org.mockito.Mockito.mock(CatalogRepository.class);
    private final FileRepository fileRepository = org.mockito.Mockito.mock(FileRepository.class);
    private final CampusTradeEligibilityResolver campusTradeEligibilityResolver = org.mockito.Mockito.mock(CampusTradeEligibilityResolver.class);
    private final GoodsService service = new GoodsService(
            goodsRepository,
            catalogRepository,
            fileRepository,
            campusTradeEligibilityResolver
    );

    @Test
    void submitRequiresRoleAndFullCanTradeRule() {
        CurrentPrincipal principal = principal(1L, Set.of("REGISTERED_USER", "VERIFIED_STUDENT"));
        when(campusTradeEligibilityResolver.resolve(1L, principal.roles()))
                .thenReturn(new CampusTradeEligibility("APPROVED", false));

        assertThatThrownBy(() -> service.submit(100L, principal))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("FORBIDDEN"));

        verify(goodsRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void approvePublishesImagesThroughStateSync() {
        GoodsRecord before = goods(GoodsStatus.PENDING_REVIEW, GoodsAuditStatus.PENDING);
        GoodsRecord after = goods(GoodsStatus.ON_SALE, GoodsAuditStatus.APPROVED);
        when(goodsRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(before));
        when(goodsRepository.findById(100L)).thenReturn(Optional.of(after), Optional.of(after));
        when(goodsRepository.imageFileIds(100L)).thenReturn(List.of(10L));

        GoodsSummary summary = service.approve(
                100L,
                new ReviewRequest("信息完整"),
                principal(2L, Set.of("CONTENT_ADMIN"))
        );

        assertThat(summary.status()).isEqualTo("ON_SALE");
        verify(fileRepository).updateVisibilityScope(List.of(10L), VisibilityScope.PUBLIC);
        verify(fileRepository).updateAuditStatus(List.of(10L), FileAuditStatus.APPROVED);
        verify(goodsRepository).insertAuditRecord(eq(100L), eq(2L), eq(AuditResult.APPROVED), eq("信息完整"), any());
    }

    @Test
    void rejectRetractsImageVisibilityThroughStateSync() {
        GoodsRecord before = goods(GoodsStatus.PENDING_REVIEW, GoodsAuditStatus.PENDING);
        GoodsRecord after = goods(GoodsStatus.DRAFT, GoodsAuditStatus.REJECTED);
        when(goodsRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(before));
        when(goodsRepository.findById(100L)).thenReturn(Optional.of(after), Optional.of(after));
        when(goodsRepository.imageFileIds(100L)).thenReturn(List.of(10L));

        GoodsSummary summary = service.reject(
                100L,
                new ReviewRequest("图片不清晰"),
                principal(2L, Set.of("CONTENT_ADMIN"))
        );

        assertThat(summary.auditStatus()).isEqualTo("REJECTED");
        verify(fileRepository).updateVisibilityScope(List.of(10L), VisibilityScope.PRIVATE);
        verify(fileRepository).updateAuditStatus(List.of(10L), FileAuditStatus.PENDING);
        verify(goodsRepository).insertAuditRecord(eq(100L), eq(2L), eq(AuditResult.REJECTED), eq("图片不清晰"), any());
    }

    @Test
    void publicListAcceptsRecommendedSortAndReturnsRecommendationReason() {
        GoodsRecord book = goods(GoodsStatus.ON_SALE, GoodsAuditStatus.APPROVED);
        when(goodsRepository.listPublic(any(GoodsRepository.SearchCriteria.class), eq(1), eq(20))).thenReturn(List.of(book));
        when(goodsRepository.countPublic(any(GoodsRepository.SearchCriteria.class))).thenReturn(1L);

        com.campusresale.platform.api.PageResponse<GoodsSummary> page = service.publicList(
                null,
                null,
                null,
                null,
                null,
                null,
                "RECOMMENDED",
                1,
                20
        );

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().recommendationReason()).isEqualTo("教材资料匹配你的浏览偏好");
        verify(goodsRepository).listPublic(any(GoodsRepository.SearchCriteria.class), eq(1), eq(20));
    }

    @Test
    void submitBlocksForbiddenTermAndRecordsRuleHit() {
        CurrentPrincipal principal = principal(1L, Set.of("REGISTERED_USER", "VERIFIED_STUDENT"));
        GoodsRecord draft = new GoodsRecord(
                100L,
                1L,
                "认证学生演示账号",
                1L,
                "DIGITAL",
                "数码电子",
                "九成新显示器",
                "包含考试答案资料的描述文本",
                ConditionLevel.LIKE_NEW,
                new BigDecimal("399.00"),
                1L,
                "图书馆门口",
                "工作日晚上",
                GoodsStatus.DRAFT,
                GoodsAuditStatus.NOT_SUBMITTED,
                10L,
                "教材资料匹配你的浏览偏好",
                null,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z")
        );
        when(campusTradeEligibilityResolver.resolve(1L, principal.roles()))
                .thenReturn(new CampusTradeEligibility("APPROVED", true));
        when(goodsRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(draft));
        when(goodsRepository.imageFileIds(100L)).thenReturn(List.of(10L));
        when(catalogRepository.categoryProhibited(1L)).thenReturn(false);
        when(goodsRepository.enabledForbiddenTerms()).thenReturn(List.of(new ForbiddenTerm(1L, "考试答案", "KEYWORD", "BLOCK")));

        assertThatThrownBy(() -> service.submit(100L, principal))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("CONFLICT"));

        verify(goodsRepository).insertRuleHit(eq(100L), any(ForbiddenTerm.class), any());
        verify(goodsRepository, never()).markSubmitted(anyLong());
    }

    private GoodsRecord goods(GoodsStatus status, GoodsAuditStatus auditStatus) {
        return new GoodsRecord(
                100L,
                1L,
                "认证学生演示账号",
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
                status,
                auditStatus,
                10L,
                "教材资料匹配你的浏览偏好",
                status == GoodsStatus.ON_SALE ? Instant.parse("2026-06-01T00:00:00Z") : null,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z")
        );
    }

    private CurrentPrincipal principal(long id, Set<String> roles) {
        return new CurrentPrincipal(
                id,
                "user" + id,
                "User " + id,
                "ACTIVE",
                roles,
                100L,
                Instant.now().plusSeconds(60),
                Instant.now().plusSeconds(120)
        );
    }
}
