package com.hanspoon.backend_api.domain.scan.entity;

import com.hanspoon.backend_api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 스캔 이미지 레코드. scan_sessions 와 1:1 (FK ON DELETE CASCADE). OCR 결과의 menu_image 매핑.
 *
 * <p>scan_quality(blur/brightness/재촬영 사유 등)는 OCR 가능/불가 판단에만 쓰이는 일회성 값이라 저장하지 않는다.
 * 성공/실패/재촬영 신호는 {@link ScanSession#getScanStatus()} 가 단일 출처다.
 */
@Entity
@Table(name = "menu_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuImage extends BaseEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "scan_session_id", columnDefinition = "uuid", nullable = false, unique = true)
    private UUID scanSessionId;

    @Column(name = "source", length = 20)
    private String source;

    @Column(name = "storage_key", length = 512)
    private String storageKey;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    private MenuImage(
            UUID scanSessionId, String source, String storageKey, String imageUrl, String mimeType, Long fileSize) {
        this.id = UUID.randomUUID();
        this.scanSessionId = scanSessionId;
        this.source = source;
        this.storageKey = storageKey;
        this.imageUrl = imageUrl;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
    }

    public static MenuImage create(
            UUID scanSessionId, String source, String storageKey, String imageUrl, String mimeType, Long fileSize) {
        return new MenuImage(scanSessionId, source, storageKey, imageUrl, mimeType, fileSize);
    }
}
