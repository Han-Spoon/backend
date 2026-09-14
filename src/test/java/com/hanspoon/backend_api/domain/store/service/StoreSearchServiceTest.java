package com.hanspoon.backend_api.domain.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.hanspoon.backend_api.domain.store.dto.StoreCandidateListResponse;
import com.hanspoon.backend_api.domain.store.dto.StoreCandidateSearchRequest;
import com.hanspoon.backend_api.domain.store.repository.StoreCandidateProjection;
import com.hanspoon.backend_api.domain.store.repository.StoreRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreSearchServiceTest {

    @Mock
    private StoreRepository storeRepository;

    @InjectMocks
    private StoreSearchService storeSearchService;

    @Test
    void searchesNearbyWithDefaultsWhenQueryIsBlank() {
        StoreCandidateSearchRequest request = new StoreCandidateSearchRequest(37.4979, 127.0276, "   ", null, null);
        StoreCandidateProjection candidate = candidate();
        when(storeRepository.findNearbyCandidates(37.4979, 127.0276, 100, 20)).thenReturn(List.of(candidate));

        StoreCandidateListResponse response = storeSearchService.findCandidates(request);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().storeId()).isEqualTo(42L);
        assertThat(response.items().getFirst().name()).isEqualTo("한스푼");
        assertThat(response.items().getFirst().verified()).isTrue();
        verify(storeRepository).findNearbyCandidates(37.4979, 127.0276, 100, 20);
        verifyNoMoreInteractions(storeRepository);
    }

    @Test
    void trimsNameAndUsesNameSearchWithRequestedBounds() {
        StoreCandidateSearchRequest request = new StoreCandidateSearchRequest(37.4979, 127.0276, "  한스푼  ", 300, 5);
        when(storeRepository.findNearbyCandidatesByName("한스푼", 37.4979, 127.0276, 300, 5))
                .thenReturn(List.of());

        StoreCandidateListResponse response = storeSearchService.findCandidates(request);

        assertThat(response.items()).isEmpty();
        verify(storeRepository).findNearbyCandidatesByName("한스푼", 37.4979, 127.0276, 300, 5);
        verifyNoMoreInteractions(storeRepository);
    }

    private StoreCandidateProjection candidate() {
        StoreCandidateProjection candidate = mock(StoreCandidateProjection.class);
        when(candidate.getStoreId()).thenReturn(42L);
        when(candidate.getName()).thenReturn("한스푼");
        when(candidate.getBranchName()).thenReturn("강남점");
        when(candidate.getRoadAddress()).thenReturn("서울특별시 강남구 테헤란로 1");
        when(candidate.getLatitude()).thenReturn(37.4978);
        when(candidate.getLongitude()).thenReturn(127.0275);
        when(candidate.getDistanceMeters()).thenReturn(42);
        when(candidate.getCategoryCode()).thenReturn("I20101");
        when(candidate.getCategoryName()).thenReturn("한식 일반 음식점업");
        when(candidate.getVerified()).thenReturn(true);
        return candidate;
    }
}
