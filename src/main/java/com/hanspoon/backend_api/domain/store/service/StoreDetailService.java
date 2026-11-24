package com.hanspoon.backend_api.domain.store.service;

import com.hanspoon.backend_api.domain.store.dto.StoreDetailResponse;
import com.hanspoon.backend_api.domain.store.repository.StoreDetailProjection;
import com.hanspoon.backend_api.domain.store.repository.StoreMenuDetailProjection;
import com.hanspoon.backend_api.domain.store.repository.StoreMenuRepository;
import com.hanspoon.backend_api.domain.store.repository.StoreRepository;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import com.hanspoon.backend_api.global.config.CdnUrls;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class StoreDetailService {

    private static final String KOREAN = "ko";
    private static final JsonMapper JSON = JsonMapper.shared();

    private final StoreRepository storeRepository;
    private final StoreMenuRepository storeMenuRepository;
    private final UserRepository userRepository;
    private final CdnUrls cdnUrls;

    public StoreDetailService(
            StoreRepository storeRepository,
            StoreMenuRepository storeMenuRepository,
            UserRepository userRepository,
            CdnUrls cdnUrls) {
        this.storeRepository = storeRepository;
        this.storeMenuRepository = storeMenuRepository;
        this.userRepository = userRepository;
        this.cdnUrls = cdnUrls;
    }

    /** 영업 중 가게 상세. 한국어 사용자는 번역 이름 없음. */
    @Transactional(readOnly = true)
    public StoreDetailResponse getDetail(UUID userId, long storeId) {
        StoreDetailProjection store = storeRepository
                .findActiveDetail(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        String locale = userRepository
                .findById(userId)
                .map(User::getLanguageCode)
                .filter(code -> !KOREAN.equals(code))
                .orElse(null);
        return new StoreDetailResponse(
                store.getStoreId(),
                store.getName(),
                store.getBranchName(),
                store.getRoadAddress(),
                store.getFloorInfo(),
                store.getLatitude(),
                store.getLongitude(),
                store.getCategoryCode(),
                store.getCategoryName(),
                Boolean.TRUE.equals(store.getVerified()),
                store.getReviewCount() == null ? 0 : store.getReviewCount(),
                cdnUrls.of(store.getThumbnailStorageKey()),
                storeMenuRepository.findActiveMenusWithImages(storeId, locale).stream()
                        .map(this::toMenu)
                        .toList());
    }

    private StoreDetailResponse.Menu toMenu(StoreMenuDetailProjection menu) {
        return new StoreDetailResponse.Menu(
                menu.getStoreMenuId(),
                menu.getName(),
                menu.getTranslatedName(),
                menu.getDescription(),
                menu.getPriceText(),
                priceOptions(menu.getPriceOptions()),
                menu.getMenuId(),
                cdnUrls.of(menu.getImageStorageKey()),
                menu.getImageAttribution(),
                menu.getImageSource());
    }

    private static JsonNode priceOptions(String json) {
        return JSON.readTree(json == null ? "[]" : json);
    }
}
