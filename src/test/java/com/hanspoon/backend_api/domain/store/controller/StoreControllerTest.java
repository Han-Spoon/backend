package com.hanspoon.backend_api.domain.store.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanspoon.backend_api.domain.store.dto.StoreCandidateListResponse;
import com.hanspoon.backend_api.domain.store.dto.StoreCandidateResponse;
import com.hanspoon.backend_api.domain.store.dto.StoreCandidateSearchRequest;
import com.hanspoon.backend_api.domain.store.service.StoreSearchService;
import com.hanspoon.backend_api.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StoreControllerTest {

    private final StoreSearchService storeSearchService = mock(StoreSearchService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StoreController(storeSearchService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsStoreCandidates() throws Exception {
        when(storeSearchService.findCandidates(any()))
                .thenReturn(new StoreCandidateListResponse(List.of(new StoreCandidateResponse(
                        42L, "한스푼", "강남점", "서울특별시 강남구 테헤란로 1", 37.4978, 127.0275, 42, "I20101", "한식 일반 음식점업", true))));

        mockMvc.perform(
                        post("/api/v1/stores/candidates")
                                .contentType("application/json")
                                .content(
                                        """
                                {
                                  "latitude": 37.4979,
                                  "longitude": 127.0276,
                                  "query": "한스푼"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].storeId").value(42))
                .andExpect(jsonPath("$.items[0].name").value("한스푼"))
                .andExpect(jsonPath("$.items[0].distanceMeters").value(42))
                .andExpect(jsonPath("$.items[0].verified").value(true));

        ArgumentCaptor<StoreCandidateSearchRequest> requestCaptor =
                ArgumentCaptor.forClass(StoreCandidateSearchRequest.class);
        verify(storeSearchService).findCandidates(requestCaptor.capture());
        StoreCandidateSearchRequest request = requestCaptor.getValue();
        assertThat(request.resolvedRadiusMeters()).isEqualTo(100);
        assertThat(request.resolvedLimit()).isEqualTo(20);
    }

    @Test
    void rejectsMissingLocation() throws Exception {
        mockMvc.perform(post("/api/v1/stores/candidates")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(storeSearchService, never()).findCandidates(any());
    }

    @Test
    void rejectsOutOfKoreaCoordinatesAndOversizedQuery() throws Exception {
        mockMvc.perform(post("/api/v1/stores/candidates")
                        .contentType("application/json")
                        .content(
                                """
                                {
                                  "latitude": 40.0,
                                  "longitude": 127.0276,
                                  "query": "%s",
                                  "radiusMeters": 1001,
                                  "limit": 21
                                }
                                """
                                        .formatted("가".repeat(101))))
                .andExpect(status().isBadRequest());

        verify(storeSearchService, never()).findCandidates(any());
    }
}
