package com.hanspoon.backend_api.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 비즈니스 예외 코드. 도메인 패키지 구조를 따라 묶는다.
 *
 * <p>{@code code} 는 응답 바디의 {@code code} 필드로 그대로 나가는 <b>API 계약</b>이다. 값을 바꾸면 클라이언트의 에러 분기가
 * 깨지므로, 이름만 바꿀 때도 문자열은 유지할 것.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ── 공통 ────────────────────────────────────────────────────────────
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request."),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Too many requests."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Unexpected server error."),

    // ── 인증 · 인가 ─────────────────────────────────────────────────────
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid or expired token."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied."),

    // ── 사용자 · 프로필 ─────────────────────────────────────────────────
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."),
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profile not found."),
    PROFILE_ALREADY_EXISTS(HttpStatus.CONFLICT, "PROFILE_ALREADY_EXISTS", "Profile already exists."),

    // ── 업로드 · 스토리지(S3) ───────────────────────────────────────────
    INVALID_CONTENT_TYPE(HttpStatus.BAD_REQUEST, "INVALID_CONTENT_TYPE", "Unsupported image content type."),
    INVALID_STORAGE_KEY(HttpStatus.BAD_REQUEST, "INVALID_STORAGE_KEY", "Invalid storage key."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Uploaded file exceeds the size limit."),
    UPLOAD_NOT_FOUND(HttpStatus.NOT_FOUND, "UPLOAD_NOT_FOUND", "Uploaded object not found."),
    STORAGE_PRESIGN_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_PRESIGN_ERROR", "Failed to presign storage URL."),

    // ── 스캔 ────────────────────────────────────────────────────────────
    SCAN_NOT_FOUND(HttpStatus.NOT_FOUND, "SCAN_NOT_FOUND", "Scan not found."),

    // ── 소통 카드 ───────────────────────────────────────────────────────
    CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "Saved card not found."),

    // ── AI 서비스 연동 ──────────────────────────────────────────────────
    OCR_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "OCR_SERVICE_ERROR", "OCR service failed."),
    RULE_ENGINE_ERROR(HttpStatus.BAD_GATEWAY, "RULE_ENGINE_ERROR", "Rule engine evaluation failed."),
    RESULT_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "RESULT_SERVICE_ERROR", "Result generation failed."),
    AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI_SERVICE_UNAVAILABLE", "AI service is unavailable."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
