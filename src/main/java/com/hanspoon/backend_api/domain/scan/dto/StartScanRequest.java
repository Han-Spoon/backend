package com.hanspoon.backend_api.domain.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 스캔 시작 요청. 이미지는 presigned URL 로 S3 에 업로드된 상태이고, 그 객체 키를 넘긴다.
 *
 * <p>표시용 제목(title)은 받지 않는다 — 생성 시엔 기본값(스캔 시각)으로 보이고, 수정은 마이페이지의 별도 API 담당.
 *
 * @param storageKey 업로드된 S3 객체 키 (예: scans/{userId}/{uuid}.jpg)
 * @param source 이미지 소스 (camera | upload), 선택
 */
@Schema(description = "스캔 시작 요청")
public record StartScanRequest(
        @Schema(description = "업로드된 S3 객체 키", example = "scans/3f2a.../9f3c....jpg") @NotBlank String storageKey,
        @Schema(description = "이미지 소스", example = "upload") String source) {}
