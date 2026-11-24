package com.hanspoon.backend_api.domain.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.hanspoon.backend_api.domain.store.dto.StoreCandidateListResponse;
import com.hanspoon.backend_api.domain.store.dto.StoreCandidateSearchRequest;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import com.hanspoon.backend_api.domain.store.repository.StoreCandidateProjection;
import com.hanspoon.backend_api.domain.store.repository.StoreRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreSearchServiceTest {

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private StoreThumbnailResolver storeThumbnailResolver;

    @InjectMocks
    private StoreSearchService storeSearchService;

    @Test
    void searchesNearbyWithDefaultsWhenQueryIsBlank() {
        StoreCandidateSearchRequest request = new StoreCandidateSearchRequest(37.4979, 127.0276, "   ", null, null);
        StoreCandidateProjection candidate = candidate();
        when(storeRepository.findNearbyCandidates(37.4979, 127.0276, 100, 20)).thenReturn(List.of(candidate));
        when(storeThumbnailResolver.find(List.of(42L))).thenReturn(Map.of(42L, "https://cdn.test/a.jpg"));

        StoreCandidateListResponse response = storeSearchService.findCandidates(request);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().storeId()).isEqualTo(42L);
        assertThat(response.items().getFirst().name()).isEqualTo("한스푼");
        assertThat(response.items().getFirst().floorInfo()).isEqualTo("B1");
        assertThat(response.items().getFirst().verified()).isTrue();
        assertThat(response.items().getFirst().reviewCount()).isEqualTo(3);
        assertThat(response.items().getFirst().matchMethod()).isEqualTo(StoreMatchMethod.GPS_CANDIDATE);
        assertThat(response.items().getFirst().thumbnailUrl()).isEqualTo("https://cdn.test/a.jpg");
        verify(storeRepository).findNearbyCandidates(37.4979, 127.0276, 100, 20);
        verifyNoMoreInteractions(storeRepository);
    }

    @Test
    void searchesNameNationwideIgnoringRequestedRadius() {
        StoreCandidateSearchRequest request = new StoreCandidateSearchRequest(37.4979, 127.0276, "  한스푼  ", 300, 5);
        when(storeRepository.normalizeStoreName("한스푼")).thenReturn("한스푼");
        when(storeRepository.findCandidatesByName("한스푼", 37.4979, 127.0276, 5)).thenReturn(List.of());

        StoreCandidateListResponse response = storeSearchService.findCandidates(request);

        assertThat(response.items()).isEmpty();
        verify(storeRepository).normalizeStoreName("한스푼");
        verify(storeRepository).findCandidatesByName("한스푼", 37.4979, 127.0276, 5);
        verifyNoMoreInteractions(storeRepository);
    }

    @Test
    void searchesByNameOnlyWhenCoordinatesAreMissing() {
        StoreCandidateSearchRequest request = new StoreCandidateSearchRequest(null, null, " 김치 찌개 ", 1000, 10);
        StoreCandidateProjection candidate = candidate();
        when(candidate.getDistanceMeters()).thenReturn(null);
        when(candidate.getMatchedMenuName()).thenReturn("김치찌개");
        when(storeRepository.normalizeStoreName("김치 찌개")).thenReturn("김치찌개");
        when(storeRepository.findCandidatesByName("김치찌개", null, null, 10)).thenReturn(List.of(candidate));

        StoreCandidateListResponse response = storeSearchService.findCandidates(request);

        assertThat(response.items().getFirst().distanceMeters()).isNull();
        assertThat(response.items().getFirst().matchMethod()).isEqualTo(StoreMatchMethod.NAME_SEARCH);
        assertThat(response.items().getFirst().matchedMenuName()).isEqualTo("김치찌개");
        verify(storeRepository).normalizeStoreName("김치 찌개");
        verify(storeRepository).findCandidatesByName("김치찌개", null, null, 10);
        verifyNoMoreInteractions(storeRepository);
    }

    @Test
    void usesShortNameSearchWithFixedNearbyRadiusBelowThreeCharacters() {
        StoreCandidateSearchRequest request = new StoreCandidateSearchRequest(37.4979, 127.0276, "이 가", 1000, null);
        when(storeRepository.normalizeStoreName("이 가")).thenReturn("이가");
        when(storeRepository.findCandidatesByShortName("이가", 37.4979, 127.0276, 3000, 20))
                .thenReturn(List.of());

        storeSearchService.findCandidates(request);

        verify(storeRepository).normalizeStoreName("이 가");
        verify(storeRepository).findCandidatesByShortName("이가", 37.4979, 127.0276, 3000, 20);
        verifyNoMoreInteractions(storeRepository);
    }

    @Test
    void returnsEmptyWithoutSearchWhenNormalizedQueryIsEmpty() {
        StoreCandidateSearchRequest request = new StoreCandidateSearchRequest(37.4979, 127.0276, "!!!", null, null);
        when(storeRepository.normalizeStoreName("!!!")).thenReturn("");

        StoreCandidateListResponse response = storeSearchService.findCandidates(request);

        assertThat(response.items()).isEmpty();
        verify(storeRepository).normalizeStoreName("!!!");
        verifyNoMoreInteractions(storeRepository);
    }

    private StoreCandidateProjection candidate() {
        StoreCandidateProjection candidate = mock(StoreCandidateProjection.class);
        when(candidate.getStoreId()).thenReturn(42L);
        when(candidate.getName()).thenReturn("한스푼");
        when(candidate.getBranchName()).thenReturn("강남점");
        when(candidate.getRoadAddress()).thenReturn("서울특별시 강남구 테헤란로 1");
        when(candidate.getFloorInfo()).thenReturn("B1");
        when(candidate.getLatitude()).thenReturn(37.4978);
        when(candidate.getLongitude()).thenReturn(127.0275);
        when(candidate.getDistanceMeters()).thenReturn(42);
        when(candidate.getCategoryCode()).thenReturn("I20101");
        when(candidate.getCategoryName()).thenReturn("한식 일반 음식점업");
        when(candidate.getVerified()).thenReturn(true);
        when(candidate.getReviewCount()).thenReturn(3);
        return candidate;
    }
}
