package com.hanspoon.backend_api.domain.card;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import com.hanspoon.backend_api.domain.card.dto.CardText;
import com.hanspoon.backend_api.domain.card.entity.CardType;
import com.hanspoon.backend_api.domain.card.entity.SavedCard;
import com.hanspoon.backend_api.domain.card.repository.SavedCardRepository;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * card 도메인 영속화 + JSONB 라운드트립 검증. @SpringBootTest 부팅이 Flyway V1(통합 베이스라인) 마이그레이트 후
 * Hibernate validate 를 수행하므로 스키마↔엔티티 정합도 함께 검증된다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CardPersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScanSessionRepository scanSessionRepository;

    @Autowired
    private SavedCardRepository savedCardRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void persistsCardWithJsonbText() {
        User user = userRepository.save(User.create("card-text@example.com", "carduser", "ko"));
        UUID cardId = savedCardRepository
                .save(SavedCard.create(
                        user.getId(),
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
                        null))
                .getId();

        entityManager.flush();
        entityManager.clear();

        SavedCard reloaded = savedCardRepository.findById(cardId).orElseThrow();
        assertThat(reloaded.getCardType()).isEqualTo(CardType.INGREDIENT_CHECK);
        assertThat(reloaded.getMenuNameKo()).isEqualTo("된장찌개");
        assertThat(reloaded.getText().ko()).isEqualTo("이 메뉴에 멸치육수가 들어가나요?");
        assertThat(reloaded.getText().en()).isEqualTo("Does this dish contain fish broth?");
        assertThat(reloaded.getLastSavedAt()).isNotNull();
    }

    @Test
    void persistsLessSpicyCardWithExtendedLanguage() {
        User user = userRepository.save(User.create("card-less-spicy@example.com", "carduser", "zh-TW"));
        CardText text = new CardText("김치찌개 덜 맵게 해 주실 수 있나요?", null, null, null, "泡菜鍋可以少辣嗎？", null, null);
        UUID cardId = savedCardRepository
                .save(SavedCard.create(user.getId(), CardType.LESS_SPICY, "김치찌개", text, null))
                .getId();

        entityManager.flush();
        entityManager.clear();

        SavedCard reloaded = savedCardRepository.findById(cardId).orElseThrow();
        assertThat(reloaded.getCardType()).isEqualTo(CardType.LESS_SPICY);
        assertThat(reloaded.getText()).isEqualTo(text);
    }

    @Test
    void deletingUserCascadesToCards() {
        User user = userRepository.save(User.create("card-cascade@example.com", "carduser2", "ko"));
        UUID cardId = savedCardRepository
                .save(SavedCard.create(user.getId(), CardType.ORDER, "삼겹살", CardText.ko("삼겹살 하나 주세요."), null))
                .getId();
        entityManager.flush();

        userRepository.delete(user);
        entityManager.flush();
        entityManager.clear();

        assertThat(savedCardRepository.findById(cardId)).isEmpty();
    }

    @Test
    void deletingScanSetsCardScanReferenceNull() {
        User user = userRepository.save(User.create("card-scannull@example.com", "carduser3", "ko"));
        ScanSession scan = scanSessionRepository.save(
                ScanSession.create(user.getId(), "m.jpg", 1, ScanStatus.COMPLETED, Instant.now()));
        UUID cardId = savedCardRepository
                .save(SavedCard.create(user.getId(), CardType.EXCLUDE, "비빔밥", CardText.ko("계란 빼주세요."), scan.getId()))
                .getId();
        entityManager.flush();
        entityManager.clear();

        scanSessionRepository.delete(
                scanSessionRepository.findById(scan.getId()).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        SavedCard reloaded = savedCardRepository.findById(cardId).orElseThrow();
        assertThat(reloaded.getScanSessionId()).isNull();
    }
}
