-- V13.1 LOCALDATA 가게 식별자. 상가정보에 없는 가게를 지자체 일반음식점 인허가 데이터로 보완
-- V14(축소 단계) 번호를 비워 두기 위한 소수 버전

ALTER TABLE stores ADD COLUMN localdata_mgmt_no VARCHAR(22) NULL;

ALTER TABLE stores
    ADD CONSTRAINT uq_stores_localdata_mgmt_no UNIQUE (localdata_mgmt_no),
    -- 인허가 관리번호 형식: 개방자치단체코드-업종-연도-일련번호
    ADD CONSTRAINT ck_stores_localdata_mgmt_no CHECK (
        localdata_mgmt_no IS NULL OR localdata_mgmt_no ~ '^[0-9]{7}-[0-9]{3}-[0-9]{4}-[0-9]{5}$'),
    ADD CONSTRAINT ck_stores_localdata_identity CHECK ((origin = 'localdata') = (localdata_mgmt_no IS NOT NULL));

COMMENT ON COLUMN stores.localdata_mgmt_no IS 'LOCALDATA 관리번호 | 인허가 데이터 멱등 키; sbiz는 NULL';
