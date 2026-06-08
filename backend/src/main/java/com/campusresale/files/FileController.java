// 文件功能：文件上传与读取接口，统一承接头像、商品图、认证材料、订单证据和私信图片等二进制入口。
package com.campusresale.files;

import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.CurrentPrincipalContext;
import com.campusresale.platform.security.RequireLogin;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 上传文件并写入 stored_files 元数据；fileKind 决定默认可见范围和后续业务归属校验。
     */
    @RequireLogin
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StoredFileSummary upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam String fileKind,
            @RequestParam(required = false) String visibilityScope,
            HttpServletRequest servletRequest
    ) {
        CurrentPrincipal principal = CurrentPrincipalContext.get(servletRequest)
                .orElseThrow(ApiExceptions::authRequired);
        return fileService.upload(
                file,
                FileKind.parse(fileKind),
                VisibilityScope.parseOptional(visibilityScope),
                principal
        );
    }

    /**
     * 读取文件元数据；私密文件只允许所属用户、相关会话参与方或管理员看到。
     */
    @GetMapping("/{id}")
    public StoredFileSummary metadata(@PathVariable long id, HttpServletRequest servletRequest) {
        return fileService.metadata(id, principal(servletRequest));
    }

    /**
     * 读取文件内容；校园认证材料对本人只返回脱敏预览，管理员查看原件会记录敏感访问日志。
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(
            @PathVariable long id,
            @RequestParam(required = false) String reason,
            HttpServletRequest servletRequest
    ) {
        FileContentResponse content = fileService.content(id, principal(servletRequest), reason, clientIp(servletRequest));
        return ResponseEntity.ok()
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(content.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(content.bytes());
    }

    private Optional<CurrentPrincipal> principal(HttpServletRequest request) {
        return CurrentPrincipalContext.get(request);
    }

    /**
     * Caddy 反代后优先取 X-Forwarded-For，便于敏感材料访问审计记录真实来源。
     */
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
