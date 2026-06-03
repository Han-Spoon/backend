-- User allergies (V2)
-- ERD user_allergies. 프로필별 알레르기 N(N>=0)개 선택. allergy_code = AllergyCode enum 소문자 code (예: egg/milk/shrimp/alcohol).
-- UUID는 애플리케이션에서 생성(UUID.randomUUID()).

CREATE TABLE user_allergies (
    id               UUID        NOT NULL,
    user_profile_id  UUID        NOT NULL,
    allergy_code     VARCHAR(50) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_allergies PRIMARY KEY (id),
    CONSTRAINT uq_user_allergies UNIQUE (user_profile_id, allergy_code),
    CONSTRAINT fk_user_allergies_profile FOREIGN KEY (user_profile_id)
        REFERENCES user_profiles (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_allergies_profile ON user_allergies (user_profile_id);
