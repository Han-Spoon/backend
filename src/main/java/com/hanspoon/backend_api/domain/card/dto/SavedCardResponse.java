package com.hanspoon.backend_api.domain.card.dto;

import com.hanspoon.backend_api.domain.card.entity.CardType;
import com.hanspoon.backend_api.domain.card.entity.SavedCard;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 저장 카드 1건 응답. 목록은 최신 저장순(lastSavedAt desc)으로 정렬된다.
 * 타입별로 채워지는 필드가 다르다(템플릿형=ingredients, owner_question=text+flag).
 *
 * @param cardId 카드 id
 * @param type 카드 종류
 * @param menuNameKo 한국어 메뉴명
 * @param ingredients hit 태그 코드(템플릿형), 그 외 null
 * @param text 표시 문구(owner_question), 그 외 null
 * @param flag 애매 사유(owner_question), 그 외 null
 * @param createdAt 최초 저장 시각
 */
@Schema(description = "저장 카드 항목")
public record SavedCardResponse(
        UUID cardId,
        CardType type,
        String menuNameKo,
        List<String> ingredients,
        CardText text,
        String flag,
        Instant createdAt) {

    public static SavedCardResponse from(SavedCard card) {
        return new SavedCardResponse(
                card.getId(),
                card.getCardType(),
                card.getMenuNameKo(),
                card.getIngredients(),
                card.getText(),
                card.getFlag(),
                card.getCreatedAt());
    }
}
