package com.hanspoon.backend_api.global.config;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.card.dto.CardText;
import com.hanspoon.backend_api.domain.card.entity.CardType;
import com.hanspoon.backend_api.domain.card.entity.SavedCard;
import com.hanspoon.backend_api.domain.card.repository.SavedCardRepository;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.AnalyzeJudgeRepository;
import com.hanspoon.backend_api.domain.scan.repository.AnalyzeJudgeRepository.MenuJudgment;
import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.user.entity.AllergyCode;
import com.hanspoon.backend_api.domain.user.entity.ReligionType;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.entity.UserProfile;
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
    private static final String DIET_SNAPSHOT =
            """
            {"religion_type":"halal","is_vegetarian":false,"vegetarian_type":null,"no_alcohol":true,\
            "allergies":["is_peanut","is_shrimp","is_egg"],"no_spicy":false}""";

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ScanSessionRepository scanSessionRepository;
    private final MenuAnalysisRepository menuAnalysisRepository;
    private final AnalyzeJudgeRepository analyzeJudgeRepository;
    private final SavedCardRepository savedCardRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public DevDataSeeder(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            ScanSessionRepository scanSessionRepository,
            MenuAnalysisRepository menuAnalysisRepository,
            AnalyzeJudgeRepository analyzeJudgeRepository,
            SavedCardRepository savedCardRepository,
            JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.scanSessionRepository = scanSessionRepository;
        this.menuAnalysisRepository = menuAnalysisRepository;
        this.analyzeJudgeRepository = analyzeJudgeRepository;
        this.savedCardRepository = savedCardRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User user = userRepository.findByEmail(DEMO_EMAIL).orElse(null);
        if (user == null) {
            user = userRepository.save(User.create(DEMO_EMAIL, "데모유저", "ko"));
            userProfileRepository.save(UserProfile.create(
                    user.getId(),
                    "SA",
                    false,
                    false,
                    null,
                    ReligionType.HALAL,
                    false,
                    true,
                    List.of(AllergyCode.PEANUT, AllergyCode.SHRIMP, AllergyCode.EGG)));
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

        // 1) COMPLETED · 커스텀 제목 · 메뉴 2건(danger + caution)
        ScanSession s1 = ScanSession.create(userId, "강남 삼겹살집", null, ScanStatus.PROCESSING, null);
        MenuAnalysis porkBelly =
                MenuAnalysis.prepared(s1.getId(), 0, "삼겹살", "구운 돼지고기", "12,000원", null, null, null, null);
        MenuAnalysis stew = MenuAnalysis.prepared(s1.getId(), 1, "된장찌개", "된장 베이스 찌개", "8,000원", null, true, null, null);
        completeScan(
                s1,
                now.minus(Duration.ofHours(1)),
                List.of(porkBelly, stew),
                List.of(RiskLevel.DANGER, RiskLevel.CAUTION));
        judge(
                porkBelly,
                0,
                RiskLevel.DANGER,
                "estimated",
                "partial",
                risk("돼지고기", "present", null, "is_pork", "identity", null),
                "[\"is_pork\"]",
                message("돼지고기가 들어 있어요.", "삼겹살은 돼지고기 메뉴예요."));
        judge(
                stew,
                1,
                RiskLevel.CAUTION,
                "unknown",
                "partial",
                risk("새우", "unknown", 0.42, "is_shrimp", "hidden", "global"),
                "[\"is_shrimp\"]",
                message("새우가 들어갈 수 있어요.", "육수에 새우를 쓰는지 확인이 필요해요."));

        // 2) COMPLETED · 기본 제목(null) · 메뉴 1건(caution). 가게 없는 스캔은 직원 응답이 없어 safe 불가
        ScanSession s2 = ScanSession.create(userId, null, null, ScanStatus.PROCESSING, null);
        MenuAnalysis bibimbap = MenuAnalysis.prepared(s2.getId(), 0, "비빔밥", "야채 비빔밥", "9,000원", null, null, null, null);
        completeScan(s2, now.minus(Duration.ofDays(1)), List.of(bibimbap), List.of(RiskLevel.CAUTION));
        judge(
                bibimbap,
                0,
                RiskLevel.CAUTION,
                "estimated",
                "partial",
                risk("달걀", "unknown", 0.55, "is_egg", "component", "global"),
                "[\"is_egg\"]",
                message("달걀이 올라갈 수 있어요.", null));

        // 3) NEEDS_RETAKE · 재촬영 사유
        ScanSession s3 = ScanSession.create(userId, null, 0, ScanStatus.PROCESSING, now.minus(Duration.ofDays(2)));
        s3.applyNeedsRetake(List.of("too blurry", "low light"), List.of("카메라를 메뉴판과 평행하게 두고 다시 촬영해 주세요."));
        scanSessionRepository.save(s3);

        // 4) PROCESSING · 진행 중
        scanSessionRepository.save(ScanSession.create(userId, null, null, ScanStatus.PROCESSING, null));
    }

    /** Prepare·Judge 저장과 같은 순서. 판정 컬럼은 JDBC 로 기록. */
    private void completeScan(
            ScanSession session, Instant scannedAt, List<MenuAnalysis> menus, List<RiskLevel> riskLevels) {
        session.applyPrepareResult(menus.size(), scannedAt, "ko");
        session.applyJudgeResult(riskLevels, DIET_SNAPSHOT);
        scanSessionRepository.save(session);
        menuAnalysisRepository.saveAllAndFlush(menus);
    }

    private void judge(
            MenuAnalysis menu,
            int displayOrder,
            RiskLevel riskLevel,
            String evidenceBasis,
            String informationStatus,
            String riskIngredients,
            String matchedTags,
            String message) {
        analyzeJudgeRepository.updateMenuJudgment(new MenuJudgment(
                menu.getId(),
                riskLevel.getCode(),
                displayOrder,
                "unmatched",
                "completed",
                evidenceBasis,
                informationStatus,
                riskIngredients,
                matchedTags,
                message,
                "[]",
                null,
                "unmatched",
                "none"));
    }

    private static String risk(
            String name, String status, Double posteriorMean, String tag, String role, String priorSource) {
        return """
                [{"ingredient":{"ingredient_id":null,"canonical_name":"%s","localized_name":"%s"},\
                "constraint_tags":["%s"],"matched_tags":["%s"],"role":"%s","status":"%s",\
                "posterior_mean":%s,"confidence":null,"prior_source":%s,"evidence_refs":[]}]"""
                .formatted(
                        name,
                        name,
                        tag,
                        tag,
                        role,
                        status,
                        posteriorMean,
                        priorSource == null ? "null" : "\"" + priorSource + "\"");
    }

    private static String message(String verdict, String reason) {
        return "{\"verdict\":\"%s\",\"reason\":%s,\"notes\":[]}"
                .formatted(verdict, reason == null ? "null" : "\"" + reason + "\"");
    }

    private void seedCards(UUID userId) {
        // 모든 카드는 화면에 보이는 text 를 그대로 저장(언어 스냅샷). 재료확인 text 는 AI 직원 카드 문구 예시.
        savedCardRepository.saveAll(List.of(
                SavedCard.create(
                        userId,
                        CardType.ORDER,
                        "삼겹살",
                        new CardText("삼겹살 하나 주세요.", "One pork belly, please.", null, null, null, null, null),
                        null),
                SavedCard.create(
                        userId,
                        CardType.INGREDIENT_CHECK,
                        "된장찌개",
                        new CardText(
                                "이 메뉴에 멸치육수가 들어가나요?",
                                "Does this dish contain fish broth?",
                                null,
                                null,
                                null,
                                null,
                                null),
                        null),
                SavedCard.create(
                        userId,
                        CardType.EXCLUDE,
                        "비빔밥",
                        new CardText("계란 빼주세요.", "No egg, please.", null, null, null, null, null),
                        null)));
    }
}
