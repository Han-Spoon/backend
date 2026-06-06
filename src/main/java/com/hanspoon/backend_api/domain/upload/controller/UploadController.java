package com.hanspoon.backend_api.domain.upload.controller;

import com.hanspoon.backend_api.domain.upload.dto.UploadSasRequest;
import com.hanspoon.backend_api.domain.upload.dto.UploadTicketResponse;
import com.hanspoon.backend_api.domain.upload.service.BlobStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 1. 업로드 SAS 발급. 인증된 사용자만 호출.
 * 2. FE 하드코딩 컨테이너 SAS 에서는 미사용으로 남는다.
 */
@Tag(name = "Upload", description = "이미지 업로드용 Blob SAS 발급 API")
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final BlobStorageService blobStorageService;

    public UploadController(BlobStorageService blobStorageService) {
        this.blobStorageService = blobStorageService;
    }

    @Operation(summary = "이미지 업로드용 쓰기 SAS 발급. FE 는 uploadUrl 로 PUT 후 스캔 요청에 storageKey 전달")
    @PostMapping("/sas")
    public UploadTicketResponse issueUploadSas(@Valid @RequestBody UploadSasRequest request) {
        return blobStorageService.createUploadSas(request.contentType());
    }
}
