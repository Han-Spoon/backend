package com.hanspoon.backend_api.domain.store.service;

import com.hanspoon.backend_api.domain.store.dto.StoreCandidateListResponse;
import com.hanspoon.backend_api.domain.store.dto.StoreCandidateResponse;
import com.hanspoon.backend_api.domain.store.dto.StoreCandidateSearchRequest;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import com.hanspoon.backend_api.domain.store.repository.StoreCandidateProjection;
import com.hanspoon.backend_api.domain.store.repository.StoreRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreSearchService {

    private final StoreRepository storeRepository;

    public StoreSearchService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @Transactional(readOnly = true)
    public StoreCandidateListResponse findCandidates(StoreCandidateSearchRequest request) {
        String query = request.trimmedQuery();
        List<StoreCandidateProjection> candidates = query == null
                ? storeRepository.findNearbyCandidates(
                        request.latitude(),
                        request.longitude(),
                        request.resolvedRadiusMeters(),
                        request.resolvedLimit())
                : storeRepository.findNearbyCandidatesByName(
                        query,
                        request.latitude(),
                        request.longitude(),
                        request.resolvedRadiusMeters(),
                        request.resolvedLimit());

        StoreMatchMethod matchMethod = query == null ? StoreMatchMethod.GPS_CANDIDATE : StoreMatchMethod.NAME_SEARCH;
        return new StoreCandidateListResponse(candidates.stream()
                .map(candidate -> toResponse(candidate, matchMethod))
                .toList());
    }

    private static StoreCandidateResponse toResponse(StoreCandidateProjection candidate, StoreMatchMethod matchMethod) {
        return new StoreCandidateResponse(
                candidate.getStoreId(),
                candidate.getName(),
                candidate.getBranchName(),
                candidate.getRoadAddress(),
                candidate.getLatitude(),
                candidate.getLongitude(),
                candidate.getDistanceMeters(),
                candidate.getCategoryCode(),
                candidate.getCategoryName(),
                Boolean.TRUE.equals(candidate.getVerified()),
                matchMethod);
    }
}
