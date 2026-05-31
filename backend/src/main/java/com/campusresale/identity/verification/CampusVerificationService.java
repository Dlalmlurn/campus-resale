package com.campusresale.identity.verification;

import com.campusresale.files.FileAuditStatus;
import com.campusresale.files.FileRepository;
import com.campusresale.files.FileService;
import com.campusresale.identity.infrastructure.UserAccountRepository;
import com.campusresale.identity.verification.CampusVerificationRequests.ReviewRequest;
import com.campusresale.identity.verification.CampusVerificationRequests.UpsertRequest;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.api.PageResponse;
import com.campusresale.platform.audit.AuditLogRepository;
import com.campusresale.platform.config.SystemConfigRepository;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.SecurityProperties;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampusVerificationService {

    private static final int REVIEW_SCORE_THRESHOLD = 50;
    private static final int TRADE_SCORE_THRESHOLD = 60;

    private final CampusVerificationRepository campusVerificationRepository;
    private final FileService fileService;
    private final FileRepository fileRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuditLogRepository auditLogRepository;

    public CampusVerificationService(
            CampusVerificationRepository campusVerificationRepository,
            FileService fileService,
            FileRepository fileRepository,
            SystemConfigRepository systemConfigRepository,
            UserAccountRepository userAccountRepository,
            AuditLogRepository auditLogRepository
    ) {
        this.campusVerificationRepository = campusVerificationRepository;
        this.fileService = fileService;
        this.fileRepository = fileRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.userAccountRepository = userAccountRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public CampusVerificationResponse myVerification(CurrentPrincipal principal) {
        return campusVerificationRepository.findByUserId(principal.id())
                .map(CampusVerificationSnapshot::toResponse)
                .orElseGet(CampusVerificationResponse::none);
    }

    @Transactional
    public CampusVerificationResponse updateMyVerification(CurrentPrincipal principal, UpsertRequest request) {
        campusVerificationRepository.findByUserId(principal.id())
                .filter(snapshot -> snapshot.auth().status() == CampusVerificationStatus.APPROVED)
                .ifPresent(snapshot -> {
                    throw ApiExceptions.conflict("已通过认证不能在 M1 中直接修改核心资料", Map.of("status", "APPROVED"));
                });

        NormalizedUpsert normalized = normalize(request);
        List<Long> documentFileIds = validateDocumentFiles(principal.id(), normalized);
        CampusVerificationStatus nextStatus = normalized.hasAnyContent()
                ? CampusVerificationStatus.ACCUMULATING
                : CampusVerificationStatus.DRAFT;

        long campusAuthId = campusVerificationRepository.upsertDraft(
                principal.id(),
                normalized.realName(),
                normalized.studentNo(),
                normalized.department(),
                normalized.campusEmail(),
                nextStatus
        );

        upsertTextFactors(campusAuthId, normalized);
        upsertDocumentFactor(campusAuthId, normalized.documentType(), documentFileIds);
        campusVerificationRepository.recalculateScore(campusAuthId);

        return campusVerificationRepository.findById(campusAuthId)
                .orElseThrow(() -> ApiExceptions.notFound("认证记录不存在"))
                .toResponse();
    }

    @Transactional
    public CampusVerificationResponse submitMyVerification(CurrentPrincipal principal) {
        CampusVerificationSnapshot snapshot = campusVerificationRepository.findByUserId(principal.id())
                .orElseThrow(() -> ApiExceptions.conflict("请先填写校园认证资料", Map.of("status", "NONE")));

        if (snapshot.auth().status() == CampusVerificationStatus.APPROVED) {
            throw ApiExceptions.conflict("已通过认证不能重复提交", Map.of("status", "APPROVED"));
        }
        if (snapshot.auth().score() < REVIEW_SCORE_THRESHOLD) {
            throw ApiExceptions.conflict("认证可信度不足，暂不能提交审核", Map.of("score", snapshot.auth().score()));
        }

        CampusFactorRecord documentFactor = snapshot.factors().stream()
                .filter(factor -> factor.factorType().isDocumentType())
                .filter(factor -> !factor.fileIds().isEmpty())
                .findFirst()
                .orElseThrow(() -> ApiExceptions.conflict("提交审核前必须上传学生证或校园卡材料", Map.of("field", "documentFileIds")));

        int limit = systemConfigRepository.intValue("campus.auth.factor_resubmit_limit_24h", 3);
        boolean accepted = campusVerificationRepository.incrementSubmitCount(documentFactor.id(), Instant.now(), limit);
        if (!accepted) {
            throw ApiExceptions.rateLimited("同一认证因子 24 小时内最多提交 " + limit + " 次", Map.of("limit", limit));
        }

        campusVerificationRepository.markSubmitted(snapshot.auth().id());
        return campusVerificationRepository.findById(snapshot.auth().id())
                .orElseThrow(() -> ApiExceptions.notFound("认证记录不存在"))
                .toResponse();
    }

    public PageResponse<CampusVerificationResponse> adminList(String status, int page, int pageSize) {
        String normalizedStatus = normalizeStatusFilter(status);
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), 50);
        List<CampusVerificationResponse> items = campusVerificationRepository
                .list(normalizedStatus, normalizedPage, normalizedPageSize)
                .stream()
                .map(CampusVerificationSnapshot::toResponse)
                .toList();
        long total = campusVerificationRepository.count(normalizedStatus);
        return new PageResponse<>(items, normalizedPage, normalizedPageSize, total);
    }

    @Transactional
    public CampusVerificationResponse approve(long authId, ReviewRequest request, CurrentPrincipal admin, String ipAddress) {
        CampusVerificationSnapshot before = campusVerificationRepository.findByIdForUpdate(authId)
                .orElseThrow(() -> ApiExceptions.notFound("认证记录不存在"));
        if (before.auth().status() != CampusVerificationStatus.PENDING_REVIEW) {
            throw ApiExceptions.conflict("只有待审核认证可以通过", Map.of("status", before.auth().status().name()));
        }
        boolean hasDocument = before.factors().stream()
                .anyMatch(factor -> factor.factorType().isDocumentType() && !factor.fileIds().isEmpty());
        if (!hasDocument) {
            throw ApiExceptions.conflict("认证记录缺少证件材料", Map.of("field", "documentFileIds"));
        }

        Instant now = Instant.now();
        try {
            campusVerificationRepository.approveDocumentFactors(authId, admin.id(), now);
            campusVerificationRepository.markApproved(authId, admin.id(), identityClaimKey(before.auth()), now);
        } catch (DuplicateKeyException exception) {
            throw ApiExceptions.conflict("姓名和学号组合已被其他账号认证", Map.of("field", "studentNo"));
        }

        CampusVerificationSnapshot after = campusVerificationRepository.findById(authId)
                .orElseThrow(() -> ApiExceptions.notFound("认证记录不存在"));
        if (isTradeEligible(after)) {
            userAccountRepository.assignRole(after.auth().userId(), SecurityProperties.VERIFIED_STUDENT_ROLE, admin.id());
        }
        fileRepository.updateCampusAuthMaterialAuditStatus(authId, FileAuditStatus.APPROVED);
        auditLogRepository.recordOperation(
                admin.id(),
                "CAMPUS_VERIFICATION_APPROVE",
                "CAMPUS_VERIFICATION",
                authId,
                before.toResponse(),
                Map.of("verification", after.toResponse(), "reason", reviewReason(request, "材料清晰，信息一致")),
                ipAddress
        );
        return after.toResponse();
    }

    @Transactional
    public CampusVerificationResponse reject(long authId, ReviewRequest request, CurrentPrincipal admin, String ipAddress) {
        CampusVerificationSnapshot before = campusVerificationRepository.findByIdForUpdate(authId)
                .orElseThrow(() -> ApiExceptions.notFound("认证记录不存在"));
        if (before.auth().status() != CampusVerificationStatus.PENDING_REVIEW) {
            throw ApiExceptions.conflict("只有待审核认证可以驳回", Map.of("status", before.auth().status().name()));
        }

        String reason = reviewReason(request, "认证材料不符合要求");
        Instant now = Instant.now();
        campusVerificationRepository.rejectDocumentFactors(authId, admin.id(), reason, now);
        campusVerificationRepository.markRejected(authId, admin.id(), reason, now);
        fileRepository.updateCampusAuthMaterialAuditStatus(authId, FileAuditStatus.REJECTED);

        CampusVerificationSnapshot after = campusVerificationRepository.findById(authId)
                .orElseThrow(() -> ApiExceptions.notFound("认证记录不存在"));
        auditLogRepository.recordOperation(
                admin.id(),
                "CAMPUS_VERIFICATION_REJECT",
                "CAMPUS_VERIFICATION",
                authId,
                before.toResponse(),
                Map.of("verification", after.toResponse(), "reason", reason),
                ipAddress
        );
        return after.toResponse();
    }

    private NormalizedUpsert normalize(UpsertRequest request) {
        String realName = blankToNull(request.realName());
        String studentNo = blankToNull(request.studentNo());
        if ((realName == null) != (studentNo == null)) {
            throw ApiExceptions.validation("姓名和学号必须同时填写", Map.of("field", "studentNo"));
        }

        String department = blankToNull(request.department());
        String campusEmail = blankToNull(request.campusEmail());
        if (campusEmail != null) {
            campusEmail = campusEmail.toLowerCase(Locale.ROOT);
            validateCampusEmailSuffix(campusEmail);
        }

        CampusFactorType documentType = null;
        if (request.documentType() != null && !request.documentType().isBlank()) {
            documentType = CampusFactorType.parseDocumentType(request.documentType());
        }
        List<Long> documentFileIds = distinctIds(request.documentFileIds());
        if (documentType == null && !documentFileIds.isEmpty()) {
            throw ApiExceptions.validation("上传证件材料时必须选择证件类型", Map.of("field", "documentType"));
        }
        return new NormalizedUpsert(realName, studentNo, department, campusEmail, documentType, documentFileIds);
    }

    private List<Long> validateDocumentFiles(long userId, NormalizedUpsert normalized) {
        if (normalized.documentType() == null) {
            return List.of();
        }
        if (normalized.documentFileIds().isEmpty()) {
            throw ApiExceptions.validation("请上传学生证或校园卡材料", Map.of("field", "documentFileIds"));
        }
        int maxCount = systemConfigRepository.intValue("campus.auth.document_max_count", 2);
        if (normalized.documentFileIds().size() > maxCount) {
            throw ApiExceptions.validation("认证材料最多上传 " + maxCount + " 张", Map.of("maxCount", maxCount));
        }
        for (Long fileId : normalized.documentFileIds()) {
            fileService.requireOwnedCampusAuthMaterial(fileId, userId);
        }
        return normalized.documentFileIds();
    }

    private void upsertTextFactors(long campusAuthId, NormalizedUpsert normalized) {
        if (normalized.realName() != null && normalized.studentNo() != null) {
            campusVerificationRepository.upsertFactor(
                    campusAuthId,
                    CampusFactorType.NAME_STUDENT_NO,
                    40,
                    CampusFactorStatus.VERIFIED,
                    normalized.realName() + "|" + normalized.studentNo()
            );
        } else {
            campusVerificationRepository.deleteFactor(campusAuthId, CampusFactorType.NAME_STUDENT_NO);
        }

        if (normalized.department() != null) {
            campusVerificationRepository.upsertFactor(
                    campusAuthId,
                    CampusFactorType.DEPARTMENT,
                    10,
                    CampusFactorStatus.VERIFIED,
                    normalized.department()
            );
        } else {
            campusVerificationRepository.deleteFactor(campusAuthId, CampusFactorType.DEPARTMENT);
        }

        if (normalized.campusEmail() != null) {
            campusVerificationRepository.upsertFactor(
                    campusAuthId,
                    CampusFactorType.CAMPUS_EMAIL,
                    10,
                    CampusFactorStatus.VERIFIED,
                    normalized.campusEmail()
            );
        } else {
            campusVerificationRepository.deleteFactor(campusAuthId, CampusFactorType.CAMPUS_EMAIL);
        }
    }

    private void upsertDocumentFactor(long campusAuthId, CampusFactorType documentType, List<Long> documentFileIds) {
        if (documentType == null) {
            campusVerificationRepository.deleteFactor(campusAuthId, CampusFactorType.STUDENT_CARD);
            campusVerificationRepository.deleteFactor(campusAuthId, CampusFactorType.CAMPUS_CARD);
            return;
        }
        CampusFactorType otherType = documentType == CampusFactorType.STUDENT_CARD
                ? CampusFactorType.CAMPUS_CARD
                : CampusFactorType.STUDENT_CARD;
        campusVerificationRepository.deleteFactor(campusAuthId, otherType);
        long factorId = campusVerificationRepository.upsertFactor(
                campusAuthId,
                documentType,
                0,
                CampusFactorStatus.PENDING,
                "uploaded-material"
        );
        campusVerificationRepository.replaceFactorFiles(factorId, documentFileIds);
        campusVerificationRepository.attachFilesToCampusAuth(documentFileIds, campusAuthId);
    }

    private void validateCampusEmailSuffix(String campusEmail) {
        List<String> suffixes = systemConfigRepository.stringListValue("campus.auth.email_suffixes", List.of("example.edu"));
        int at = campusEmail.lastIndexOf('@');
        if (at <= 0 || at == campusEmail.length() - 1) {
            throw ApiExceptions.validation("校园邮箱格式不正确", Map.of("field", "campusEmail"));
        }
        String domain = campusEmail.substring(at + 1);
        boolean matches = suffixes.stream()
                .map(suffix -> suffix.toLowerCase(Locale.ROOT))
                .anyMatch(domain::equals);
        if (!matches) {
            throw ApiExceptions.validation("校园邮箱后缀不在允许范围内", Map.of("field", "campusEmail"));
        }
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            CampusVerificationStatus parsed = CampusVerificationStatus.valueOf(status);
            if (parsed == CampusVerificationStatus.NONE) {
                throw new IllegalArgumentException();
            }
            return parsed.name();
        } catch (IllegalArgumentException exception) {
            throw ApiExceptions.validation("认证状态不支持", Map.of("field", "status"));
        }
    }

    private boolean isTradeEligible(CampusVerificationSnapshot snapshot) {
        boolean hasVerifiedDocument = snapshot.factors().stream()
                .anyMatch(factor -> factor.factorType().isDocumentType()
                        && factor.status() == CampusFactorStatus.VERIFIED);
        return snapshot.auth().status() == CampusVerificationStatus.APPROVED
                && snapshot.auth().score() >= TRADE_SCORE_THRESHOLD
                && hasVerifiedDocument;
    }

    private String identityClaimKey(CampusAuthRecord auth) {
        String raw = (auth.realName() == null ? "" : auth.realName().trim())
                + "|"
                + (auth.studentNo() == null ? "" : auth.studentNo().trim().toLowerCase(Locale.ROOT));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw ApiExceptions.internalError();
        }
    }

    private String reviewReason(ReviewRequest request, String fallback) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            return fallback;
        }
        return request.reason().trim();
    }

    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw ApiExceptions.validation("文件 id 不正确", Map.of("field", "documentFileIds"));
            }
            distinct.add(id);
        }
        return new ArrayList<>(distinct);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record NormalizedUpsert(
            String realName,
            String studentNo,
            String department,
            String campusEmail,
            CampusFactorType documentType,
            List<Long> documentFileIds
    ) {

        boolean hasAnyContent() {
            return realName != null
                    || studentNo != null
                    || department != null
                    || campusEmail != null
                    || documentType != null
                    || !documentFileIds.isEmpty();
        }
    }
}
