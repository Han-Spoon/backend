package com.hanspoon.backend_api.domain.user.entity;

import com.hanspoon.backend_api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 온보딩 프로필. users 와 1:1 (FK ON DELETE CASCADE). 언어는 users.language_code 가 단일 출처라 여기엔 두지 않는다.
 * 알레르기는 allergy_codes 배열(식약처 19종 소문자 code, 정렬·중복 없음 — DB CHECK)로 프로필과 함께 저장한다.
 */
@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile extends BaseEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "nationality", length = 2)
    private String nationality;

    @Column(name = "is_first_time_korean_food")
    private Boolean firstTime;

    @Column(name = "is_vegetarian")
    private Boolean vegetarian;

    @Column(name = "vegetarian_type", length = 50)
    private VegetarianType vegetarianType;

    @Column(name = "religion_type", length = 50)
    private ReligionType religionType;

    @Column(name = "no_spicy")
    private Boolean noSpicy;

    @Column(name = "no_alcohol")
    private Boolean noAlcohol;

    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allergy_codes", columnDefinition = "varchar(20)[]", nullable = false)
    private String[] allergyCodes;

    private UserProfile(
            UUID userId,
            String nationality,
            Boolean firstTime,
            Boolean vegetarian,
            VegetarianType vegetarianType,
            ReligionType religionType,
            Boolean noSpicy,
            Boolean noAlcohol,
            List<AllergyCode> allergies) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.nationality = nationality;
        this.firstTime = firstTime;
        this.vegetarian = vegetarian;
        this.vegetarianType = vegetarianType;
        this.religionType = religionType;
        this.noSpicy = noSpicy;
        this.noAlcohol = noAlcohol;
        this.allergyCodes = toCodes(allergies);
    }

    public static UserProfile create(
            UUID userId,
            String nationality,
            Boolean firstTime,
            Boolean vegetarian,
            VegetarianType vegetarianType,
            ReligionType religionType,
            Boolean noSpicy,
            Boolean noAlcohol,
            List<AllergyCode> allergies) {
        return new UserProfile(
                userId,
                nationality,
                firstTime,
                vegetarian,
                vegetarianType,
                religionType,
                noSpicy,
                noAlcohol,
                allergies);
    }

    public void update(
            String nationality,
            Boolean firstTime,
            Boolean vegetarian,
            VegetarianType vegetarianType,
            ReligionType religionType,
            Boolean noSpicy,
            Boolean noAlcohol,
            List<AllergyCode> allergies) {
        this.nationality = nationality;
        this.firstTime = firstTime;
        this.vegetarian = vegetarian;
        this.vegetarianType = vegetarianType;
        this.religionType = religionType;
        this.noSpicy = noSpicy;
        this.noAlcohol = noAlcohol;
        this.allergyCodes = toCodes(allergies);
    }

    /** 저장된 알레르기(code 오름차순). 빈 목록은 "알레르기 없음"으로 응답한 상태. */
    public List<AllergyCode> getAllergies() {
        return Arrays.stream(allergyCodes).map(AllergyCode::fromCode).toList();
    }

    /**
     * ck_user_profiles_allergy_codes 에 맞춰 중복 제거 + code 오름차순(코드포인트, DB COLLATE "C" 와 동일).
     * null 을 "없음"으로 바꾸지 않는다(FN-safe): 없음은 호출부가 빈 목록으로 명시한다.
     */
    private static String[] toCodes(List<AllergyCode> allergies) {
        Objects.requireNonNull(allergies, "allergies");
        return allergies.stream().map(AllergyCode::getCode).distinct().sorted().toArray(String[]::new);
    }
}
