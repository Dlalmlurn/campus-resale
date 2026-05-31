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

    @GetMapping("/{id}")
    public StoredFileSummary metadata(@PathVariable long id, HttpServletRequest servletRequest) {
        return fileService.metadata(id, principal(servletRequest));
    }

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

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
