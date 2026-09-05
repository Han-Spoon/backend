package com.hanspoon.backend_api.domain.upload.service;

import com.hanspoon.backend_api.domain.upload.dto.UploadTicketResponse;
import com.hanspoon.backend_api.global.config.S3Properties;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@Service
public class S3StorageService {

    private static final String SCAN_PREFIX = "scans/";

    // 브라우저가 실제로 보내는 표준 MIME 만 허용.
    // ⚠ 값(확장자)은 아래 KEY_TAIL_PATTERN 의 허용 목록과 반드시 일치해야 한다.
    //   불일치하면 createUploadUrl 이 만든 키를 resolveKey 가 거부한다.
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private static final Pattern KEY_TAIL_PATTERN =
            Pattern.compile("^[0-9a-fA-F-]{36}/[A-Za-z0-9][A-Za-z0-9._-]{0,127}\\.(jpg|png|webp)$");

    private final S3Presigner presigner;
    private final S3Client s3Client;
    private final S3Properties properties;

    public S3StorageService(S3Presigner presigner, S3Client s3Client, S3Properties properties) {
        this.presigner = presigner;
        this.s3Client = s3Client;
        this.properties = properties;
    }

    public UploadTicketResponse createUploadUrl(UUID userId, String contentType) {
        String normalized = contentType == null ? "" : contentType.trim().toLowerCase();
        String extension = EXTENSIONS.get(normalized);

        if (extension == null) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_TYPE, "Unsupported content type: " + contentType);
        }

        // Server에서 Key 생성.
        String key = "%s%s/%s.%s".formatted(SCAN_PREFIX, userId, UUID.randomUUID(), extension);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(normalized)
                // 같은 URL이 재사용되어 기존 객체가 덮어쓰여지는 것 방지.
                .ifNoneMatch("*")
                .build();

        try {
            PresignedPutObjectRequest signedRequest = presigner.presignPutObject(builder ->
                    builder.signatureDuration(properties.uploadUrlTtl()).putObjectRequest(putRequest));

            return new UploadTicketResponse(
                    key, signedRequest.url().toString(), Instant.now().plus(properties.uploadUrlTtl()));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.STORAGE_PRESIGN_ERROR, "Failed to presign upload URL.", exception);
        }
    }

    // 프론트가 보낸 키 검증, 유효하면 그대로 반환. (S3에 접근할 때 사용)
    public String resolveKey(UUID userId, String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_STORAGE_KEY, "storageKey is blank.");
        }

        String key = storageKey.trim();

        if (!key.startsWith(SCAN_PREFIX) || key.contains("..") || key.contains("//")) {
            throw new BusinessException(ErrorCode.INVALID_STORAGE_KEY, "Invalid storage key: " + key);
        }

        String tail = key.substring(SCAN_PREFIX.length());
        if (!KEY_TAIL_PATTERN.matcher(tail).matches()) {
            throw new BusinessException(ErrorCode.INVALID_STORAGE_KEY, "Invalid storage key: " + key);
        }

        if (!tail.startsWith(userId + "/")) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Storage key belongs to another user.");
        }

        return key;
    }

    // 객체가 실제로 올라왔는지, 크기·타입이 정책에 맞는지 확인.
    public HeadObjectResponse verifyUploadObject(String key) {
        try {
            HeadObjectResponse object = s3Client.headObject(
                    builder -> builder.bucket(properties.bucket()).key(key));

            if (object.contentLength() > properties.maxFileSize()) {
                throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
            }

            if (!EXTENSIONS.containsKey(object.contentType())) {
                throw new BusinessException(ErrorCode.INVALID_CONTENT_TYPE);
            }

            return object;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new BusinessException(ErrorCode.UPLOAD_NOT_FOUND, "Object not found: " + key, exception);
            }
            throw new BusinessException(ErrorCode.STORAGE_PRESIGN_ERROR, "Failed to read object metadata.", exception);
        }
    }

    // presigned GET URL 발급 (객체 조회)
    public String createReadUrl(String key) {
        GetObjectRequest getRequest =
                GetObjectRequest.builder().bucket(properties.bucket()).key(key).build();

        try {
            return presigner
                    .presignGetObject(builder ->
                            builder.signatureDuration(properties.readUrlTtl()).getObjectRequest(getRequest))
                    .url()
                    .toString();
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.STORAGE_PRESIGN_ERROR, "Failed to presign read URL.", exception);
        }
    }

    // DB 기록용 객체 좌표.
    public String objectUri(String key) {
        return "s3://" + properties.bucket() + "/" + key;
    }
}
