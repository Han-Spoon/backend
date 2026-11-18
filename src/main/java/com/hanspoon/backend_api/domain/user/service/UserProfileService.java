package com.hanspoon.backend_api.domain.user.service;

import com.hanspoon.backend_api.domain.consent.service.ConsentService;
import com.hanspoon.backend_api.domain.user.dto.ProfilePatchRequest;
import com.hanspoon.backend_api.domain.user.dto.ProfileRequest;
import com.hanspoon.backend_api.domain.user.dto.ProfileResponse;
import com.hanspoon.backend_api.domain.user.entity.AllergyCode;
import com.hanspoon.backend_api.domain.user.entity.ReligionType;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.entity.UserProfile;
import com.hanspoon.backend_api.domain.user.entity.VegetarianType;
import com.hanspoon.backend_api.domain.user.repository.UserProfileRepository;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ConsentService consentService;

    public UserProfileService(
            UserRepository userRepository, UserProfileRepository userProfileRepository, ConsentService consentService) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.consentService = consentService;
    }

    @Transactional
    public ProfileResponse createProfile(UUID userId, ProfileRequest request) {
        consentService.requireSensitiveConsent(userId);
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (userProfileRepository.existsByUserId(userId)) {
            throw new BusinessException(ErrorCode.PROFILE_ALREADY_EXISTS);
        }
        validate(request);

        user.changeLanguage(request.languageCode());
        UserProfile profile = userProfileRepository.save(UserProfile.create(
                userId,
                normalizeNationality(request.nationality()),
                request.isFirstTime(),
                request.isVegan(),
                resolveVeganType(request),
                resolveReligionType(request),
                request.noSpicy(),
                request.noAlcohol(),
                resolveAllergies(request)));
        return ProfileResponse.of(profile);
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        UserProfile profile = userProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));
        return ProfileResponse.of(profile);
    }

    /** 부분 수정. 보낸 필드만 저장값과 합친 뒤 조건 규칙 검사. */
    @Transactional
    public ProfileResponse updateProfile(UUID userId, ProfilePatchRequest request) {
        consentService.requireSensitiveConsent(userId);
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserProfile profile = userProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));

        boolean vegan = orElse(request.isVegan(), profile.getVegetarian());
        VegetarianType veganType = vegan ? orElse(request.veganType(), profile.getVegetarianType()) : null;
        if (vegan && veganType == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "veganType is required when isVegan is true.");
        }

        ReligionType currentReligion = profile.getReligionType();
        boolean religion =
                orElse(request.hasReligion(), currentReligion != null && currentReligion != ReligionType.NONE);
        ReligionType religionType = religion ? orElse(request.religionType(), currentReligion) : null;
        if (religion && (religionType == null || religionType == ReligionType.NONE)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "religionType is required when hasReligion is true.");
        }

        List<AllergyCode> currentAllergies = profile.getAllergies();
        boolean allergic = orElse(request.hasAllergies(), !currentAllergies.isEmpty());
        List<AllergyCode> allergies = allergic ? orElse(request.allergies(), currentAllergies) : List.of();
        if (allergic && allergies.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "allergies are required when hasAllergies is true.");
        }

        user.changeLanguage(request.languageCode());
        profile.update(
                request.nationality() == null ? profile.getNationality() : normalizeNationality(request.nationality()),
                orElse(request.isFirstTime(), profile.getFirstTime()),
                vegan,
                veganType,
                religionType,
                orElse(request.noSpicy(), profile.getNoSpicy()),
                orElse(request.noAlcohol(), profile.getNoAlcohol()),
                allergies);
        return ProfileResponse.of(profile);
    }

    /** 교차검증: 플래그가 true 인데 상세값이 없으면 INVALID_REQUEST. */
    private void validate(ProfileRequest request) {
        if (Boolean.TRUE.equals(request.isVegan()) && request.veganType() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "veganType is required when isVegan is true.");
        }
        if (Boolean.TRUE.equals(request.hasReligion())
                && (request.religionType() == null || request.religionType() == ReligionType.NONE)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "religionType is required when hasReligion is true.");
        }
        if (Boolean.TRUE.equals(request.hasAllergies())
                && (request.allergies() == null || request.allergies().isEmpty())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "allergies are required when hasAllergies is true.");
        }
    }

    /** 국가 코드 대문자 정규화(ISO 3166-1 alpha-2 는 대문자). 검증은 @CountryCode 가 담당. */
    private String normalizeNationality(String nationality) {
        return nationality == null ? null : nationality.trim().toUpperCase(Locale.ROOT);
    }

    private static <T> T orElse(T value, T current) {
        return value != null ? value : current;
    }

    private VegetarianType resolveVeganType(ProfileRequest request) {
        return Boolean.TRUE.equals(request.isVegan()) ? request.veganType() : null;
    }

    private ReligionType resolveReligionType(ProfileRequest request) {
        return Boolean.TRUE.equals(request.hasReligion()) ? request.religionType() : null;
    }

    /** hasAllergies=false 면 "없음"(빈 목록). 정렬·중복 제거는 엔티티가 한다. */
    private List<AllergyCode> resolveAllergies(ProfileRequest request) {
        return Boolean.TRUE.equals(request.hasAllergies()) ? request.allergies() : List.of();
    }
}
