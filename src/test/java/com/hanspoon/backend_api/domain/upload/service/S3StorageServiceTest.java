package com.hanspoon.backend_api.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hanspoon.backend_api.domain.upload.dto.UploadTicketResponse;
import com.hanspoon.backend_api.domain.upload.dto.VerifiedUpload;
import com.hanspoon.backend_api.global.config.S3Properties;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3StorageServiceTest {

    private static final String BUCKET = "hanspoon-test-images";
    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final S3Properties properties = new S3Properties(
            BUCKET, "ap-northeast-2", null, Duration.ofMinutes(10), Duration.ofMinutes(15), 10 * 1024 * 1024);

    private final S3StorageService service = new S3StorageService(presigner(), mock(S3Client.class), properties);

    private static S3Presigner presigner() {
        return S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-ak", "test-sk")))
                .build();
    }

    @Test
    void createUploadUrlIssuesPresignedPut() {
        UploadTicketResponse ticket = service.createUploadUrl(USER, "image/jpeg");

        assertThat(ticket.storageKey()).matches("^scans/" + USER + "/[0-9a-f-]{36}\\.jpg$");
        assertThat(ticket.uploadUrl())
                .contains(BUCKET)
                .contains("X-Amz-Signature=")
                .contains("X-Amz-Expires=");
        assertThat(ticket.expiresAt()).isNotNull();
        assertThat(ticket.uploadHeaders())
                .containsEntry("Content-Type", "image/jpeg")
                .containsEntry("If-None-Match", "*");
    }

    @Test
    void createUploadUrlRejectsUnsupportedContentType() {
        assertThatThrownBy(() -> service.createUploadUrl(USER, "image/gif"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CONTENT_TYPE);
    }

    @Test
    void resolveKeyAcceptsOwnKey() {
        String key = service.createUploadUrl(USER, "image/png").storageKey();

        assertThat(service.resolveKey(USER, key)).isEqualTo(key);
    }

    @Test
    void resolveKeyRejectsOtherUsersKey() {
        String key = service.createUploadUrl(OTHER_USER, "image/png").storageKey();

        assertThatThrownBy(() -> service.resolveKey(USER, key))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void resolveKeyRejectsMalformedKeys() {
        assertThatThrownBy(() -> service.resolveKey(USER, "menus/foo.jpg")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.resolveKey(USER, "scans/" + USER + "/../secret.jpg"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.resolveKey(USER, "scans/" + USER + "/a.gif"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.resolveKey(USER, "https://evil.com/scans/x.jpg"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.resolveKey(USER, "")).isInstanceOf(BusinessException.class);
    }

    @Test
    void verifyUploadObjectReturnsVersionAndEtagFromHeadObject() {
        S3Client s3Client = mock(S3Client.class);
        S3StorageService storageService = new S3StorageService(presigner(), s3Client, properties);
        String key = "scans/" + USER + "/abc.jpg";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .versionId("version-1")
                        .eTag("\"etag-1\"")
                        .contentLength(123L)
                        .contentType("image/jpeg")
                        .build());

        VerifiedUpload upload = storageService.verifyUploadObject(key);

        assertThat(upload.storageKey()).isEqualTo(key);
        assertThat(upload.versionId()).isEqualTo("version-1");
        assertThat(upload.eTag()).isEqualTo("\"etag-1\"");
        assertThat(upload.contentLength()).isEqualTo(123L);
        assertThat(upload.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void verifyUploadObjectRejectsOversizedObject() {
        S3Client s3Client = mock(S3Client.class);
        S3StorageService storageService = new S3StorageService(presigner(), s3Client, properties);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength(properties.maxFileSize() + 1)
                        .contentType("image/jpeg")
                        .build());

        assertThatThrownBy(() -> storageService.verifyUploadObject("scans/" + USER + "/large.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void verifyUploadObjectRejectsUnexpectedContentType() {
        S3Client s3Client = mock(S3Client.class);
        S3StorageService storageService = new S3StorageService(presigner(), s3Client, properties);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength(123L)
                        .contentType("image/gif")
                        .build());

        assertThatThrownBy(() -> storageService.verifyUploadObject("scans/" + USER + "/fake.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CONTENT_TYPE);
    }

    @Test
    void verifyUploadObjectMapsMissingObject() {
        S3Client s3Client = mock(S3Client.class);
        S3StorageService storageService = new S3StorageService(presigner(), s3Client, properties);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(
                        S3Exception.builder().statusCode(404).message("missing").build());

        assertThatThrownBy(() -> storageService.verifyUploadObject("scans/" + USER + "/missing.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_NOT_FOUND);
    }

    @Test
    void objectUriReturnsS3Scheme() {
        assertThat(service.objectUri("scans/" + USER + "/abc.jpg"))
                .isEqualTo("s3://" + BUCKET + "/scans/" + USER + "/abc.jpg");
    }

    @Test
    void createReadUrlPinsFallbackToTheVerifiedVersion() {
        String key = "scans/" + USER + "/abc.jpg";

        assertThat(service.createReadUrl(key, "version-1"))
                .contains(key)
                .contains("versionId=version-1")
                .contains("X-Amz-Signature=");
    }
}
