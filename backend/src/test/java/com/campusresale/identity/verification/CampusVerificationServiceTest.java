package com.campusresale.identity.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusresale.files.FileRepository;
import com.campusresale.files.FileService;
import com.campusresale.identity.infrastructure.UserAccountRepository;
import com.campusresale.identity.verification.CampusVerificationRequests.ReviewRequest;
import com.campusresale.identity.verification.CampusVerificationRequests.UpsertRequest;
import com.campusresale.platform.api.ApiException;
import com.campusresale.platform.audit.AuditLogRepository;
import com.campusresale.platform.config.SystemConfigRepository;
import com.campusresale.platform.security.CurrentPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CampusVerificationServiceTest {

    private final CampusVerificationRepository campusVerificationRepository = org.mockito.Mockito.mock(CampusVerificationRepository.class);
    private final FileService fileService = org.mockito.Mockito.mock(FileService.class);
    private final FileRepository fileRepository = org.mockito.Mockito.mock(FileRepository.class);
    private final SystemConfigRepository systemConfigRepository = org.mockito.Mockito.mock(SystemConfigRepository.class);
    private final UserAccountRepository userAccountRepository = org.mockito.Mockito.mock(UserAccountRepository.class);
    private final AuditLogRepository auditLogRepository = org.mockito.Mockito.mock(AuditLogRepository.class);
    private final CampusVerificationService service = new CampusVerificationService(
            campusVerificationRepository,
            fileService,
            fileRepository,
            systemConfigRepository,
            userAccountRepository,
            auditLogRepository
    );

    @Test
    void approvedVerificationCannotBeUpdatedInM1() {
        when(campusVerificationRepository.findByUserId(1L)).thenReturn(Optional.of(snapshot(
                CampusVerificationStatus.APPROVED,
                100,
                factor(CampusFactorType.STUDENT_CARD, CampusFactorStatus.VERIFIED, 40, List.of(10L))
        )));

        assertThatThrownBy(() -> service.updateMyVerification(
                principal(1L, Set.of("REGISTERED_USER", "VERIFIED_STUDENT")),
                new UpsertRequest("张三", "20260001", "计算机学院", "zhangsan@example.edu", null, List.of())
        )).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.code()).isEqualTo("CONFLICT"));

        verify(campusVerificationRepository, never()).upsertDraft(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void submitRequiresDocumentMaterial() {
        when(campusVerificationRepository.findByUserId(1L)).thenReturn(Optional.of(snapshot(
                CampusVerificationStatus.ACCUMULATING,
                60,
                factor(CampusFactorType.NAME_STUDENT_NO, CampusFactorStatus.VERIFIED, 40, List.of()),
                factor(CampusFactorType.DEPARTMENT, CampusFactorStatus.VERIFIED, 10, List.of()),
                factor(CampusFactorType.CAMPUS_EMAIL, CampusFactorStatus.VERIFIED, 10, List.of())
        )));

        assertThatThrownBy(() -> service.submitMyVerification(principal(1L, Set.of("REGISTERED_USER"))))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("CONFLICT"));

        verify(campusVerificationRepository, never()).markSubmitted(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void approveGrantsVerifiedStudentWhenFullRulePasses() {
        CampusVerificationSnapshot before = snapshot(
                CampusVerificationStatus.PENDING_REVIEW,
                60,
                factor(CampusFactorType.NAME_STUDENT_NO, CampusFactorStatus.VERIFIED, 40, List.of()),
                factor(CampusFactorType.CAMPUS_EMAIL, CampusFactorStatus.VERIFIED, 10, List.of()),
                factor(CampusFactorType.DEPARTMENT, CampusFactorStatus.VERIFIED, 10, List.of()),
                factor(CampusFactorType.STUDENT_CARD, CampusFactorStatus.PENDING, 0, List.of(10L))
        );
        CampusVerificationSnapshot after = snapshot(
                CampusVerificationStatus.APPROVED,
                100,
                factor(CampusFactorType.NAME_STUDENT_NO, CampusFactorStatus.VERIFIED, 40, List.of()),
                factor(CampusFactorType.CAMPUS_EMAIL, CampusFactorStatus.VERIFIED, 10, List.of()),
                factor(CampusFactorType.DEPARTMENT, CampusFactorStatus.VERIFIED, 10, List.of()),
                factor(CampusFactorType.STUDENT_CARD, CampusFactorStatus.VERIFIED, 40, List.of(10L))
        );
        when(campusVerificationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(before));
        when(campusVerificationRepository.findById(1L)).thenReturn(Optional.of(after));

        CampusVerificationResponse response = service.approve(
                1L,
                new ReviewRequest("材料清晰"),
                principal(2L, Set.of("CONTENT_ADMIN")),
                "127.0.0.1"
        );

        assertThat(response.status()).isEqualTo("APPROVED");
        verify(userAccountRepository).assignRole(1L, "VERIFIED_STUDENT", 2L);
        verify(auditLogRepository).recordOperation(
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq("CAMPUS_VERIFICATION_APPROVE"),
                org.mockito.ArgumentMatchers.eq("CAMPUS_VERIFICATION"),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("127.0.0.1")
        );
    }

    @Test
    void rejectsMalformedCampusEmail() {
        when(campusVerificationRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMyVerification(
                principal(1L, Set.of("REGISTERED_USER")),
                new UpsertRequest(null, null, null, "a@@edu.cn", null, List.of())
        )).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.code()).isEqualTo("VALIDATION_FAILED"));

        verify(campusVerificationRepository, never()).upsertDraft(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void rejectsDisallowedCampusEmailDomain() {
        when(campusVerificationRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(systemConfigRepository.stringListValue(
                org.mockito.ArgumentMatchers.eq("campus.auth.email_suffixes"),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of("edu.cn"));

        assertThatThrownBy(() -> service.updateMyVerification(
                principal(1L, Set.of("REGISTERED_USER")),
                new UpsertRequest(null, null, null, "user@evil-edu.cn", null, List.of())
        )).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.code()).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void acceptsCampusSubdomainEmail() {
        when(campusVerificationRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(systemConfigRepository.stringListValue(
                org.mockito.ArgumentMatchers.eq("campus.auth.email_suffixes"),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of("edu.cn"));
        when(campusVerificationRepository.upsertDraft(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("stu@mails.zju.edu.cn"),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(7L);
        when(campusVerificationRepository.findById(7L)).thenReturn(Optional.of(snapshot(
                CampusVerificationStatus.ACCUMULATING,
                10,
                factor(CampusFactorType.CAMPUS_EMAIL, CampusFactorStatus.VERIFIED, 10, List.of())
        )));

        CampusVerificationResponse response = service.updateMyVerification(
                principal(1L, Set.of("REGISTERED_USER")),
                new UpsertRequest(null, null, null, "stu@mails.zju.edu.cn", null, List.of())
        );

        // 子域邮箱通过后缀校验，落到草稿；不再因为不是完整等于 edu.cn 而被拒。
        assertThat(response).isNotNull();
        verify(campusVerificationRepository).upsertDraft(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("stu@mails.zju.edu.cn"),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private CampusVerificationSnapshot snapshot(
            CampusVerificationStatus status,
            int score,
            CampusFactorRecord... factors
    ) {
        CampusAuthRecord auth = new CampusAuthRecord(
                1L,
                1L,
                "张三",
                "20260001",
                "计算机学院",
                "zhangsan@example.edu",
                score,
                status,
                null,
                null,
                null,
                null,
                Instant.parse("2026-05-31T00:00:00Z"),
                Instant.parse("2026-05-31T00:00:00Z")
        );
        return new CampusVerificationSnapshot(auth, List.of(factors));
    }

    private CampusFactorRecord factor(
            CampusFactorType factorType,
            CampusFactorStatus status,
            int scoreValue,
            List<Long> fileIds
    ) {
        return new CampusFactorRecord(
                factorType.ordinal() + 1L,
                1L,
                factorType,
                scoreValue,
                status,
                "value",
                null,
                null,
                0,
                null,
                fileIds
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
