package com.hanspoon.backend_api.domain.user.service;

import com.hanspoon.backend_api.domain.user.dto.ProfileRequest;
import com.hanspoon.backend_api.domain.user.dto.ProfileResponse;
import com.hanspoon.backend_api.domain.user.entity.AllergyCode;
import com.hanspoon.backend_api.domain.user.entity.ReligionType;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.entity.UserAllergy;
import com.hanspoon.backend_api.domain.user.entity.UserProfile;
import com.hanspoon.backend_api.domain.user.entity.VegetarianType;
import com.hanspoon.backend_api.domain.user.repository.UserAllergyRepository;
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
    private final UserAllergyRepository userAllergyRepository;

    public UserProfileService(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            UserAllergyRepository userAllergyRepository) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.userAllergyRepository = userAllergyRepository;
    }

    @Transactional
    public ProfileResponse createProfile(UUID userId, ProfileRequest request) {
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
                request.noAlcohol()));

        List<AllergyCode> allergies = replaceAllergies(profile.getId(), request);
        return ProfileResponse.of(profile, allergies);
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        UserProfile profile = userProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));
        List<AllergyCode> allergies = userAllergyRepository.findByUserProfileId(profile.getId()).stream()
                .map(UserAllergy::getAllergyCode)
                .toList();
        return ProfileResponse.of(profile, allergies);
    }

    @Transactional
    public ProfileResponse updateProfile(UUID userId, ProfileRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserProfile profile = userProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));
        validate(request);

        user.changeLanguage(request.languageCode());
        profile.update(
                normalizeNationality(request.nationality()),
                request.isFirstTime(),
                request.isVegan(),
                resolveVeganType(request),
                resolveReligionType(request),
                request.noSpicy(),
                request.noAlcohol());

        List<AllergyCode> allergies = replaceAllergies(profile.getId(), request);
        return ProfileResponse.of(profile, allergies);
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

    private VegetarianType resolveVeganType(ProfileRequest request) {
        return Boolean.TRUE.equals(request.isVegan()) ? request.veganType() : null;
    }

    private ReligionType resolveReligionType(ProfileRequest request) {
        return Boolean.TRUE.equals(request.hasReligion()) ? request.religionType() : null;
    }

    /** 기존 알레르기를 전부 지우고 요청값으로 재삽입(중복 제거). 저장된 목록을 반환. */
    private List<AllergyCode> replaceAllergies(UUID profileId, ProfileRequest request) {
        userAllergyRepository.deleteByUserProfileId(profileId);
        if (!Boolean.TRUE.equals(request.hasAllergies()) || request.allergies() == null) {
            return List.of();
        }
        List<AllergyCode> codes = request.allergies().stream().distinct().toList();
        codes.forEach(code -> userAllergyRepository.save(UserAllergy.of(profileId, code)));
        return codes;
    }
}
