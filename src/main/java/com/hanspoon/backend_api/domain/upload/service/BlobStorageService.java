package com.hanspoon.backend_api.domain.upload.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.hanspoon.backend_api.domain.upload.dto.UploadTicketResponse;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Blob 업로드/읽기 SAS 발급.
 * - A: FE 가 Blob 에 직접 업로드(백엔드 우회)하므로 백엔드는 업로드 바이트를 다루지 않고 단일 blob 범위의 짧은 SAS 만 발급한다.
 * - B: FE 가 백엔드에서 쓰기 SAS 를 받아 업로드. Path A 보다 보안은 약간 향상되나, 여전히 SAS URL 이 노출될 수 있으므로 짧은 TTL 권장.
 *
 * <ul>
 *   <li>{@link #createReadSasUrl} / {@link #extractStorageKey} — 두 업로드 경로(A/B) 공통 코어.
 *      AI 가 private blob 을 읽도록 읽기 SAS 를 발급한다.
 *      오케스트레이션이 OcrRequest.imageUrl 빌드에 사용.
 *   <li>{@link #createUploadSas} — Path B 전용. FE 가 백엔드에서 쓰기 SAS 를 받아 업로드.
 * </ul>
 */
@Service
public class BlobStorageService {

    private static final Pattern STORAGE_KEY_PATTERN = Pattern.compile("^menu-[A-Za-z0-9-]+\\.(jpg|jpeg|png|webp)$");

    private static final Map<String, String> CONTENT_TYPE_EXTENSION =
            Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    private final BlobContainerClient containerClient;
    private final Duration uploadSasTtl;
    private final Duration readSasTtl;
    private final List<String> allowedContentTypes;

    public BlobStorageService(
            BlobContainerClient menuImageContainerClient,
            @Value("${app.blob.upload-sas-ttl:10m}") Duration uploadSasTtl,
            @Value("${app.blob.read-sas-ttl:15m}") Duration readSasTtl,
            @Value("${app.blob.allowed-content-types:image/jpeg,image/png,image/webp}")
                    List<String> allowedContentTypes) {
        this.containerClient = menuImageContainerClient;
        this.uploadSasTtl = uploadSasTtl;
        this.readSasTtl = readSasTtl;
        this.allowedContentTypes = allowedContentTypes;
    }

    /** B: 단일 blob 쓰기 SAS 발급. */
    public UploadTicketResponse createUploadSas(String contentType) {
        String normalized = contentType == null ? "" : contentType.trim().toLowerCase();
        String extension = CONTENT_TYPE_EXTENSION.get(normalized);
        if (extension == null || !allowedContentTypes.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_TYPE, "Unsupported content type: " + contentType);
        }

        String storageKey = "menu-" + UUID.randomUUID() + "." + extension;
        BlobClient blob = containerClient.getBlobClient(storageKey);
        OffsetDateTime expiry = OffsetDateTime.now().plus(uploadSasTtl);
        BlobSasPermission permission =
                new BlobSasPermission().setCreatePermission(true).setWritePermission(true);

        String sas = generateSas(blob, expiry, permission);
        return new UploadTicketResponse(
                storageKey, blob.getBlobUrl() + "?" + sas, blob.getBlobUrl(), expiry.toInstant());
    }

    /** A, B 공유 코어: storageKey 로 읽기 SAS URL 발급 (AI OCR 이 private blob 을 읽도록). */
    public String createReadSasUrl(String storageKey) {
        String key = validateKey(storageKey);
        BlobClient blob = containerClient.getBlobClient(key);
        OffsetDateTime expiry = OffsetDateTime.now().plus(readSasTtl);
        BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);

        String sas = generateSas(blob, expiry, permission);
        return blob.getBlobUrl() + "?" + sas;
    }

    /** SAS 없는 평문 blob URL (DB 저장용 — SAS 는 휘발성이라 미저장). */
    public String blobUrl(String storageKey) {
        return containerClient.getBlobClient(validateKey(storageKey)).getBlobUrl();
    }

    /** A, B 공유 코어: storageKey 또는 imageUrl(Path A) 을 받아 blob 명으로 정규화. 우리 컨테이너 외 URL 은 거부(SSRF 가드). */
    public String extractStorageKey(String storageKeyOrUrl) {
        if (storageKeyOrUrl == null || storageKeyOrUrl.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_STORAGE_KEY, "storageKey/imageUrl is blank.");
        }
        String value = storageKeyOrUrl.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            String prefix = containerClient.getBlobContainerUrl() + "/";
            if (!value.startsWith(prefix)) {
                throw new BusinessException(ErrorCode.INVALID_STORAGE_KEY, "URL is outside the managed container.");
            }
            String afterPrefix = value.substring(prefix.length());
            int query = afterPrefix.indexOf('?');
            value = query >= 0 ? afterPrefix.substring(0, query) : afterPrefix;
        }
        return validateKey(value);
    }

    private String validateKey(String key) {
        if (key == null || !STORAGE_KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException(ErrorCode.INVALID_STORAGE_KEY, "Invalid storage key: " + key);
        }
        return key;
    }

    private String generateSas(BlobClient blob, OffsetDateTime expiry, BlobSasPermission permission) {
        try {
            return blob.generateSas(new BlobServiceSasSignatureValues(expiry, permission));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.BLOB_SAS_ERROR, "Failed to sign blob SAS.", exception);
        }
    }
}
