package com.hanspoon.backend_api.domain.card.service;

import com.hanspoon.backend_api.domain.card.dto.SaveCardRequest;
import com.hanspoon.backend_api.domain.card.dto.SavedCardResponse;
import com.hanspoon.backend_api.domain.card.entity.SavedCard;
import com.hanspoon.backend_api.domain.card.repository.SavedCardRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.global.common.PageResponse;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자주 쓰는 카드 저장/목록/삭제. 문구·재료는 FE 가 구성하고 서버는 저장만 담당한다.
 *
 * <p>카드는 화면에 보이는 텍스트를 그대로 스냅샷 저장한다(언어 재렌더 없음). 동일 카드 재저장 시 새 행을 만들지 않고
 * 기존 카드의 저장 시각만 갱신해 목록 최상단으로 올린다. 동일 판정은 (user, type, menuNameKo, text)로 한다.
 */
@Service
public class CardService {

    private final SavedCardRepository savedCardRepository;
    private final ScanSessionRepository scanSessionRepository;

    public CardService(SavedCardRepository savedCardRepository, ScanSessionRepository scanSessionRepository) {
        this.savedCardRepository = savedCardRepository;
        this.scanSessionRepository = scanSessionRepository;
    }

    @Transactional
    public SavedCardResponse save(UUID userId, SaveCardRequest request) {
        UUID scanId = resolveScanId(userId, request.scanId());

        SavedCard duplicate =
                savedCardRepository
                        .findByUserIdAndCardTypeAndMenuNameKo(userId, request.type(), request.menuNameKo())
                        .stream()
                        .filter(card -> card.getText().equals(request.text()))
                        .findFirst()
                        .orElse(null);
        if (duplicate != null) {
            duplicate.resave(); // 중복(동일 type/menuNameKo/text)은 새로 만들지 않고 최상단으로
            return SavedCardResponse.from(duplicate);
        }

        SavedCard saved = savedCardRepository.save(
                SavedCard.create(userId, request.type(), request.menuNameKo(), request.text(), scanId));
        return SavedCardResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<SavedCardResponse> getCards(UUID userId, Pageable pageable) {
        return PageResponse.of(
                savedCardRepository.findByUserIdOrderByLastSavedAtDesc(userId, pageable), SavedCardResponse::from);
    }

    @Transactional
    public void delete(UUID userId, UUID cardId) {
        SavedCard card = savedCardRepository
                .findByIdAndUserId(cardId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
        savedCardRepository.delete(card);
    }

    /** scanId 제공 시 본인 소유 스캔인지 검증하고 그대로 반환. 미제공이면 null. */
    private UUID resolveScanId(UUID userId, UUID scanId) {
        if (scanId == null) {
            return null;
        }
        scanSessionRepository
                .findByIdAndUserId(scanId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCAN_NOT_FOUND));
        return scanId;
    }
}
