package com.hanspoon.backend_api.domain.upload.controller;

import com.hanspoon.backend_api.domain.upload.dto.UploadTicketRequest;
import com.hanspoon.backend_api.domain.upload.dto.UploadTicketResponse;
import com.hanspoon.backend_api.domain.upload.service.S3StorageService;
import com.hanspoon.backend_api.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 이미지 업로드용 presigned URL 발급. 인증된 사용자만 호출. */
@Tag(name = "Upload", description = "이미지 업로드용 presigned URL 발급 API")
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final S3StorageService s3StorageService;

    public UploadController(S3StorageService s3StorageService) {
        this.s3StorageService = s3StorageService;
    }

    @Operation(summary = "업로드용 presigned PUT URL 발급. FE 는 uploadUrl 로 PUT 후 스캔 요청에 storageKey 전달")
    @PostMapping("/sas")
    public UploadTicketResponse issueUploadTicket(
            @CurrentUser String userId, @Valid @RequestBody UploadTicketRequest request) {
        return s3StorageService.createUploadUrl(UUID.fromString(userId), request.contentType());
    }
}
