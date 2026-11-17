package com.hanspoon.backend_api.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hanspoon.backend_api.domain.consent.service.ConsentService;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ConsentService consentService;

    @InjectMocks
    private UserProfileService userProfileService;

    private ProfileRequest sampleRequest() {
        return new ProfileRequest(
                "KR",
                "ko",
                true,
                false,
                null,
                true,
                ReligionType.HALAL,
                true,
                List.of(AllergyCode.EGG, AllergyCode.MILK),
                false,
                true);
    }

    @Test
    void createProfile_success_persistsProfileAndAllergies() {
        User user = User.create("me@example.com", "Me", "en");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userProfileRepository.existsByUserId(user.getId())).thenReturn(false);
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileResponse response = userProfileService.createProfile(user.getId(), sampleRequest());

        assertThat(user.getLanguageCode()).isEqualTo("ko"); // 언어 동기화
        assertThat(response.nationality()).isEqualTo("KR");
        assertThat(response.hasReligion()).isTrue();
        assertThat(response.religionType()).isEqualTo(ReligionType.HALAL);
        assertThat(response.hasAllergies()).isTrue();
        assertThat(response.allergies()).containsExactly(AllergyCode.EGG, AllergyCode.MILK);
    }

    @Test
    void createProfile_storesAllergiesSortedAndDistinct() {
        User user = User.create("me@example.com", "Me", "en");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userProfileRepository.existsByUserId(user.getId())).thenReturn(false);
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        ProfileRequest request = new ProfileRequest(
                "KR",
                "ko",
                true,
                false,
                null,
                false,
                null,
                true,
                List.of(AllergyCode.SHRIMP, AllergyCode.EGG, AllergyCode.SHRIMP, AllergyCode.PINE_NUT),
                false,
                false);

        ProfileResponse response = userProfileService.createProfile(user.getId(), request);

        // ck_user_profiles_allergy_codes: code 오름차순(egg < pinenut < shrimp) + 중복 없음
        assertThat(response.allergies()).containsExactly(AllergyCode.EGG, AllergyCode.PINE_NUT, AllergyCode.SHRIMP);
    }

    @Test
    void createProfile_withoutAllergies_storesEmptyEvenIfListSent() {
        User user = User.create("me@example.com", "Me", "en");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userProfileRepository.existsByUserId(user.getId())).thenReturn(false);
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        ProfileRequest request = new ProfileRequest(
                "KR", "ko", true, false, null, false, null, false, List.of(AllergyCode.EGG), false, false);

        ProfileResponse response = userProfileService.createProfile(user.getId(), request);

        assertThat(response.hasAllergies()).isFalse();
        assertThat(response.allergies()).isEmpty();
    }

    @Test
    void createProfile_whenAlreadyExists_throwsConflict() {
        User user = User.create("me@example.com", "Me", "en");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userProfileRepository.existsByUserId(user.getId())).thenReturn(true);

        assertThatThrownBy(() -> userProfileService.createProfile(user.getId(), sampleRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PROFILE_ALREADY_EXISTS);
    }

    @Test
    void createProfile_veganWithoutType_throwsInvalidRequest() {
        User user = User.create("me@example.com", "Me", "en");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userProfileRepository.existsByUserId(user.getId())).thenReturn(false);

        ProfileRequest invalid =
                new ProfileRequest("US", "en", true, true, null, false, null, false, List.of(), false, false);

        assertThatThrownBy(() -> userProfileService.createProfile(user.getId(), invalid))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void getProfile_whenMissing_throwsProfileNotFound() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getProfile(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PROFILE_NOT_FOUND);
    }

    @Test
    void getProfile_returnsProfileWithAllergies() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(
                userId, "KR", true, true, VegetarianType.VEGAN, null, true, false, List.of(AllergyCode.CRAB));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        ProfileResponse response = userProfileService.getProfile(userId);

        assertThat(response.isVegan()).isTrue();
        assertThat(response.veganType()).isEqualTo(VegetarianType.VEGAN);
        assertThat(response.hasReligion()).isFalse();
        assertThat(response.allergies()).containsExactly(AllergyCode.CRAB);
    }

    @Test
    void updateProfile_replacesAllergiesAndSyncsLanguage() {
        UUID userId = UUID.randomUUID();
        User user = User.create("me@example.com", "Me", "en");
        UserProfile profile =
                UserProfile.create(userId, "US", false, false, null, null, false, false, List.of(AllergyCode.CRAB));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        ProfileResponse response = userProfileService.updateProfile(userId, sampleRequest());

        assertThat(user.getLanguageCode()).isEqualTo("ko");
        assertThat(profile.getNationality()).isEqualTo("KR");
        assertThat(response.allergies()).containsExactly(AllergyCode.EGG, AllergyCode.MILK);
        assertThat(profile.getAllergies()).containsExactly(AllergyCode.EGG, AllergyCode.MILK);
    }
}
