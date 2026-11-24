package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.MenuPhotoProjection;
import com.hanspoon.backend_api.global.config.CdnProperties;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 스캔 메뉴 id → 공개 사진(가게 메뉴 사진, 없으면 표준 메뉴 이미지). */
@Component
public class MenuPhotoResolver {

    private final MenuAnalysisRepository menuAnalysisRepository;
    private final String baseUrl;

    public MenuPhotoResolver(MenuAnalysisRepository menuAnalysisRepository, CdnProperties cdnProperties) {
        this.menuAnalysisRepository = menuAnalysisRepository;
        this.baseUrl = cdnProperties.baseUrl().replaceAll("/+$", "");
    }

    public Map<UUID, MenuPhoto> find(UUID scanSessionId) {
        return menuAnalysisRepository.findPhotosByScanSessionId(scanSessionId).stream()
                .collect(Collectors.toMap(
                        MenuPhotoProjection::getMenuAnalysisId,
                        p -> new MenuPhoto(baseUrl + "/" + p.getStorageKey(), p.getAttributionText())));
    }

    /** 공개 주소 + 출처 표기. */
    public record MenuPhoto(String url, String attribution) {}
}
