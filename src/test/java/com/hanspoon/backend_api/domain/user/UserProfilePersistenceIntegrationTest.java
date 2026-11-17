package com.hanspoon.backend_api.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import com.hanspoon.backend_api.domain.user.entity.AllergyCode;
import com.hanspoon.backend_api.domain.user.entity.ReligionType;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.entity.UserProfile;
import com.hanspoon.backend_api.domain.user.repository.UserProfileRepository;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** user_profiles.allergy_codes(varchar[]) 배열 매핑 라운드트립 + ck_user_profiles_allergy_codes 정합 검증. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class UserProfilePersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void persistsAllergiesAsSortedArrayAndReplacesThemOnUpdate() {
        User user = userRepository.save(User.create("allergy@example.com", "tester", "ko"));
        UserProfile profile = userProfileRepository.save(UserProfile.create(
                user.getId(),
                "KR",
                false,
                false,
                null,
                ReligionType.HALAL,
                false,
                true,
                List.of(AllergyCode.SHRIMP, AllergyCode.EGG, AllergyCode.PINE_NUT)));
        entityManager.flush();
        entityManager.clear();

        assertThat(jdbcTemplate.queryForObject(
                        "select array_to_string(allergy_codes, ',') from user_profiles where id = ?",
                        String.class,
                        profile.getId()))
                .isEqualTo("egg,pinenut,shrimp");
        UserProfile reloaded = userProfileRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(reloaded.getAllergies()).containsExactly(AllergyCode.EGG, AllergyCode.PINE_NUT, AllergyCode.SHRIMP);

        reloaded.update("KR", false, false, null, ReligionType.HALAL, false, true, List.of());
        entityManager.flush();
        entityManager.clear();

        assertThat(userProfileRepository
                        .findByUserId(user.getId())
                        .orElseThrow()
                        .getAllergies())
                .isEmpty();
    }
}
