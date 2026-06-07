package com.hanspoon.backend_api.domain.card.dto;

import com.hanspoon.backend_api.domain.card.entity.CardType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * 카드 저장 요청. FE 가 화면에 보이는 문구를 그대로 {@code text} 로 전달한다(언어 스냅샷 — 서버는 저장만).
 *
 * @param type 카드 종류 (order | ingredient_check | exclude)
 * @param menuNameKo 한국어 메뉴명(사장님 제시용)
 * @param text 표시 문구(다국어). ko 필수 + 사용자 언어 1개
 * @param scanId 출처 스캔 id(선택). 제공 시 본인 소유 검증
 */
@Schema(description = "카드 저장 요청")
public record SaveCardRequest(
        @NotNull CardType type,
        @NotBlank @Size(max = 255) String menuNameKo,
        @NotNull @Valid CardText text,
        UUID scanId) {}
