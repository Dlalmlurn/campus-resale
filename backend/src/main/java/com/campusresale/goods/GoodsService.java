package com.campusresale.goods;

import com.campusresale.files.FileAuditStatus;
import com.campusresale.files.FileKind;
import com.campusresale.files.FileRepository;
import com.campusresale.files.StoredFileRecord;
import com.campusresale.files.VisibilityScope;
import com.campusresale.goods.GoodsRepository.AdminCriteria;
import com.campusresale.goods.GoodsRepository.ForbiddenTerm;
import com.campusresale.goods.GoodsRepository.GoodsWriteData;
import com.campusresale.goods.GoodsRepository.MineCriteria;
import com.campusresale.goods.GoodsRepository.SearchCriteria;
import com.campusresale.goods.GoodsRequests.ReviewRequest;
import com.campusresale.goods.GoodsRequests.UpsertRequest;
import com.campusresale.identity.verification.CampusTradeEligibility;
import com.campusresale.identity.verification.CampusTradeEligibilityResolver;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.api.PageResponse;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.SecurityProperties;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoodsService {

    private static final int MAX_IMAGES = 15;

    private final GoodsRepository goodsRepository;
    private final CatalogRepository catalogRepository;
    private final FileRepository fileRepository;
    private final CampusTradeEligibilityResolver campusTradeEligibilityResolver;

    public GoodsService(
            GoodsRepository goodsRepository,
            CatalogRepository catalogRepository,
            FileRepository fileRepository,
            CampusTradeEligibilityResolver campusTradeEligibilityResolver
    ) {
        this.goodsRepository = goodsRepository;
        this.catalogRepository = catalogRepository;
        this.fileRepository = fileRepository;
        this.campusTradeEligibilityResolver = campusTradeEligibilityResolver;
    }

    @Transactional
    public GoodsSummary createDraft(CurrentPrincipal principal, UpsertRequest request) {
        requireTradeEligible(principal);
        NormalizedGoodsInput input = normalizeForCreate(request);
        validateCatalog(input);
        validateImages(principal.id(), null, input.imageFileIds());
        validateTags(input.tagIds());

        long goodsId = goodsRepository.create(principal.id(), input.toWriteData());
        fileRepository.attachToBusiness(input.imageFileIds(), "GOODS", goodsId);
        goodsRepository.replaceImages(goodsId, input.imageFileIds());
        goodsRepository.replaceTags(goodsId, input.tagIds());
        syncGoodsImageVisibility(goodsId);

        return loadSummary(goodsId);
    }

    @Transactional
    public GoodsSummary update(long goodsId, CurrentPrincipal principal, UpsertRequest request) {
        GoodsRecord before = goodsRepository.findByIdForUpdate(goodsId)
                .orElseThrow(() -> ApiExceptions.notFound("商品不存在或不可见"));
        requireSeller(before, principal);
        if (before.status() != GoodsStatus.DRAFT
                || before.auditStatus() == GoodsAuditStatus.PENDING
                || before.auditStatus() == GoodsAuditStatus.APPROVED) {
            throw ApiExceptions.conflict("只有草稿或被驳回商品可以修改", Map.of("status", before.status().name()));
        }

        NormalizedGoodsInput input = normalizeForUpdate(request, before);
        validateCatalog(input);
        validateImages(principal.id(), goodsId, input.imageFileIds());
        validateTags(input.tagIds());

        goodsRepository.updateCore(goodsId, input.toWriteData());
        fileRepository.attachToBusiness(input.imageFileIds(), "GOODS", goodsId);
        goodsRepository.replaceImages(goodsId, input.imageFileIds());
        goodsRepository.replaceTags(goodsId, input.tagIds());
        syncGoodsImageVisibility(goodsId);

        return loadSummary(goodsId);
    }

    @Transactional
    public GoodsSummary submit(long goodsId, CurrentPrincipal principal) {
        requireTradeEligible(principal);
        GoodsRecord before = goodsRepository.findByIdForUpdate(goodsId)
                .orElseThrow(() -> ApiExceptions.notFound("商品不存在或不可见"));
        requireSeller(before, principal);
        if (before.status() != GoodsStatus.DRAFT) {
            throw ApiExceptions.conflict("只有草稿商品可以提交审核", Map.of("status", before.status().name()));
        }

        List<Long> imageFileIds = goodsRepository.imageFileIds(goodsId);
        validateImageCount(imageFileIds);
        if (catalogRepository.categoryProhibited(before.categoryId())) {
            throw ApiExceptions.conflict("该分类暂不允许发布", Map.of("field", "categoryId"));
        }
        rejectForbiddenContent(goodsId, before.title(), before.description());

        goodsRepository.markSubmitted(goodsId);
        syncGoodsImageVisibility(goodsId);
        return loadSummary(goodsId);
    }

    public PageResponse<GoodsSummary> mine(String status, String auditStatus, int page, int pageSize, CurrentPrincipal principal) {
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizePageSize(pageSize);
        MineCriteria criteria = new MineCriteria(
                principal.id(),
                GoodsStatus.parseFilter(status),
                GoodsAuditStatus.parseFilter(auditStatus)
        );
        List<GoodsSummary> items = goodsRepository.listMine(criteria, normalizedPage, normalizedPageSize)
                .stream()
                .map(GoodsRecord::toSummary)
                .toList();
        long total = goodsRepository.countMine(criteria);
        return new PageResponse<>(items, normalizedPage, normalizedPageSize, total);
    }

    public PageResponse<GoodsSummary> publicList(
            String keyword,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String conditionLevel,
            Long placeId,
            String sort,
            int page,
            int pageSize
    ) {
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizePageSize(pageSize);
        validatePriceRange(minPrice, maxPrice);
        SearchCriteria criteria = new SearchCriteria(
                blankToNull(keyword),
                categoryId,
                minPrice,
                maxPrice,
                conditionLevel == null || conditionLevel.isBlank() ? null : ConditionLevel.parse(conditionLevel),
                placeId,
                normalizeSort(sort)
        );
        List<GoodsSummary> items = goodsRepository.listPublic(criteria, normalizedPage, normalizedPageSize)
                .stream()
                .map(GoodsRecord::toSummary)
                .toList();
        long total = goodsRepository.countPublic(criteria);
        return new PageResponse<>(items, normalizedPage, normalizedPageSize, total);
    }

    public GoodsSummary detail(long goodsId, Optional<CurrentPrincipal> principal) {
        GoodsRecord record = goodsRepository.findById(goodsId)
                .orElseThrow(() -> ApiExceptions.notFound("商品不存在或不可见"));
        if (isPublic(record) || canSeeNonPublic(record, principal)) {
            return record.toSummary();
        }
        throw ApiExceptions.notFound("商品不存在或不可见");
    }

    public PageResponse<GoodsSummary> adminList(String status, String auditStatus, int page, int pageSize) {
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizePageSize(pageSize);
        AdminCriteria criteria = new AdminCriteria(
                GoodsStatus.parseFilter(status),
                GoodsAuditStatus.parseFilter(auditStatus)
        );
        List<GoodsSummary> items = goodsRepository.listAdmin(criteria, normalizedPage, normalizedPageSize)
                .stream()
                .map(GoodsRecord::toSummary)
                .toList();
        long total = goodsRepository.countAdmin(criteria);
        return new PageResponse<>(items, normalizedPage, normalizedPageSize, total);
    }

    @Transactional
    public GoodsSummary approve(long goodsId, ReviewRequest request, CurrentPrincipal admin) {
        GoodsRecord before = goodsRepository.findByIdForUpdate(goodsId)
                .orElseThrow(() -> ApiExceptions.notFound("商品不存在或不可见"));
        if (before.status() != GoodsStatus.PENDING_REVIEW || before.auditStatus() != GoodsAuditStatus.PENDING) {
            throw ApiExceptions.conflict("只有待审核商品可以通过", Map.of("status", before.status().name()));
        }

        Instant now = Instant.now();
        goodsRepository.markApproved(goodsId, now);
        syncGoodsImageVisibility(goodsId);
        goodsRepository.insertAuditRecord(goodsId, admin.id(), AuditResult.APPROVED, reviewReason(request, "信息完整，图片清晰"), null);
        return loadSummary(goodsId);
    }

    @Transactional
    public GoodsSummary reject(long goodsId, ReviewRequest request, CurrentPrincipal admin) {
        GoodsRecord before = goodsRepository.findByIdForUpdate(goodsId)
                .orElseThrow(() -> ApiExceptions.notFound("商品不存在或不可见"));
        if (before.status() != GoodsStatus.PENDING_REVIEW || before.auditStatus() != GoodsAuditStatus.PENDING) {
            throw ApiExceptions.conflict("只有待审核商品可以驳回", Map.of("status", before.status().name()));
        }

        String reason = reviewReason(request, "图片不清晰或描述不完整");
        goodsRepository.markRejected(goodsId);
        syncGoodsImageVisibility(goodsId);
        goodsRepository.insertAuditRecord(goodsId, admin.id(), AuditResult.REJECTED, reason, null);
        return loadSummary(goodsId);
    }

    private NormalizedGoodsInput normalizeForCreate(UpsertRequest request) {
        if (request == null) {
            throw ApiExceptions.validation("请填写商品信息", Map.of("body", "required"));
        }
        String title = requiredTrimmed(request.title(), "title", "请填写商品标题");
        String description = requiredTrimmed(request.description(), "description", "请填写商品描述");
        validateTitle(title);
        validateDescription(description);
        Long categoryId = requireNonNull(request.categoryId(), "categoryId", "请选择商品分类");
        ConditionLevel conditionLevel = ConditionLevel.parse(requiredTrimmed(request.conditionLevel(), "conditionLevel", "请选择商品成色"));
        BigDecimal listPrice = normalizePrice(request.listPrice());
        List<Long> imageFileIds = distinctIds(request.imageFileIds());
        List<Long> tagIds = distinctIds(request.tagIds());
        return new NormalizedGoodsInput(
                title,
                description,
                categoryId,
                conditionLevel,
                listPrice,
                request.tradePlaceId(),
                blankToNull(request.tradePlaceDetail()),
                blankToNull(request.availableTimeText()),
                imageFileIds,
                tagIds
        );
    }

    private NormalizedGoodsInput normalizeForUpdate(UpsertRequest request, GoodsRecord before) {
        if (request == null) {
            throw ApiExceptions.validation("请填写商品信息", Map.of("body", "required"));
        }
        String title = request.title() == null ? before.title() : requiredTrimmed(request.title(), "title", "请填写商品标题");
        String description = request.description() == null
                ? before.description()
                : requiredTrimmed(request.description(), "description", "请填写商品描述");
        validateTitle(title);
        validateDescription(description);
        Long categoryId = request.categoryId() == null ? before.categoryId() : request.categoryId();
        ConditionLevel conditionLevel = request.conditionLevel() == null
                ? before.conditionLevel()
                : ConditionLevel.parse(requiredTrimmed(request.conditionLevel(), "conditionLevel", "请选择商品成色"));
        BigDecimal listPrice = request.listPrice() == null ? before.listPrice() : normalizePrice(request.listPrice());
        List<Long> imageFileIds = request.imageFileIds() == null
                ? goodsRepository.imageFileIds(before.id())
                : distinctIds(request.imageFileIds());
        List<Long> tagIds = request.tagIds() == null
                ? goodsRepository.tagIds(before.id())
                : distinctIds(request.tagIds());
        return new NormalizedGoodsInput(
                title,
                description,
                categoryId,
                conditionLevel,
                listPrice,
                request.tradePlaceId() == null ? before.tradePlaceId() : request.tradePlaceId(),
                request.tradePlaceDetail() == null ? before.tradePlaceDetail() : blankToNull(request.tradePlaceDetail()),
                request.availableTimeText() == null ? before.availableTimeText() : blankToNull(request.availableTimeText()),
                imageFileIds,
                tagIds
        );
    }

    private void validateCatalog(NormalizedGoodsInput input) {
        if (!catalogRepository.enabledCategoryExists(input.categoryId())) {
            throw ApiExceptions.validation("商品分类不存在或不可用", Map.of("field", "categoryId"));
        }
        if (catalogRepository.categoryProhibited(input.categoryId())) {
            throw ApiExceptions.conflict("该分类暂不允许发布", Map.of("field", "categoryId"));
        }
        if (input.tradePlaceId() != null && !catalogRepository.enabledCampusPlaceExists(input.tradePlaceId())) {
            throw ApiExceptions.validation("校园地点不存在或不可用", Map.of("field", "tradePlaceId"));
        }
    }

    private void validateTags(List<Long> tagIds) {
        Set<Long> enabled = catalogRepository.enabledTagIds(tagIds);
        if (enabled.size() != new HashSet<>(tagIds).size()) {
            throw ApiExceptions.validation("商品标签不存在或不可用", Map.of("field", "tagIds"));
        }
    }

    private void validateImages(long ownerUserId, Long currentGoodsId, List<Long> imageFileIds) {
        validateImageCount(imageFileIds);
        Map<Long, StoredFileRecord> filesById = fileRepository.findAllByIds(imageFileIds)
                .stream()
                .collect(Collectors.toMap(StoredFileRecord::id, Function.identity()));
        for (Long fileId : imageFileIds) {
            StoredFileRecord record = filesById.get(fileId);
            if (record == null) {
                throw ApiExceptions.validation("商品图片不存在", Map.of("field", "imageFileIds"));
            }
            if (!Long.valueOf(ownerUserId).equals(record.ownerUserId())
                    || record.fileKind() != FileKind.GOODS_IMAGE) {
                throw ApiExceptions.validation("商品图片必须由当前用户上传并且用途为商品图片", Map.of("field", "imageFileIds"));
            }
            if (record.businessType() != null
                    && (!"GOODS".equals(record.businessType()) || !Long.valueOf(currentGoodsId == null ? -1L : currentGoodsId).equals(record.businessId()))) {
                throw ApiExceptions.conflict("商品图片已经绑定到其他业务对象", Map.of("fileId", fileId));
            }
        }
    }

    private void validateImageCount(List<Long> imageFileIds) {
        if (imageFileIds.isEmpty()) {
            throw ApiExceptions.validation("商品至少需要 1 张图片", Map.of("field", "imageFileIds"));
        }
        if (imageFileIds.size() > MAX_IMAGES) {
            throw ApiExceptions.validation("商品图片最多 " + MAX_IMAGES + " 张", Map.of("maxCount", MAX_IMAGES));
        }
    }

    private void rejectForbiddenContent(long goodsId, String title, String description) {
        String haystack = (title + "\n" + description).toLowerCase(Locale.ROOT);
        for (ForbiddenTerm term : goodsRepository.enabledForbiddenTerms()) {
            if (haystack.contains(term.term().toLowerCase(Locale.ROOT)) && "BLOCK".equals(term.severity())) {
                goodsRepository.insertRuleHit(goodsId, term, sha256Hex(term.term()));
                throw ApiExceptions.conflict("商品内容命中禁售规则，暂不能提交审核", Map.of("rule", term.termType()));
            }
        }
    }

    private void syncGoodsImageVisibility(long goodsId) {
        GoodsRecord goods = goodsRepository.findById(goodsId)
                .orElseThrow(() -> ApiExceptions.notFound("商品不存在或不可见"));
        List<Long> imageFileIds = goodsRepository.imageFileIds(goodsId);
        if (isPublic(goods)) {
            fileRepository.updateVisibilityScope(imageFileIds, VisibilityScope.PUBLIC);
            fileRepository.updateAuditStatus(imageFileIds, FileAuditStatus.APPROVED);
        } else {
            fileRepository.updateVisibilityScope(imageFileIds, VisibilityScope.PRIVATE);
            fileRepository.updateAuditStatus(imageFileIds, FileAuditStatus.PENDING);
        }
    }

    private GoodsSummary loadSummary(long goodsId) {
        return goodsRepository.findById(goodsId)
                .orElseThrow(() -> ApiExceptions.notFound("商品不存在或不可见"))
                .toSummary();
    }

    private void requireTradeEligible(CurrentPrincipal principal) {
        if (!principal.hasRole(SecurityProperties.VERIFIED_STUDENT_ROLE)) {
            throw ApiExceptions.forbidden("当前账号尚未具备完整交易权限");
        }
        CampusTradeEligibility eligibility = campusTradeEligibilityResolver.resolve(principal.id(), principal.roles());
        if (!eligibility.canTrade()) {
            throw ApiExceptions.forbidden("当前账号尚未具备完整交易权限");
        }
    }

    private void requireSeller(GoodsRecord record, CurrentPrincipal principal) {
        if (record.sellerId() != principal.id()) {
            throw ApiExceptions.notFound("商品不存在或不可见");
        }
    }

    private boolean canSeeNonPublic(GoodsRecord record, Optional<CurrentPrincipal> principal) {
        return principal
                .map(value -> value.id() == record.sellerId() || isAdmin(value))
                .orElse(false);
    }

    private boolean isAdmin(CurrentPrincipal principal) {
        return principal.hasAnyRole(new String[]{
                SecurityProperties.CONTENT_ADMIN_ROLE,
                SecurityProperties.SUPER_ADMIN_ROLE
        });
    }

    private boolean isPublic(GoodsRecord record) {
        return record.status() == GoodsStatus.ON_SALE && record.auditStatus() == GoodsAuditStatus.APPROVED;
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank() || "LATEST".equals(sort)) {
            return "LATEST";
        }
        if ("PRICE_ASC".equals(sort) || "PRICE_DESC".equals(sort)) {
            return sort;
        }
        throw ApiExceptions.validation("商品排序方式不支持", Map.of("field", "sort"));
    }

    private void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw ApiExceptions.validation("最低价格不能小于 0", Map.of("field", "minPrice"));
        }
        if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw ApiExceptions.validation("最高价格不能小于 0", Map.of("field", "maxPrice"));
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw ApiExceptions.validation("最低价格不能大于最高价格", Map.of("field", "minPrice"));
        }
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        if (price == null) {
            throw ApiExceptions.validation("请填写商品价格", Map.of("field", "listPrice"));
        }
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiExceptions.validation("商品价格必须大于 0", Map.of("field", "listPrice"));
        }
        if (price.scale() > 2) {
            throw ApiExceptions.validation("商品价格最多保留 2 位小数", Map.of("field", "listPrice"));
        }
        return price;
    }

    private void validateTitle(String title) {
        if (title.length() < 2 || title.length() > 80) {
            throw ApiExceptions.validation("商品标题必须为 2 到 80 个字符", Map.of("field", "title"));
        }
    }

    private void validateDescription(String description) {
        if (description.length() < 10 || description.length() > 2000) {
            throw ApiExceptions.validation("商品描述必须为 10 到 2000 个字符", Map.of("field", "description"));
        }
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizePageSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), 50);
    }

    private String reviewReason(ReviewRequest request, String fallback) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            return fallback;
        }
        return request.reason().trim();
    }

    private String requiredTrimmed(String value, String field, String message) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            throw ApiExceptions.validation(message, Map.of("field", field));
        }
        return trimmed;
    }

    private <T> T requireNonNull(T value, String field, String message) {
        if (value == null) {
            throw ApiExceptions.validation(message, Map.of("field", field));
        }
        return value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            if (id == null || id <= 0) {
                throw ApiExceptions.validation("ID 参数不正确", Map.of("id", id));
            }
            result.add(id);
        }
        return result;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw ApiExceptions.internalError();
        }
    }

    private record NormalizedGoodsInput(
            String title,
            String description,
            long categoryId,
            ConditionLevel conditionLevel,
            BigDecimal listPrice,
            Long tradePlaceId,
            String tradePlaceDetail,
            String availableTimeText,
            List<Long> imageFileIds,
            List<Long> tagIds
    ) {

        GoodsWriteData toWriteData() {
            return new GoodsWriteData(
                    title,
                    description,
                    categoryId,
                    conditionLevel,
                    listPrice,
                    tradePlaceId,
                    tradePlaceDetail,
                    availableTimeText
            );
        }
    }
}
