package com.hanspoon.backend_api.domain.upload.dto;

/**
 * S3 HeadObject 검증을 통과한 업로드 객체 메타데이터.
 *
 * @param storageKey 검증한 S3 객체 키
 * @param versionId 검증 시점의 S3 객체 버전 ID
 * @param eTag 검증 시점의 S3 객체 ETag
 * @param contentLength 객체 크기(byte)
 * @param contentType 객체 MIME 타입
 */
public record VerifiedUpload(
        String storageKey, String versionId, String eTag, Long contentLength, String contentType) {}
