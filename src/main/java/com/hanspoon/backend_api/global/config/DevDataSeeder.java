package com.hanspoon.backend_api.global.config;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.ai.dto.result.FinalMessage;
import com.hanspoon.backend_api.domain.ai.dto.result.OwnerCard;
import com.hanspoon.backend_api.domain.ai.dto.result.OwnerQuestion;
import com.hanspoon.backend_api.domain.card.dto.CardText;
import com.hanspoon.backend_api.domain.card.entity.CardType;
import com.hanspoon.backend_api.domain.card.entity.SavedCard;
import com.hanspoon.backend_api.domain.card.repository.SavedCardRepository;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.user.entity.AllergyCode;
import com.hanspoon.backend_api.domain.user.entity.ReligionType;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.entity.UserAllergy;
import com.hanspoon.backend_api.domain.user.entity.UserProfile;
import com.hanspoon.backend_api.domain.user.repository.UserAllergyRepository;
import com.hanspoon.backend_api.domain.user.repository.UserProfileRepository;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import com.hanspoon.backend_api.global.security.JwtTokenProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 Swagger 수동 테스트용 더미 데이터 시더. 데모 유저 + 다양한 상태의 스캔 이력을 멱등하게 넣고,
 * 그 유저로 인증할 수 있는 access token 을 로그에 출력한다.
 *
 * <p>실행: {@code ./gradlew bootRun --args='--app.seed.enabled=true'} (local 프로파일 + 플래그가 모두 켜질 때만 동작).
 * 기본값이 꺼져 있어 테스트(@SpringBootTest)에는 로드되지 않는다.
 */
@Component
@Profile("local")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final String DEMO_EMAIL = "demo@han-spoon.com";

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserAllergyRepository userAllergyRepository;
    private final ScanSessionRepository scanSessionRepository;
    private final MenuAnalysisRepository menuAnalysisRepository;
    private final SavedCardRepository savedCardRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public DevDataSeeder(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            UserAllergyRepository userAllergyRepository,
            ScanSessionRepository scanSessionRepository,
            MenuAnalysisRepository menuAnalysisRepository,
            SavedCardRepository savedCardRepository,
            JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.userAllergyRepository = userAllergyRepository;
        this.scanSessionRepository = scanSessionRepository;
        this.menuAnalysisRepository = menuAnalysisRepository;
        this.savedCardRepository = savedCardRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User user = userRepository.findByEmail(DEMO_EMAIL).orElse(null);
        if (user == null) {
            user = userRepository.save(User.create(DEMO_EMAIL, "데모유저", "ko"));
            UserProfile profile = userProfileRepository.save(
                    UserProfile.create(user.getId(), "SA", false, false, null, ReligionType.HALAL, false, true));
            userAllergyRepository.saveAll(List.of(
                    UserAllergy.of(profile.getId(), AllergyCode.PEANUT),
                    UserAllergy.of(profile.getId(), AllergyCode.SHRIMP),
                    UserAllergy.of(profile.getId(), AllergyCode.EGG)));
            seedScans(user.getId());
            seedCards(user.getId());
            log.info("[seed] demo user + profile(allergies) + scan history + saved cards created");
        } else {
            log.info("[seed] demo user already exists ({}), skip insert", user.getId());
        }

        String token = jwtTokenProvider.createAccessToken(user.getId());
        log.info(
                """

                ===================== DEV SEED =====================
                 userId : {}
                 email  : {}
                 만료    : {}분 (재실행 시 갱신)

                 Swagger → Authorize 에 아래 토큰 붙여넣기:
                 {}
                ====================================================
                """,
                user.getId(),
                DEMO_EMAIL,
                jwtTokenProvider.getAccessTokenExpiration().toMinutes(),
                token);
    }

    private void seedScans(UUID userId) {
        Instant now = Instant.now();

        // 1) COMPLETED · 커스텀 제목 · 메뉴 2건(danger + caution+ownerCard)
        ScanSession s1 = scanSessionRepository.save(
                ScanSession.create(userId, "강남 삼겹살집", 2, 1, ScanStatus.COMPLETED, now.minus(Duration.ofHours(1))));
        menuAnalysisRepository.saveAll(List.of(
                MenuAnalysis.create(
                        s1.getId(),
                        1,
                        "삼겹살",
                        "Pork Belly",
                        "구운 돼지고기",
                        "Grilled pork belly",
                        "12,000원",
                        false,
                        null,
                        RiskLevel.DANGER,
                        List.of("is_pork"),
                        new FinalMessage("돼지고기가 포함되어 있어요.", "Contains pork.", "يحتوي على لحم الخنزير."),
                        null),
                MenuAnalysis.create(
                        s1.getId(),
                        2,
                        "된장찌개",
                        "Soybean Paste Stew",
                        "된장 베이스 찌개",
                        "Soybean paste stew",
                        "8,000원",
                        true,
                        null,
                        RiskLevel.CAUTION,
                        List.of("has_unclear_broth"),
                        new FinalMessage("육수 재료가 불명확해요.", "Broth base unclear.", null),
                        new OwnerCard(
                                "된장찌개",
                                "has_unclear_broth",
                                new OwnerQuestion(
                                        "멸치 육수를 사용하나요?", "Do you use anchovy broth?", "هل تستخدم مرق الأنشوجة؟")))));

        // 2) COMPLETED · 기본 제목(null) · 메뉴 1건(safe)
        ScanSession s2 = scanSessionRepository.save(
                ScanSession.create(userId, null, 1, 0, ScanStatus.COMPLETED, now.minus(Duration.ofDays(1))));
        menuAnalysisRepository.save(MenuAnalysis.create(
                s2.getId(),
                1,
                "비빔밥",
                "Bibimbap",
                "야채 비빔밥",
                "Vegetable rice bowl",
                "9,000원",
                false,
                null,
                RiskLevel.SAFE,
                List.of(),
                new FinalMessage("드셔도 괜찮아요.", "Safe to eat.", null),
                null));

        // 3) NEEDS_RETAKE · 재촬영 사유
        ScanSession s3 =
                ScanSession.create(userId, null, 0, null, ScanStatus.PROCESSING, now.minus(Duration.ofDays(2)));
        s3.applyNeedsRetake(List.of("too blurry", "low light"));
        scanSessionRepository.save(s3);

        // 4) PROCESSING · 진행 중
        scanSessionRepository.save(ScanSession.create(userId, null, null, null, ScanStatus.PROCESSING, null));
    }

    private void seedCards(UUID userId) {
        savedCardRepository.saveAll(List.of(
                // 템플릿형: ingredients(hit 코드)만 저장 → FE 가 언어별 문장 재렌더
                SavedCard.create(userId, CardType.ORDER, "삼겹살", null, null, null, null),
                SavedCard.create(userId, CardType.INGREDIENT_CHECK, "김치찌개", List.of("is_fish"), null, null, null),
                SavedCard.create(userId, CardType.EXCLUDE, "비빔밥", List.of("is_egg"), null, null, null),
                // owner_question: AI 완성 문구 text + flag 저장
                SavedCard.create(
                        userId,
                        CardType.OWNER_QUESTION,
                        "된장찌개",
                        null,
                        new CardText("멸치 육수를 사용하나요?", "Do you use anchovy broth?", null),
                        "has_unclear_broth",
                        null)));
    }
}
