package com.hanspoon.backend_api.global.config;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Azure Blob 컨테이너 클라이언트. 계정키 기반 {@link StorageSharedKeyCredential} 로 SAS 를 로컬 서명한다(네트워크 불필요).
 * 클라이언트 생성은 지연(lazy)이라 부팅 시 네트워크 호출이 없다 → 로컬에서 키 없이도 기동 가능.
 * (하드닝 후속: 계정키 대신 Managed Identity + User Delegation SAS)
 */
@Configuration
public class BlobStorageConfig {

    // 로컬/테스트에서 키 없이도 기동 가능하도록 placeholder(base64). 실제 SAS 발급에는 운영의 BLOB_ACCOUNT_KEY(Key Vault) 필요.
    private static final String PLACEHOLDER_KEY = "ZGV2LXBsYWNlaG9sZGVy"; // base64("dev-placeholder")

    @Bean
    public BlobContainerClient menuImageContainerClient(
            @Value("${app.blob.account-name}") String accountName,
            @Value("${app.blob.account-key:}") String accountKey,
            @Value("${app.blob.container}") String container) {

        String effectiveKey = (accountKey == null || accountKey.isBlank()) ? PLACEHOLDER_KEY : accountKey;
        return new BlobServiceClientBuilder()
                .endpoint("https://" + accountName + ".blob.core.windows.net")
                .credential(new StorageSharedKeyCredential(accountName, effectiveKey))
                .buildClient()
                .getBlobContainerClient(container);
    }
}
