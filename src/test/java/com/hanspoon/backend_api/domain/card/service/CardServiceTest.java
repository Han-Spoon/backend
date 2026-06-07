package com.hanspoon.backend_api.domain.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanspoon.backend_api.domain.card.dto.CardText;
import com.hanspoon.backend_api.domain.card.dto.SaveCardRequest;
import com.hanspoon.backend_api.domain.card.dto.SavedCardResponse;
import com.hanspoon.backend_api.domain.card.entity.CardType;
import com.hanspoon.backend_api.domain.card.entity.SavedCard;
import com.hanspoon.backend_api.domain.card.repository.SavedCardRepository;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.global.common.PageResponse;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private SavedCardRepository savedCardRepository;

    @Mock
    private ScanSessionRepository scanSessionRepository;

    @InjectMocks
    private CardService cardService;

    private static SaveCardRequest orderRequest() {
        return new SaveCardRequest(CardType.ORDER, "삼겹살", null, null, null, null);
    }

    private static SaveCardRequest excludeRequest(UUID scanId) {
        return new SaveCardRequest(CardType.EXCLUDE, "비빔밥", List.of("is_egg"), null, null, scanId);
    }

    private static SaveCardRequest ownerQuestionRequest() {
        return new SaveCardRequest(
                CardType.OWNER_QUESTION,
                "된장찌개",
                null,
                new CardText("멸치 육수를 사용하나요?", "Do you use anchovy broth?", null),
                "has_unclear_broth",
                null);
    }

    @Test
    void saveCreatesNewTemplateCard() {
        UUID userId = UUID.randomUUID();
        when(savedCardRepository.findByUserIdAndCardTypeAndMenuNameKo(userId, CardType.ORDER, "삼겹살"))
                .thenReturn(List.of());
        when(savedCardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SavedCardResponse response = cardService.save(userId, orderRequest());

        assertThat(response.type()).isEqualTo(CardType.ORDER);
        assertThat(response.menuNameKo()).isEqualTo("삼겹살");
        assertThat(response.ingredients()).isNull();
        assertThat(response.text()).isNull();
        verify(savedCardRepository).save(any());
    }

    @Test
    void saveCreatesOwnerQuestionCardWithTextAndFlag() {
        UUID userId = UUID.randomUUID();
        when(savedCardRepository.findByUserIdAndCardTypeAndMenuNameKo(userId, CardType.OWNER_QUESTION, "된장찌개"))
                .thenReturn(List.of());
        when(savedCardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SavedCardResponse response = cardService.save(userId, ownerQuestionRequest());

        assertThat(response.type()).isEqualTo(CardType.OWNER_QUESTION);
        assertThat(response.text().ko()).isEqualTo("멸치 육수를 사용하나요?");
        assertThat(response.flag()).isEqualTo("has_unclear_broth");
        assertThat(response.ingredients()).isNull();
        verify(savedCardRepository).save(any());
    }

    @Test
    void saveBumpsDuplicateTemplateCardByIngredients() {
        UUID userId = UUID.randomUUID();
        SavedCard existing = SavedCard.create(userId, CardType.EXCLUDE, "비빔밥", List.of("is_egg"), null, null, null);
        when(savedCardRepository.findByUserIdAndCardTypeAndMenuNameKo(userId, CardType.EXCLUDE, "비빔밥"))
                .thenReturn(List.of(existing));

        SavedCardResponse response = cardService.save(userId, excludeRequest(null));

        assertThat(response.cardId()).isEqualTo(existing.getId());
        verify(savedCardRepository, never()).save(any());
    }

    @Test
    void saveCreatesNewWhenIngredientsDiffer() {
        UUID userId = UUID.randomUUID();
        SavedCard existing = SavedCard.create(userId, CardType.EXCLUDE, "비빔밥", List.of("is_pork"), null, null, null);
        when(savedCardRepository.findByUserIdAndCardTypeAndMenuNameKo(userId, CardType.EXCLUDE, "비빔밥"))
                .thenReturn(List.of(existing));
        when(savedCardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        cardService.save(userId, excludeRequest(null)); // ingredients=["is_egg"] ≠ existing ["is_pork"]

        verify(savedCardRepository).save(any());
    }

    @Test
    void saveValidatesScanOwnershipWhenScanIdGiven() {
        UUID userId = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();
        when(scanSessionRepository.findByIdAndUserId(scanId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.save(userId, excludeRequest(scanId)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCAN_NOT_FOUND);
        verify(savedCardRepository, never()).save(any());
    }

    @Test
    void saveAcceptsOwnedScanId() {
        UUID userId = UUID.randomUUID();
        ScanSession session = ScanSession.create(userId, null, 1, 0, ScanStatus.COMPLETED, Instant.now());
        UUID scanId = session.getId();
        when(scanSessionRepository.findByIdAndUserId(scanId, userId)).thenReturn(Optional.of(session));
        when(savedCardRepository.findByUserIdAndCardTypeAndMenuNameKo(userId, CardType.EXCLUDE, "비빔밥"))
                .thenReturn(List.of());
        when(savedCardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SavedCardResponse response = cardService.save(userId, excludeRequest(scanId));

        assertThat(response.ingredients()).containsExactly("is_egg");
        verify(savedCardRepository).save(any());
    }

    @Test
    void getCardsReturnsMappedPage() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        SavedCard card =
                SavedCard.create(userId, CardType.INGREDIENT_CHECK, "김치찌개", List.of("is_fish"), null, null, null);
        when(savedCardRepository.findByUserIdOrderByLastSavedAtDesc(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(card), pageable, 1));

        PageResponse<SavedCardResponse> response = cardService.getCards(userId, pageable);

        assertThat(response.items()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.items().get(0).type()).isEqualTo(CardType.INGREDIENT_CHECK);
        assertThat(response.items().get(0).ingredients()).containsExactly("is_fish");
    }

    @Test
    void deleteRemovesForOwner() {
        UUID userId = UUID.randomUUID();
        SavedCard card = SavedCard.create(userId, CardType.ORDER, "삼겹살", null, null, null, null);
        UUID cardId = card.getId();
        when(savedCardRepository.findByIdAndUserId(cardId, userId)).thenReturn(Optional.of(card));

        cardService.delete(userId, cardId);

        verify(savedCardRepository).delete(card);
    }

    @Test
    void deleteThrowsWhenNotOwnedOrMissing() {
        UUID userId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        when(savedCardRepository.findByIdAndUserId(cardId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.delete(userId, cardId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CARD_NOT_FOUND);
        verify(savedCardRepository, never()).delete(any());
    }
}
