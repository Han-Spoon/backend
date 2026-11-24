package com.hanspoon.backend_api.domain.store.service;

import com.hanspoon.backend_api.domain.store.repository.StoreRepository;
import com.hanspoon.backend_api.domain.store.repository.StoreThumbnailProjection;
import com.hanspoon.backend_api.global.config.CdnUrls;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 가게 대표 사진 주소. 가게 사진, 없으면 사진 있는 첫 가게 메뉴. 표준 메뉴 이미지 제외. */
@Component
public class StoreThumbnailResolver {

    private final StoreRepository storeRepository;
    private final CdnUrls cdnUrls;

    public StoreThumbnailResolver(StoreRepository storeRepository, CdnUrls cdnUrls) {
        this.storeRepository = storeRepository;
        this.cdnUrls = cdnUrls;
    }

    /** 사진 없는 가게는 키 없음. */
    public Map<Long, String> find(Collection<Long> storeIds) {
        if (storeIds.isEmpty()) {
            return Map.of();
        }
        return storeRepository.findThumbnailKeys(storeIds).stream()
                .collect(
                        Collectors.toMap(StoreThumbnailProjection::getStoreId, row -> cdnUrls.of(row.getStorageKey())));
    }
}
