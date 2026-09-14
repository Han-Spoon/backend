ALTER TABLE menu_images
    ADD COLUMN object_version_id VARCHAR(1024) NULL,
    ADD COLUMN etag VARCHAR(255) NULL;

COMMENT ON COLUMN menu_images.object_version_id IS
    'S3 HeadObject 검증 시점의 객체 버전 ID. 실제 OCR 분석 대상을 식별한다.';
COMMENT ON COLUMN menu_images.etag IS
    'S3 HeadObject 검증 시점의 ETag. 업로드 검증 객체의 동일성 확인에 사용한다.';
