package com.hanspoon.backend_api.domain.upload.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 쓰기 SAS 발급 응답 (Path B). FE 는 {@code uploadUrl} 로 이미지를 PUT 하고, 스캔 요청 시 {@code storageKey} 를 보낸다.
 *
 * @param storageKey 서버가 생성한 blob 명 (예: menu-xxxx.jpg)
 * @param uploadUrl 쓰기 SAS 가 붙은 업로드 URL (PUT 대상)
 * @param readUrl SAS 없는 평문 blob URL (참조용)
 * @param expiresAt 업로드 SAS 만료 시각
 */
@Schema(description = "업로드 SAS 발급 응답")
public record UploadTicketResponse(String storageKey, String uploadUrl, String readUrl, Instant expiresAt) {}
