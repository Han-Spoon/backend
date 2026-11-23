package com.hanspoon.backend_api.domain.favorite.controller;

import com.hanspoon.backend_api.domain.favorite.dto.SaveMenuRequest;
import com.hanspoon.backend_api.domain.favorite.dto.SavedMenuResponse;
import com.hanspoon.backend_api.domain.favorite.service.SavedMenuService;
import com.hanspoon.backend_api.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Saved Menu", description = "메뉴 찜 API")
@RestController
@RequestMapping("/api/v1/saved-menus")
public class SavedMenuController {

    private final SavedMenuService savedMenuService;

    public SavedMenuController(SavedMenuService savedMenuService) {
        this.savedMenuService = savedMenuService;
    }

    @Operation(summary = "메뉴 찜(이미 찜이면 같은 항목). 내 스캔의 메뉴만")
    @PostMapping
    public SavedMenuResponse save(@CurrentUser String userId, @Valid @RequestBody SaveMenuRequest request) {
        return savedMenuService.save(UUID.fromString(userId), request.scanMenuId());
    }

    @Operation(summary = "찜 해제. 내 찜에 없으면 404")
    @DeleteMapping("/{scanMenuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser String userId, @PathVariable UUID scanMenuId) {
        savedMenuService.delete(UUID.fromString(userId), scanMenuId);
    }
}
