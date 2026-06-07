package com.hanspoon.backend_api.domain.card.controller;

import com.hanspoon.backend_api.domain.card.dto.SaveCardRequest;
import com.hanspoon.backend_api.domain.card.dto.SavedCardResponse;
import com.hanspoon.backend_api.domain.card.service.CardService;
import com.hanspoon.backend_api.global.common.PageResponse;
import com.hanspoon.backend_api.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Card", description = "자주 쓰는 카드(사장님 소통 카드) 저장/목록/삭제 API")
@RestController
@RequestMapping("/api/v1/cards/saved")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @Operation(summary = "카드 저장(결과 화면).", description = "동일 카드 재저장 시 최상단으로 갱신. 문구는 FE 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedCardResponse save(@CurrentUser String userId, @Valid @RequestBody SaveCardRequest request) {
        return cardService.save(UUID.fromString(userId), request);
    }

    @Operation(summary = "저장 카드 목록 조회(마이페이지).", description = "최신 저장순 - (lastSavedAt desc)으로 정렬")
    @GetMapping
    public PageResponse<SavedCardResponse> getCards(
            @CurrentUser String userId, @PageableDefault(size = 20) Pageable pageable) {
        return cardService.getCards(UUID.fromString(userId), pageable);
    }

    @Operation(summary = "저장 카드 삭제(마이페이지).")
    @DeleteMapping("/{cardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser String userId, @PathVariable UUID cardId) {
        cardService.delete(UUID.fromString(userId), cardId);
    }
}
