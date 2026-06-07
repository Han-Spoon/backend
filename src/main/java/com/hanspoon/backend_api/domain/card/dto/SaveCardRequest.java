package com.hanspoon.backend_api.domain.card.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hanspoon.backend_api.domain.card.entity.CardType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * 카드 저장 요청. 문구/재료는 FE 가 분석 결과(hits/ownerCard)로 구성해 전달한다.
 *
 * <p>타입별 페이로드가 다르다:
 * <ul>
 *   <li>order: menuNameKo 만.
 *   <li>ingredient_check / exclude: menuNameKo + ingredients(hit 코드).
 *   <li>owner_question: menuNameKo + text(AI 완성 문구) + flag(선택).
 * </ul>
 *
 * @param type 카드 종류 (order | ingredient_check | exclude | owner_question)
 * @param menuNameKo 한국어 메뉴명(사장님 제시용)
 * @param ingredients hit 태그 코드 목록(템플릿형 전용)
 * @param text 표시 문구(다국어, owner_question 전용). ko 필수
 * @param flag 애매 사유 플래그(owner_question 선택)
 * @param scanId 출처 스캔 id(선택). 제공 시 본인 소유 검증
 */
@Schema(description = "카드 저장 요청")
public record SaveCardRequest(
        @NotNull CardType type,
        @NotBlank @Size(max = 255) String menuNameKo,
        List<String> ingredients,
        @Valid CardText text,
        @Size(max = 50) String flag,
        UUID scanId) {

    /** ingredient_check / exclude 는 ingredients 가 필수(non-empty). */
    @JsonIgnore
    @AssertTrue(message = "ingredients is required for ingredient_check/exclude cards.") public boolean isIngredientsValid() {
        if (type != CardType.INGREDIENT_CHECK && type != CardType.EXCLUDE) {
            return true;
        }
        return ingredients != null && !ingredients.isEmpty();
    }

    /** owner_question 은 text(다국어 완성 문구)가 필수. */
    @JsonIgnore
    @AssertTrue(message = "text is required for owner_question cards.") public boolean isTextValid() {
        if (type != CardType.OWNER_QUESTION) {
            return true;
        }
        return text != null;
    }
}
