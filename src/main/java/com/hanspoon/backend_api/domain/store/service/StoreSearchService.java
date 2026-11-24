package com.hanspoon.backend_api.domain.store.service;

import com.hanspoon.backend_api.domain.store.dto.StoreCandidateListResponse;
import com.hanspoon.backend_api.domain.store.dto.StoreCandidateResponse;
import com.hanspoon.backend_api.domain.store.dto.StoreCandidateSearchRequest;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import com.hanspoon.backend_api.domain.store.repository.StoreCandidateProjection;
import com.hanspoon.backend_api.domain.store.repository.StoreRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreSearchService {

    /** 포함 매칭에 trigram 인덱스를 쓸 수 있는 정규형 최소 길이. */
    private static final int TRIGRAM_MIN_LENGTH = 3;

    /** 짧은 검색어의 포함 매칭 반경(m). */
    private static final int SHORT_QUERY_RADIUS_METERS = 3000;

    private final StoreRepository storeRepository;
    private final StoreThumbnailResolver storeThumbnailResolver;

    public StoreSearchService(StoreRepository storeRepository, StoreThumbnailResolver storeThumbnailResolver) {
        this.storeRepository = storeRepository;
        this.storeThumbnailResolver = storeThumbnailResolver;
    }

    @Transactional(readOnly = true)
    public StoreCandidateListResponse findCandidates(StoreCandidateSearchRequest request) {
        String query = request.trimmedQuery();
        if (query == null) {
            return toListResponse(
                    storeRepository.findNearbyCandidates(
                            request.latitude(),
                            request.longitude(),
                            request.resolvedRadiusMeters(),
                            request.resolvedLimit()),
                    StoreMatchMethod.GPS_CANDIDATE);
        }
        return toListResponse(findByName(query, request), StoreMatchMethod.NAME_SEARCH);
    }

    /** 전국 이름·메뉴명 검색. 좌표는 거리 계산·정렬에만 사용하고 요청 반경은 무시. */
    private List<StoreCandidateProjection> findByName(String query, StoreCandidateSearchRequest request) {
        String normalized = storeRepository.normalizeStoreName(query);
        if (normalized == null || normalized.isEmpty()) {
            return List.of();
        }
        if (normalized.codePointCount(0, normalized.length()) >= TRIGRAM_MIN_LENGTH) {
            return storeRepository.findCandidatesByName(
                    normalized, request.latitude(), request.longitude(), request.resolvedLimit());
        }
        return storeRepository.findCandidatesByShortName(
                normalized,
                request.latitude(),
                request.longitude(),
                SHORT_QUERY_RADIUS_METERS,
                request.resolvedLimit());
    }

    private StoreCandidateListResponse toListResponse(
            List<StoreCandidateProjection> candidates, StoreMatchMethod matchMethod) {
        Map<Long, String> thumbnails = storeThumbnailResolver.find(
                candidates.stream().map(StoreCandidateProjection::getStoreId).toList());
        return new StoreCandidateListResponse(candidates.stream()
                .map(candidate -> toResponse(candidate, matchMethod, thumbnails.get(candidate.getStoreId())))
                .toList());
    }

    private static StoreCandidateResponse toResponse(
            StoreCandidateProjection candidate, StoreMatchMethod matchMethod, String thumbnailUrl) {
        return new StoreCandidateResponse(
                candidate.getStoreId(),
                candidate.getName(),
                candidate.getBranchName(),
                candidate.getRoadAddress(),
                candidate.getFloorInfo(),
                candidate.getLatitude(),
                candidate.getLongitude(),
                candidate.getDistanceMeters(),
                candidate.getCategoryCode(),
                candidate.getCategoryName(),
                candidate.getReviewCount() == null ? 0 : candidate.getReviewCount(),
                Boolean.TRUE.equals(candidate.getVerified()),
                matchMethod,
                thumbnailUrl,
                candidate.getMatchedMenuName());
    }
}
