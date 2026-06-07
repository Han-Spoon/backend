package com.hanspoon.backend_api.domain.card.dto;

import com.hanspoon.backend_api.domain.card.entity.CardType;
import com.hanspoon.backend_api.domain.card.entity.SavedCard;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * 저장 카드 1건 응답. 목록은 최신 저장순(lastSavedAt desc)으로 정렬된다.
 *
 * @param cardId 카드 id
 * @param type 카드 종류
 * @param menuNameKo 한국어 메뉴명
 * @param text 표시 문구(다국어, 저장 시점 스냅샷)
 * @param createdAt 최초 저장 시각
 */
@Schema(description = "저장 카드 항목")
public record SavedCardResponse(UUID cardId, CardType type, String menuNameKo, CardText text, Instant createdAt) {

    public static SavedCardResponse from(SavedCard card) {
        return new SavedCardResponse(
                card.getId(), card.getCardType(), card.getMenuNameKo(), card.getText(), card.getCreatedAt());
    }
}
