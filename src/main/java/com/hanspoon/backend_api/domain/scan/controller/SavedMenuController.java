package com.hanspoon.backend_api.domain.scan.controller;

import com.hanspoon.backend_api.domain.scan.dto.SaveMenuRequest;
import com.hanspoon.backend_api.domain.scan.dto.SavedMenuResponse;
import com.hanspoon.backend_api.domain.scan.service.SavedMenuService;
import com.hanspoon.backend_api.global.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/saved-menus")
public class SavedMenuController {
    private final SavedMenuService service;

    public SavedMenuController(SavedMenuService service) {
        this.service = service;
    }

    @GetMapping
    public List<SavedMenuResponse> list(@CurrentUser String userId) {
        return service.list(UUID.fromString(userId));
    }

    @PostMapping
    public SavedMenuResponse save(@CurrentUser String userId, @Valid @RequestBody SaveMenuRequest request) {
        return service.save(UUID.fromString(userId), request);
    }

    @DeleteMapping("/{favoriteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser String userId, @PathVariable UUID favoriteId) {
        service.delete(UUID.fromString(userId), favoriteId);
    }
}
