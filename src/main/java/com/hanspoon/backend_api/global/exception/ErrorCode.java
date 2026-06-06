package com.hanspoon.backend_api.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."),
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profile not found."),
    PROFILE_ALREADY_EXISTS(HttpStatus.CONFLICT, "PROFILE_ALREADY_EXISTS", "Profile already exists."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid or expired token."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied."),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Too many requests."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request."),
    OCR_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "OCR_SERVICE_ERROR", "OCR service failed."),
    RULE_ENGINE_ERROR(HttpStatus.BAD_GATEWAY, "RULE_ENGINE_ERROR", "Rule engine evaluation failed."),
    AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI_SERVICE_UNAVAILABLE", "AI service is unavailable."),
    INVALID_CONTENT_TYPE(HttpStatus.BAD_REQUEST, "INVALID_CONTENT_TYPE", "Unsupported image content type."),
    INVALID_STORAGE_KEY(HttpStatus.BAD_REQUEST, "INVALID_STORAGE_KEY", "Invalid storage key or image URL."),
    BLOB_SAS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "BLOB_SAS_ERROR", "Failed to issue blob SAS."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Unexpected server error.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
