package com.hanspoon.backend_api.domain.favorite.service;

import com.hanspoon.backend_api.domain.favorite.dto.SavedMenuResponse;
import com.hanspoon.backend_api.domain.favorite.repository.SavedMenuRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 메뉴 찜. 내 스캔의 메뉴만. */
@Service
public class SavedMenuService {

    private final SavedMenuRepository savedMenuRepository;

    public SavedMenuService(SavedMenuRepository savedMenuRepository) {
        this.savedMenuRepository = savedMenuRepository;
    }

    @Transactional(readOnly = true)
    public List<SavedMenuResponse> list(UUID userId) {
        return savedMenuRepository.findAll(userId);
    }

    /** 멱등. 이미 찜이면 같은 항목. */
    @Transactional
    public SavedMenuResponse save(UUID userId, UUID scanMenuId) {
        savedMenuRepository.insertIfOwned(userId, scanMenuId);
        return savedMenuRepository
                .findOne(userId, scanMenuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCAN_MENU_NOT_FOUND));
    }

    @Transactional
    public void delete(UUID userId, UUID scanMenuId) {
        if (savedMenuRepository.delete(userId, scanMenuId) == 0) {
            throw new BusinessException(ErrorCode.SAVED_MENU_NOT_FOUND);
        }
    }
}
