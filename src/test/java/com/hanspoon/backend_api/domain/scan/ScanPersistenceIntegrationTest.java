package com.hanspoon.backend_api.domain.scan;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.ai.dto.result.FinalMessage;
import com.hanspoon.backend_api.domain.ai.dto.result.OwnerCard;
import com.hanspoon.backend_api.domain.ai.dto.result.OwnerQuestion;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.GptContext;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RiskReason;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.MenuImage;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.MenuImageRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * scan 도메인 영속화 + JSONB 라운드트립 검증. @SpringBootTest 부팅 자체가 Flyway V1~V4 마이그레이트 후
 * Hibernate validate 를 수행하므로 스키마↔엔티티 정합도 함께 검증된다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ScanPersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScanSessionRepository scanSessionRepository;

    @Autowired
    private MenuImageRepository menuImageRepository;

    @Autowired
    private MenuAnalysisRepository menuAnalysisRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void persistsAndReloadsScanGraphWithJsonbFields() {
        User user = userRepository.save(User.create("scan-test@example.com", "scanuser", "ko"));
        UUID userId = user.getId();

        ScanSession session = scanSessionRepository.save(
                ScanSession.create(userId, "menu_001.jpg", 2, null, ScanStatus.PROCESSING, Instant.now()));
        UUID sessionId = session.getId();

        menuImageRepository.save(MenuImage.create(
                sessionId,
                "upload",
                "scans/menu_001.jpg",
                "https://example.com/scans/menu_001.jpg",
                "image/jpeg",
                123456L));

        GptContext gptContext = new GptContext("sundubu", List.of("sundubu", "egg"), List.of("is_egg"), List.of());
        menuAnalysisRepository.save(MenuAnalysis.create(
                sessionId,
                1,
                "sundubu",
                null,
                "",
                null,
                "8000",
                true,
                null,
                RiskLevel.CAUTION,
                Boolean.TRUE,
                List.of(),
                List.of("has_unclear_broth", "has_unclear_jeotgal"),
                List.of("is_alcohol", "is_pork"),
                List.of("ambiguity"),
                gptContext,
                List.of(new RiskReason("halal", "pork possible")),
                new FinalMessage("pork possible", null, null),
                new OwnerCard("sundubu", "has_unclear_jeotgal", new OwnerQuestion("use jeotgal?", null, null))));

        // 강제로 DB 까지 쓰고(L1 비우고) 다시 읽어 JSONB 직렬화/역직렬화 라운드트립을 검증
        entityManager.flush();
        entityManager.clear();

        ScanSession reloadedSession =
                scanSessionRepository.findByIdAndUserId(sessionId, userId).orElseThrow();
        assertThat(reloadedSession.getScanStatus()).isEqualTo(ScanStatus.PROCESSING);
        assertThat(reloadedSession.getRiskyMenuCount()).isNull();

        MenuImage reloadedImage =
                menuImageRepository.findByScanSessionId(sessionId).orElseThrow();
        assertThat(reloadedImage.getStorageKey()).isEqualTo("scans/menu_001.jpg");
        assertThat(reloadedImage.getSource()).isEqualTo("upload");

        List<MenuAnalysis> analyses = menuAnalysisRepository.findByScanSessionIdOrderByDisplayOrder(sessionId);
        assertThat(analyses).hasSize(1);
        MenuAnalysis analysis = analyses.get(0);
        assertThat(analysis.getRiskLevel()).isEqualTo(RiskLevel.CAUTION);
        assertThat(analysis.getNeedGpt()).isTrue();
        assertThat(analysis.getTriggeredFlags()).containsExactly("has_unclear_broth", "has_unclear_jeotgal");
        assertThat(analysis.getEscalationCase()).containsExactly("ambiguity");
        assertThat(analysis.getGptContext().baseMenu()).isEqualTo("sundubu");
        assertThat(analysis.getGptContext().explicitTags()).containsExactly("is_egg");
        assertThat(analysis.getRiskReasons()).hasSize(1);
        assertThat(analysis.getRiskReasons().get(0).reasonType()).isEqualTo("halal");
        assertThat(analysis.getMessage().ko()).isEqualTo("pork possible");
        assertThat(analysis.getOwnerCard().flag()).isEqualTo("has_unclear_jeotgal");
        assertThat(analysis.getOwnerCard().question().ko()).isEqualTo("use jeotgal?");
    }

    @Test
    void appliesRuleEngineResultUpdatesCountAndStatus() {
        User user = userRepository.save(User.create("scan-update@example.com", "scanuser2", "ko"));
        ScanSession session = scanSessionRepository.save(
                ScanSession.create(user.getId(), "m.jpg", 3, null, ScanStatus.PROCESSING, Instant.now()));

        session.applyRuleEngineResult(2, ScanStatus.COMPLETED);
        scanSessionRepository.save(session);
        entityManager.flush();
        entityManager.clear();

        ScanSession reloaded = scanSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getRiskyMenuCount()).isEqualTo(2);
        assertThat(reloaded.getScanStatus()).isEqualTo(ScanStatus.COMPLETED);
    }
}
