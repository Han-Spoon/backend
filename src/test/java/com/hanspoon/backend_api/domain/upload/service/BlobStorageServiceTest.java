package com.hanspoon.backend_api.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.hanspoon.backend_api.domain.upload.dto.UploadTicketResponse;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 더미 base64 계정키로 SAS 를 오프라인 서명 → 네트워크 없이 검증. */
class BlobStorageServiceTest {

    private static final String ACCOUNT = "testacct";
    private static final String CONTAINER = "board-images";
    private static final String CONTAINER_URL = "https://testacct.blob.core.windows.net/board-images";
    // base64("storage-account-key")
    private static final String DUMMY_KEY = "c3RvcmFnZS1hY2NvdW50LWtleQ==";

    private final BlobStorageService service = new BlobStorageService(
            containerClient(),
            Duration.ofMinutes(10),
            Duration.ofMinutes(15),
            List.of("image/jpeg", "image/png", "image/webp"));

    private static BlobContainerClient containerClient() {
        return new BlobServiceClientBuilder()
                .endpoint("https://" + ACCOUNT + ".blob.core.windows.net")
                .credential(new StorageSharedKeyCredential(ACCOUNT, DUMMY_KEY))
                .buildClient()
                .getBlobContainerClient(CONTAINER);
    }

    @Test
    void createUploadSasIssuesWriteSas() {
        UploadTicketResponse ticket = service.createUploadSas("image/jpeg");

        assertThat(ticket.storageKey()).matches("^menu-[A-Za-z0-9-]+\\.jpg$");
        assertThat(ticket.uploadUrl())
                .startsWith(CONTAINER_URL + "/" + ticket.storageKey() + "?")
                .contains("sig=")
                .contains("se=");
        assertThat(ticket.readUrl()).isEqualTo(CONTAINER_URL + "/" + ticket.storageKey());
        assertThat(ticket.readUrl()).doesNotContain("?");
        assertThat(ticket.expiresAt()).isNotNull();
    }

    @Test
    void createUploadSasRejectsUnsupportedContentType() {
        assertThatThrownBy(() -> service.createUploadSas("image/gif"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CONTENT_TYPE);
    }

    @Test
    void createReadSasUrlIssuesReadSas() {
        String url = service.createReadSasUrl("menu-abc123.jpg");

        assertThat(url)
                .startsWith(CONTAINER_URL + "/menu-abc123.jpg?")
                .contains("sp=r")
                .contains("sig=");
    }

    @Test
    void createReadSasUrlRejectsInvalidKey() {
        assertThatThrownBy(() -> service.createReadSasUrl("../secret.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STORAGE_KEY);
        assertThatThrownBy(() -> service.createReadSasUrl("menu-x.gif")).isInstanceOf(BusinessException.class);
    }

    @Test
    void extractStorageKeyAcceptsPlainKey() {
        assertThat(service.extractStorageKey("menu-abc123.png")).isEqualTo("menu-abc123.png");
    }

    @Test
    void extractStorageKeyAcceptsOurContainerUrlWithSas() {
        String url = CONTAINER_URL + "/menu-abc123.jpg?sv=2024&sig=xyz";

        assertThat(service.extractStorageKey(url)).isEqualTo("menu-abc123.jpg");
    }

    @Test
    void extractStorageKeyRejectsForeignHost() {
        assertThatThrownBy(() -> service.extractStorageKey("https://evil.com/board-images/menu-abc123.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STORAGE_KEY);
    }

    @Test
    void extractStorageKeyRejectsOtherContainer() {
        assertThatThrownBy(
                        () -> service.extractStorageKey("https://testacct.blob.core.windows.net/other/menu-abc123.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STORAGE_KEY);
    }
}
