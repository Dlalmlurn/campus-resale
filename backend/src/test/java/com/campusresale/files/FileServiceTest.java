package com.campusresale.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusresale.platform.api.ApiException;
import com.campusresale.platform.audit.AuditLogRepository;
import com.campusresale.platform.config.CampusResaleProperties;
import com.campusresale.platform.config.SystemConfigRepository;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.storage.ObjectStorageClient;
import com.campusresale.platform.storage.StoredObject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileServiceTest {

    private final ObjectStorageClient objectStorageClient = org.mockito.Mockito.mock(ObjectStorageClient.class);
    private final FileRepository fileRepository = org.mockito.Mockito.mock(FileRepository.class);
    private final AuditLogRepository auditLogRepository = org.mockito.Mockito.mock(AuditLogRepository.class);
    private final SystemConfigRepository systemConfigRepository = org.mockito.Mockito.mock(SystemConfigRepository.class);
    private final CampusResaleProperties properties = new CampusResaleProperties(
            new CampusResaleProperties.Cors(List.of("http://localhost:5173")),
            new CampusResaleProperties.Storage("http://localhost:9000", "bucket", "access", "secret")
    );
    private final FileService fileService = new FileService(
            objectStorageClient,
            fileRepository,
            auditLogRepository,
            systemConfigRepository,
            properties
    );

    @Test
    void uploadForcesCampusAuthMaterialToAdminOnly() {
        when(systemConfigRepository.intValue("campus.auth.material_max_mb", 5)).thenReturn(5);
        when(fileRepository.create(
                eq("bucket"),
                any(),
                eq("card.png"),
                eq("image/png"),
                eq((long) pngBytes().length),
                any(),
                eq(FileKind.CAMPUS_AUTH_MATERIAL),
                eq(VisibilityScope.ADMIN_ONLY),
                eq(1L)
        )).thenReturn(record(FileKind.CAMPUS_AUTH_MATERIAL, VisibilityScope.ADMIN_ONLY, 1L));

        StoredFileSummary summary = fileService.upload(
                new MockMultipartFile("file", "card.png", "image/png", pngBytes()),
                FileKind.CAMPUS_AUTH_MATERIAL,
                null,
                principal(1L, Set.of("REGISTERED_USER"))
        );

        assertThat(summary.visibilityScope()).isEqualTo("ADMIN_ONLY");
        verify(objectStorageClient).putObject(any(), any(byte[].class), eq("image/png"));
    }

    @Test
    void rejectsMismatchedDeclaredContentType() {
        when(systemConfigRepository.intValue("campus.auth.material_max_mb", 5)).thenReturn(5);

        assertThatThrownBy(() -> fileService.upload(
                new MockMultipartFile("file", "card.jpg", "image/jpeg", pngBytes()),
                FileKind.CAMPUS_AUTH_MATERIAL,
                null,
                principal(1L, Set.of("REGISTERED_USER"))
        )).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE"));

        verify(objectStorageClient, never()).putObject(any(), any(), any());
    }

    @Test
    void ownerReadsRedactedPreviewForCampusAuthMaterial() {
        when(fileRepository.findById(10L)).thenReturn(Optional.of(record(FileKind.CAMPUS_AUTH_MATERIAL, VisibilityScope.ADMIN_ONLY, 1L)));

        FileContentResponse content = fileService.content(
                10L,
                Optional.of(principal(1L, Set.of("REGISTERED_USER"))),
                null,
                "127.0.0.1"
        );

        assertThat(content.contentType()).isEqualTo("image/png");
        assertThat(content.bytes()).isNotEmpty();
        verify(objectStorageClient, never()).getObject(any());
        verify(auditLogRepository, never()).recordSensitiveAccess(anyLong(), any(), anyLong(), any(), any(), any());
    }

    @Test
    void adminReadsOriginalCampusAuthMaterialAndWritesSensitiveAccessLog() {
        when(fileRepository.findById(10L)).thenReturn(Optional.of(record(FileKind.CAMPUS_AUTH_MATERIAL, VisibilityScope.ADMIN_ONLY, 1L)));
        when(objectStorageClient.getObject("key")).thenReturn(new StoredObject(pngBytes(), "image/png"));

        FileContentResponse content = fileService.content(
                10L,
                Optional.of(principal(2L, Set.of("CONTENT_ADMIN"))),
                "审核认证材料",
                "127.0.0.1"
        );

        assertThat(content.contentType()).isEqualTo("image/png");
        verify(auditLogRepository).recordSensitiveAccess(
                2L,
                "CAMPUS_AUTH_MATERIAL",
                10L,
                "审核认证材料",
                "ALLOWED",
                "127.0.0.1"
        );
    }

    @Test
    void seedGoodsPlaceholderIsReadableWhenObjectIsMissing() {
        StoredFileRecord seedPlaceholder = new StoredFileRecord(
                11L,
                "bucket",
                "seed/goods-placeholder/monitor.png",
                "monitor-placeholder.png",
                "image/png",
                0,
                "seed-monitor-placeholder",
                FileKind.GOODS_IMAGE,
                VisibilityScope.PUBLIC,
                1L,
                "GOODS",
                100L,
                FileAuditStatus.APPROVED,
                Instant.parse("2026-06-01T00:00:00Z")
        );
        when(fileRepository.findById(11L)).thenReturn(Optional.of(seedPlaceholder));
        when(objectStorageClient.getObject("seed/goods-placeholder/monitor.png"))
                .thenThrow(new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "NOT_FOUND", "missing"));

        FileContentResponse content = fileService.content(
                11L,
                Optional.empty(),
                null,
                "127.0.0.1"
        );

        assertThat(content.contentType()).isEqualTo("image/png");
        assertThat(content.filename()).isEqualTo("monitor-placeholder.png");
        assertThat(content.bytes()).isNotEmpty();
    }

    private StoredFileRecord record(FileKind fileKind, VisibilityScope visibilityScope, long ownerUserId) {
        return new StoredFileRecord(
                10L,
                "bucket",
                "key",
                "card.png",
                "image/png",
                pngBytes().length,
                "checksum",
                fileKind,
                visibilityScope,
                ownerUserId,
                null,
                null,
                FileAuditStatus.PENDING,
                Instant.parse("2026-05-31T00:00:00Z")
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

    private byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4e, 0x47,
                0x0d, 0x0a, 0x1a, 0x0a,
                0x00, 0x00, 0x00, 0x00
        };
    }
}
