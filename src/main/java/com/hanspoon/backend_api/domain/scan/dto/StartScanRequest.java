package com.hanspoon.backend_api.domain.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 스캔 시작 요청. 이미지는 미리 Blob 에 업로드된 상태이고, 그 키(또는 우리 컨테이너 URL)를 넘긴다.
 *
 * <p>표시용 제목(title)은 받지 않는다 — 생성 시엔 기본값(스캔 시각)으로 보이고, 수정은 마이페이지의 별도 API 담당.
 *
 * @param storageKey 업로드된 blob 키(예: menu-xxxx.jpg) 또는 우리 컨테이너의 imageUrl
 * @param source 이미지 소스 (camera | upload), 선택
 */
@Schema(description = "스캔 시작 요청")
public record StartScanRequest(
        @Schema(description = "업로드된 blob 키 또는 우리 컨테이너 imageUrl", example = "menu-9f3c.jpg") @NotBlank String storageKey,
        @Schema(description = "이미지 소스", example = "upload") String source) {}
