// 文件功能：文件业务服务，负责上传校验、对象存储写入、可见性判定、脱敏预览和敏感访问审计。
package com.campusresale.files;

import com.campusresale.platform.api.ApiException;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.audit.AuditLogRepository;
import com.campusresale.platform.config.CampusResaleProperties;
import com.campusresale.platform.config.SystemConfigRepository;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.SecurityProperties;
import com.campusresale.platform.storage.ObjectStorageClient;
import com.campusresale.platform.storage.StoredObject;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {

    private static final long BYTES_PER_MB = 1024L * 1024L;
    private static final DateTimeFormatter PREVIEW_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ObjectStorageClient objectStorageClient;
    private final FileRepository fileRepository;
    private final AuditLogRepository auditLogRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final String bucket;

    public FileService(
            ObjectStorageClient objectStorageClient,
            FileRepository fileRepository,
            AuditLogRepository auditLogRepository,
            SystemConfigRepository systemConfigRepository,
            CampusResaleProperties properties
    ) {
        this.objectStorageClient = objectStorageClient;
        this.fileRepository = fileRepository;
        this.auditLogRepository = auditLogRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.bucket = properties.storage().bucket();
    }

    /**
     * 上传图片文件：先做大小/类型校验，再写入对象存储，最后落库保存业务可追溯的文件元数据。
     */
    @Transactional
    public StoredFileSummary upload(MultipartFile file, FileKind fileKind, VisibilityScope requestedScope, CurrentPrincipal principal) {
        byte[] bytes = readBytes(file);
        validateSize(bytes.length);
        String detectedContentType = detectContentType(bytes)
                .orElseThrow(() -> ApiExceptions.unsupportedMediaType(
                        "文件类型不支持，仅允许 JPEG、PNG 或 WebP 图片",
                        Map.of("field", "file")
                ));
        validateDeclaredContentType(file.getContentType(), detectedContentType);

        VisibilityScope visibilityScope = resolveVisibility(fileKind, requestedScope);
        String originalName = sanitizeOriginalName(file.getOriginalFilename());
        String storageKey = storageKey(fileKind, principal.id(), originalName);
        String checksum = sha256Hex(bytes);

        objectStorageClient.putObject(storageKey, bytes, detectedContentType);
        try {
            StoredFileRecord record = fileRepository.create(
                    bucket,
                    storageKey,
                    originalName,
                    detectedContentType,
                    bytes.length,
                    checksum,
                    fileKind,
                    visibilityScope,
                    principal.id()
            );
            return StoredFileSummary.from(record);
        } catch (RuntimeException exception) {
            // 元数据落库失败时回收刚写入的对象，避免 MinIO 中留下无主文件。
            objectStorageClient.deleteObject(storageKey);
            throw exception;
        }
    }

    /**
     * 返回文件元数据，权限口径比原件读取宽：本人可看到认证材料元信息，但不能直接拿到原件。
     */
    public StoredFileSummary metadata(long fileId, Optional<CurrentPrincipal> principal) {
        StoredFileRecord record = fileRepository.findById(fileId)
                .orElseThrow(() -> ApiExceptions.notFound("文件不存在或不可见"));
        requireMetadataAccess(record, principal);
        return StoredFileSummary.from(record);
    }

    /**
     * 根据文件可见范围读取内容：公开图直接返回，私密/参与者/管理员专属材料分别走不同授权分支。
     */
    public FileContentResponse content(long fileId, Optional<CurrentPrincipal> principal, String reason, String ipAddress) {
        StoredFileRecord record = fileRepository.findById(fileId)
                .orElseThrow(() -> ApiExceptions.notFound("文件不存在或不可见"));

        if (record.visibilityScope() == VisibilityScope.PUBLIC) {
            return originalContent(record);
        }

        CurrentPrincipal currentPrincipal = principal.orElse(null);
        if (record.visibilityScope() == VisibilityScope.PRIVATE) {
            if (currentPrincipal != null && isOwner(record, currentPrincipal)) {
                return originalContent(record);
            }
            if (currentPrincipal != null && isAdmin(currentPrincipal)) {
                if (record.fileKind() == FileKind.ORDER_EVIDENCE && "REFUND".equals(record.businessType())) {
                    return originalSensitiveContent(record, currentPrincipal, "REFUND_EVIDENCE", reason, ipAddress);
                }
                return originalContent(record);
            }
            throw ApiExceptions.notFound("文件不存在或不可见");
        }

        if (record.visibilityScope() == VisibilityScope.PARTICIPANTS && record.fileKind() == FileKind.MESSAGE_IMAGE) {
            if (currentPrincipal != null && isOwner(record, currentPrincipal) && record.businessId() == null) {
                return originalContent(record);
            }
            if (currentPrincipal != null && fileRepository.isMessageAttachmentParticipant(record.id(), currentPrincipal.id())) {
                return originalContent(record);
            }
            if (currentPrincipal != null && isAdmin(currentPrincipal)) {
                return originalSensitiveContent(record, currentPrincipal, "PRIVATE_MESSAGE_IMAGE", reason, ipAddress);
            }
            throw ApiExceptions.notFound("文件不存在或不可见");
        }

        // 校园认证材料是最敏感的文件类型：本人只能看脱敏预览，管理员看原件必须留审计记录。
        if (record.visibilityScope() == VisibilityScope.ADMIN_ONLY && record.fileKind() == FileKind.CAMPUS_AUTH_MATERIAL) {
            if (currentPrincipal != null && isAdmin(currentPrincipal)) {
                return originalSensitiveContent(record, currentPrincipal, "CAMPUS_AUTH_MATERIAL", reason, ipAddress);
            }
            if (currentPrincipal != null && isOwner(record, currentPrincipal)) {
                return redactedPreview(record);
            }
            throw ApiExceptions.notFound("文件不存在或不可见");
        }

        if (record.visibilityScope() == VisibilityScope.ADMIN_ONLY
                && currentPrincipal != null
                && isAdmin(currentPrincipal)) {
            return originalContent(record);
        }

        throw ApiExceptions.notFound("文件不存在或不可见");
    }

    /**
     * 校园认证保存时调用：确保 documentFileIds 确实是当前用户上传的认证材料，防止串用他人文件。
     */
    public StoredFileRecord requireOwnedCampusAuthMaterial(long fileId, long ownerUserId) {
        StoredFileRecord record = fileRepository.findById(fileId)
                .orElseThrow(() -> ApiExceptions.validation("认证材料不存在", Map.of("field", "documentFileIds")));
        if (!Long.valueOf(ownerUserId).equals(record.ownerUserId())
                || record.fileKind() != FileKind.CAMPUS_AUTH_MATERIAL
                || record.visibilityScope() != VisibilityScope.ADMIN_ONLY) {
            throw ApiExceptions.validation("认证材料必须由当前用户上传并且用途为校园认证材料", Map.of("field", "documentFileIds"));
        }
        return record;
    }

    /**
     * 退款流程使用的证据校验，要求文件归属本人且可见范围仍为 PRIVATE。
     */
    public StoredFileRecord requireOwnedOrderEvidence(long fileId, long ownerUserId) {
        StoredFileRecord record = fileRepository.findById(fileId)
                .orElseThrow(() -> ApiExceptions.validation("退款证据文件不存在", Map.of("field", "evidenceFileIds")));
        if (!Long.valueOf(ownerUserId).equals(record.ownerUserId())
                || record.fileKind() != FileKind.ORDER_EVIDENCE
                || record.visibilityScope() != VisibilityScope.PRIVATE) {
            throw ApiExceptions.validation("退款证据必须由当前用户上传并且用途为 ORDER_EVIDENCE", Map.of("field", "evidenceFileIds"));
        }
        return record;
    }

    /**
     * 头像绑定前校验文件归属和公开属性，避免把私密材料误设为头像。
     */
    public StoredFileRecord requireOwnedPublicAvatar(long fileId, long ownerUserId) {
        StoredFileRecord record = fileRepository.findById(fileId)
                .orElseThrow(() -> ApiExceptions.validation("头像文件不存在", Map.of("field", "avatarFileId")));
        if (!Long.valueOf(ownerUserId).equals(record.ownerUserId())
                || record.fileKind() != FileKind.AVATAR
                || record.visibilityScope() != VisibilityScope.PUBLIC) {
            throw ApiExceptions.validation("头像必须由当前用户上传并且为公开图片", Map.of("field", "avatarFileId"));
        }
        return record;
    }

    private void requireMetadataAccess(StoredFileRecord record, Optional<CurrentPrincipal> principal) {
        if (record.visibilityScope() == VisibilityScope.PUBLIC) {
            return;
        }
        CurrentPrincipal currentPrincipal = principal.orElse(null);
        if (currentPrincipal == null) {
            throw ApiExceptions.notFound("文件不存在或不可见");
        }
        if (isAdmin(currentPrincipal) || isOwner(record, currentPrincipal)) {
            return;
        }
        if (record.visibilityScope() == VisibilityScope.PARTICIPANTS
                && record.fileKind() == FileKind.MESSAGE_IMAGE
                && fileRepository.isMessageAttachmentParticipant(record.id(), currentPrincipal.id())) {
            return;
        }
        throw ApiExceptions.notFound("文件不存在或不可见");
    }

    private FileContentResponse originalSensitiveContent(
            StoredFileRecord record,
            CurrentPrincipal principal,
            String targetType,
            String reason,
            String ipAddress
    ) {
        // 所有敏感原件读取都从这里经过，统一补默认理由并写 sensitive_access_logs。
        String defaultReason = switch (targetType) {
            case "PRIVATE_MESSAGE_IMAGE" -> "查看私信图片";
            case "REFUND_EVIDENCE" -> "查看退款证据";
            default -> "审核认证材料";
        };
        String accessReason = reason == null || reason.isBlank() ? defaultReason : reason.trim();
        try {
            FileContentResponse response = originalContent(record);
            auditLogRepository.recordSensitiveAccess(
                    principal.id(),
                    targetType,
                    record.id(),
                    accessReason,
                    "ALLOWED",
                    ipAddress
            );
            return response;
        } catch (RuntimeException exception) {
            auditLogRepository.recordSensitiveAccess(
                    principal.id(),
                    targetType,
                    record.id(),
                    accessReason,
                    "FAILED",
                    ipAddress
            );
            throw exception;
        }
    }

    private FileContentResponse originalContent(StoredFileRecord record) {
        StoredObject storedObject;
        try {
            storedObject = objectStorageClient.getObject(record.storageKey());
        } catch (ApiException exception) {
            if (isSeedGoodsPlaceholder(record)) {
                return seededGoodsPlaceholder(record);
            }
            throw exception;
        }
        String contentType = storedObject.contentType() == null || storedObject.contentType().isBlank()
                ? record.contentType()
                : storedObject.contentType();
        return new FileContentResponse(storedObject.bytes(), contentType, record.originalName());
    }

    private FileContentResponse redactedPreview(StoredFileRecord record) {
        // 本人查看校园认证材料时生成占位预览，既确认已上传，又不把证件原图回传到普通页面。
        try {
            BufferedImage image = new BufferedImage(900, 420, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(247, 249, 252));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(31, 41, 55));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
            graphics.drawString("Campus auth material uploaded", 48, 78);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 23));
            graphics.drawString("Original document is hidden from student preview.", 48, 128);
            graphics.drawString("File ID: " + record.id(), 48, 190);
            graphics.drawString("Type: " + record.contentType(), 48, 230);
            graphics.drawString("Size: " + record.byteSize() + " bytes", 48, 270);
            graphics.drawString("Audit: " + record.auditStatus().name(), 48, 310);
            graphics.drawString("Uploaded: " + PREVIEW_TIME_FORMATTER.format(record.createdAt().atOffset(ZoneOffset.UTC)), 48, 350);
            graphics.dispose();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return new FileContentResponse(output.toByteArray(), "image/png", "campus-auth-material-preview.png");
        } catch (Exception exception) {
            throw ApiExceptions.internalError();
        }
    }

    private FileContentResponse seededGoodsPlaceholder(StoredFileRecord record) {
        try {
            BufferedImage image = new BufferedImage(960, 640, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(245, 247, 250));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(38, 70, 83));
            graphics.fillRoundRect(64, 64, 832, 512, 36, 36);
            graphics.setColor(new Color(233, 196, 106));
            graphics.fillOval(112, 112, 144, 144);
            graphics.setColor(new Color(244, 162, 97));
            graphics.fillRoundRect(300, 140, 480, 72, 24, 24);
            graphics.setColor(new Color(42, 157, 143));
            graphics.fillRoundRect(300, 260, 360, 64, 22, 22);
            graphics.setColor(new Color(255, 255, 255));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
            graphics.drawString("Campus Resale", 112, 430);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26));
            graphics.drawString("Seed goods placeholder image", 112, 476);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
            graphics.drawString("File ID: " + record.id(), 112, 524);
            graphics.dispose();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return new FileContentResponse(output.toByteArray(), "image/png", record.originalName());
        } catch (Exception exception) {
            throw ApiExceptions.internalError();
        }
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiExceptions.validation("请选择要上传的文件", Map.of("field", "file"));
        }
        try {
            return file.getBytes();
        } catch (Exception exception) {
            throw ApiExceptions.internalError();
        }
    }

    private void validateSize(long byteSize) {
        int maxMb = systemConfigRepository.intValue("campus.auth.material_max_mb", 5);
        long maxBytes = maxMb * BYTES_PER_MB;
        if (byteSize > maxBytes) {
            throw ApiExceptions.payloadTooLarge(
                    "文件不能超过 " + maxMb + " MB",
                    Map.of("maxMb", maxMb)
            );
        }
    }

    private void validateDeclaredContentType(String declaredContentType, String detectedContentType) {
        if (declaredContentType == null || declaredContentType.isBlank()) {
            return;
        }
        String normalized = declaredContentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals(detectedContentType)) {
            throw ApiExceptions.unsupportedMediaType(
                    "文件内容与声明类型不一致",
                    Map.of("declared", normalized, "detected", detectedContentType)
            );
        }
    }

    private Optional<String> detectContentType(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return Optional.of("image/jpeg");
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a) {
            return Optional.of("image/png");
        }
        if (bytes.length >= 12
                && bytes[0] == 0x52
                && bytes[1] == 0x49
                && bytes[2] == 0x46
                && bytes[3] == 0x46
                && bytes[8] == 0x57
                && bytes[9] == 0x45
                && bytes[10] == 0x42
                && bytes[11] == 0x50) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private VisibilityScope resolveVisibility(FileKind fileKind, VisibilityScope requestedScope) {
        if (fileKind == FileKind.CAMPUS_AUTH_MATERIAL) {
            if (requestedScope != null && requestedScope != VisibilityScope.ADMIN_ONLY) {
                throw ApiExceptions.validation("校园认证材料必须为 ADMIN_ONLY", Map.of("field", "visibilityScope"));
            }
            return VisibilityScope.ADMIN_ONLY;
        }
        if (fileKind == FileKind.GOODS_IMAGE) {
            if (requestedScope != null && requestedScope != VisibilityScope.PRIVATE) {
                throw ApiExceptions.validation("M1 商品图片上传阶段必须为 PRIVATE", Map.of("field", "visibilityScope"));
            }
            return VisibilityScope.PRIVATE;
        }
        if (fileKind == FileKind.ORDER_EVIDENCE
                || fileKind == FileKind.REPORT_EVIDENCE
                || fileKind == FileKind.APPEAL_EVIDENCE) {
            if (requestedScope != null && requestedScope != VisibilityScope.PRIVATE) {
                throw ApiExceptions.validation("交易与治理证据文件必须为 PRIVATE", Map.of("field", "visibilityScope"));
            }
            return VisibilityScope.PRIVATE;
        }
        if (fileKind == FileKind.MESSAGE_IMAGE) {
            if (requestedScope != null && requestedScope != VisibilityScope.PARTICIPANTS) {
                throw ApiExceptions.validation("私信图片必须为 PARTICIPANTS", Map.of("field", "visibilityScope"));
            }
            return VisibilityScope.PARTICIPANTS;
        }
        if (requestedScope == null) {
            return VisibilityScope.PUBLIC;
        }
        if (requestedScope == VisibilityScope.PUBLIC || requestedScope == VisibilityScope.PRIVATE) {
            return requestedScope;
        }
        throw ApiExceptions.validation("头像只允许 PUBLIC 或 PRIVATE", Map.of("field", "visibilityScope"));
    }

    private String storageKey(FileKind fileKind, long ownerUserId, String originalName) {
        return fileKind.name().toLowerCase(Locale.ROOT)
                + "/"
                + ownerUserId
                + "/"
                + UUID.randomUUID()
                + "-"
                + originalName;
    }

    private String sanitizeOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "upload.bin";
        }
        String sanitized = originalName.replace('\\', '_').replace('/', '_').trim();
        if (sanitized.length() > 120) {
            return sanitized.substring(sanitized.length() - 120);
        }
        return sanitized;
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception exception) {
            throw ApiExceptions.internalError();
        }
    }

    private boolean isOwner(StoredFileRecord record, CurrentPrincipal principal) {
        return record.ownerUserId() != null && record.ownerUserId() == principal.id();
    }

    private boolean isAdmin(CurrentPrincipal principal) {
        return principal.hasAnyRole(new String[]{
                SecurityProperties.CONTENT_ADMIN_ROLE,
                SecurityProperties.SUPER_ADMIN_ROLE
        });
    }

    private boolean isSeedGoodsPlaceholder(StoredFileRecord record) {
        return record.fileKind() == FileKind.GOODS_IMAGE
                && record.storageKey() != null
                && record.storageKey().startsWith("seed/goods-placeholder/");
    }
}
