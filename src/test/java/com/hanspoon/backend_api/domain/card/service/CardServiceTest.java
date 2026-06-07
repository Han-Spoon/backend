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

    private static SaveCardRequest request(CardType type, String menuNameKo, CardText text, UUID scanId) {
        return new SaveCardRequest(type, menuNameKo, text, scanId);
    }

    private static CardText orderText() {
        return new CardText("삼겹살 하나 주세요.", "One pork belly, please.", null);
    }

    @Test
    void saveCreatesNewCard() {
        UUID userId = UUID.randomUUID();
        when(savedCardRepository.findByUserIdAndCardTypeAndMenuNameKo(userId, CardType.ORDER, "삼겹살"))
                .thenReturn(List.of());
        when(savedCardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SavedCardResponse response = cardService.save(userId, request(CardType.ORDER, "삼겹살", orderText(), null));

        assertThat(response.type()).isEqualTo(CardType.ORDER);
        assertThat(response.menuNameKo()).isEqualTo("삼겹살");
        assertThat(response.text().ko()).isEqualTo("삼겹살 하나 주세요.");
        verify(savedCardRepository).save(any());
    }

    @Test
    void saveBumpsDuplicateByText() {
        UUID userId = UUID.randomUUID();
        SavedCard existing = SavedCard.create(userId, CardType.ORDER, "삼겹살", orderText(), null);
        when(savedCardRepository.findByUserIdAndCardTypeAndMenuNameKo(userId, CardType.ORDER, "삼겹살"))
                .thenReturn(List.of(existing));

        SavedCardResponse response = cardService.save(userId, request(CardType.ORDER, "삼겹살", orderText(), null));

        assertThat(response.cardId()).isEqualTo(existing.getId());
        verify(savedCardRepository, never()).save(any());
    }

    @Test
    void saveCreatesNewWhenTextDiffers() {
        UUID userId = UUID.randomUUID();
        SavedCard existing =
                SavedCard.create(userId, CardType.ORDER, "삼겹살", new CardText("삼겹살 2인분 주세요.", null, null), null);
        when(savedCardRepository.findByUserIdAndCardTypeAndMenuNameKo(userId, CardType.ORDER, "삼겹살"))
                .thenReturn(List.of(existing));
        when(savedCardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        cardService.save(userId, request(CardType.ORDER, "삼겹살", orderText(), null)); // 다른 text

        verify(savedCardRepository).save(any());
    }

    @Test
    void saveValidatesScanOwnershipWhenScanIdGiven() {
        UUID userId = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();
        when(scanSessionRepository.findByIdAndUserId(scanId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.save(userId, request(CardType.ORDER, "삼겹살", orderText(), scanId)))
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
        when(savedCardRepository.findByUserIdAndCardTypeAndMenuNameKo(userId, CardType.ORDER, "삼겹살"))
                .thenReturn(List.of());
        when(savedCardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SavedCardResponse response = cardService.save(userId, request(CardType.ORDER, "삼겹살", orderText(), scanId));

        assertThat(response.menuNameKo()).isEqualTo("삼겹살");
        verify(savedCardRepository).save(any());
    }

    @Test
    void getCardsReturnsMappedPage() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        SavedCard card = SavedCard.create(
                userId, CardType.INGREDIENT_CHECK, "된장찌개", new CardText("이 메뉴에 멸치육수가 들어가나요?", null, null), null);
        when(savedCardRepository.findByUserIdOrderByLastSavedAtDesc(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(card), pageable, 1));

        PageResponse<SavedCardResponse> response = cardService.getCards(userId, pageable);

        assertThat(response.items()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.items().get(0).type()).isEqualTo(CardType.INGREDIENT_CHECK);
        assertThat(response.items().get(0).text().ko()).isEqualTo("이 메뉴에 멸치육수가 들어가나요?");
    }

    @Test
    void deleteRemovesForOwner() {
        UUID userId = UUID.randomUUID();
        SavedCard card = SavedCard.create(userId, CardType.ORDER, "삼겹살", orderText(), null);
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
