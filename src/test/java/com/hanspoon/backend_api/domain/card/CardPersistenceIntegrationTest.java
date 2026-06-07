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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * card 도메인 영속화 + JSONB 라운드트립 검증. @SpringBootTest 부팅이 Flyway V1~V6 마이그레이트 후
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
    void persistsTemplateCardWithIngredientsJsonb() {
        User user = userRepository.save(User.create("card-tmpl@example.com", "carduser", "ko"));
        UUID cardId = savedCardRepository
                .save(SavedCard.create(
                        user.getId(), CardType.EXCLUDE, "비빔밥", List.of("is_egg", "is_pork"), null, null, null))
                .getId();

        entityManager.flush();
        entityManager.clear();

        SavedCard reloaded = savedCardRepository.findById(cardId).orElseThrow();
        assertThat(reloaded.getCardType()).isEqualTo(CardType.EXCLUDE);
        assertThat(reloaded.getIngredients()).containsExactly("is_egg", "is_pork");
        assertThat(reloaded.getText()).isNull();
        assertThat(reloaded.getFlag()).isNull();
        assertThat(reloaded.getLastSavedAt()).isNotNull();
    }

    @Test
    void persistsOwnerQuestionCardWithTextAndFlagJsonb() {
        User user = userRepository.save(User.create("card-owner@example.com", "carduser2", "ko"));
        UUID cardId = savedCardRepository
                .save(SavedCard.create(
                        user.getId(),
                        CardType.OWNER_QUESTION,
                        "된장찌개",
                        null,
                        new CardText("멸치 육수를 사용하나요?", "Do you use anchovy broth?", null),
                        "has_unclear_broth",
                        null))
                .getId();

        entityManager.flush();
        entityManager.clear();

        SavedCard reloaded = savedCardRepository.findById(cardId).orElseThrow();
        assertThat(reloaded.getCardType()).isEqualTo(CardType.OWNER_QUESTION);
        assertThat(reloaded.getIngredients()).isNull();
        assertThat(reloaded.getText().ko()).isEqualTo("멸치 육수를 사용하나요?");
        assertThat(reloaded.getText().en()).isEqualTo("Do you use anchovy broth?");
        assertThat(reloaded.getFlag()).isEqualTo("has_unclear_broth");
    }

    @Test
    void deletingUserCascadesToCards() {
        User user = userRepository.save(User.create("card-cascade@example.com", "carduser3", "ko"));
        UUID cardId = savedCardRepository
                .save(SavedCard.create(user.getId(), CardType.ORDER, "삼겹살", null, null, null, null))
                .getId();
        entityManager.flush();

        userRepository.delete(user);
        entityManager.flush();
        entityManager.clear();

        assertThat(savedCardRepository.findById(cardId)).isEmpty();
    }

    @Test
    void deletingScanSetsCardScanReferenceNull() {
        User user = userRepository.save(User.create("card-scannull@example.com", "carduser4", "ko"));
        ScanSession scan = scanSessionRepository.save(
                ScanSession.create(user.getId(), "m.jpg", 1, 0, ScanStatus.COMPLETED, Instant.now()));
        UUID cardId = savedCardRepository
                .save(SavedCard.create(
                        user.getId(), CardType.INGREDIENT_CHECK, "김치찌개", List.of("is_fish"), null, null, scan.getId()))
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
