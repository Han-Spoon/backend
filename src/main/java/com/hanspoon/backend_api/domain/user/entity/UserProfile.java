package com.hanspoon.backend_api.domain.user.entity;

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
 * 온보딩 프로필. users 와 1:1 (FK ON DELETE CASCADE). 언어는 users.language_code 가 단일 출처라 여기엔 두지 않는다.
 * 알레르기는 별도 테이블({@link UserAllergy})로 분리.
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

    private UserProfile(
            UUID userId,
            String nationality,
            Boolean firstTime,
            Boolean vegetarian,
            VegetarianType vegetarianType,
            ReligionType religionType,
            Boolean noSpicy,
            Boolean noAlcohol) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.nationality = nationality;
        this.firstTime = firstTime;
        this.vegetarian = vegetarian;
        this.vegetarianType = vegetarianType;
        this.religionType = religionType;
        this.noSpicy = noSpicy;
        this.noAlcohol = noAlcohol;
    }

    public static UserProfile create(
            UUID userId,
            String nationality,
            Boolean firstTime,
            Boolean vegetarian,
            VegetarianType vegetarianType,
            ReligionType religionType,
            Boolean noSpicy,
            Boolean noAlcohol) {
        return new UserProfile(
                userId, nationality, firstTime, vegetarian, vegetarianType, religionType, noSpicy, noAlcohol);
    }

    public void update(
            String nationality,
            Boolean firstTime,
            Boolean vegetarian,
            VegetarianType vegetarianType,
            ReligionType religionType,
            Boolean noSpicy,
            Boolean noAlcohol) {
        this.nationality = nationality;
        this.firstTime = firstTime;
        this.vegetarian = vegetarian;
        this.vegetarianType = vegetarianType;
        this.religionType = religionType;
        this.noSpicy = noSpicy;
        this.noAlcohol = noAlcohol;
    }
}
