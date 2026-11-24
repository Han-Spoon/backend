package com.hanspoon.backend_api.domain.store.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanspoon.backend_api.domain.store.dto.StoreCandidateListResponse;
import com.hanspoon.backend_api.domain.store.dto.StoreCandidateResponse;
import com.hanspoon.backend_api.domain.store.dto.StoreCandidateSearchRequest;
import com.hanspoon.backend_api.domain.store.dto.StoreDetailResponse;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import com.hanspoon.backend_api.domain.store.service.StoreDetailService;
import com.hanspoon.backend_api.domain.store.service.StoreSearchService;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import com.hanspoon.backend_api.global.exception.GlobalExceptionHandler;
import com.hanspoon.backend_api.global.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.json.JsonMapper;

class StoreControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private final StoreSearchService storeSearchService = mock(StoreSearchService.class);
    private final StoreDetailService storeDetailService = mock(StoreDetailService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        HandlerMethodArgumentResolver currentUserResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(CurrentUser.class);
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory) {
                return USER_ID.toString();
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(new StoreController(storeSearchService, storeDetailService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(currentUserResolver)
                .build();
    }

    @Test
    void returnsStoreDetail() throws Exception {
        when(storeDetailService.getDetail(USER_ID, 220752L))
                .thenReturn(new StoreDetailResponse(
                        220752L,
                        "이가뚝배기",
                        null,
                        "서울특별시 용산구 원효로19길 56",
                        "1",
                        37.53,
                        126.96,
                        "I20102",
                        "국/탕/찌개류",
                        true,
                        3,
                        "https://cdn.test/menus/stores/a.jpg",
                        List.of(new StoreDetailResponse.Menu(
                                9012L,
                                "뚝배기 불고기",
                                "Bulgogi in Earthen Pot",
                                null,
                                "12,000원",
                                JsonMapper.shared().readTree("[]"),
                                37L,
                                "https://cdn.test/menus/stores/a.jpg",
                                "사진 제공: 이가뚝배기",
                                "store"))));

        mockMvc.perform(get("/api/v1/stores/220752"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeId").value(220752))
                .andExpect(jsonPath("$.branchName").value(nullValue()))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.reviewCount").value(3))
                .andExpect(jsonPath("$.thumbnailUrl").value("https://cdn.test/menus/stores/a.jpg"))
                .andExpect(jsonPath("$.menus[0].storeMenuId").value(9012))
                .andExpect(jsonPath("$.menus[0].translatedName").value("Bulgogi in Earthen Pot"))
                .andExpect(jsonPath("$.menus[0].priceOptions").isArray())
                .andExpect(jsonPath("$.menus[0].menuId").value(37))
                .andExpect(jsonPath("$.menus[0].imageSource").value("store"));
    }

    @Test
    void returnsNotFoundForUnknownStore() throws Exception {
        when(storeDetailService.getDetail(USER_ID, 1L)).thenThrow(new BusinessException(ErrorCode.STORE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/stores/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }

    @Test
    void returnsStoreCandidates() throws Exception {
        when(storeSearchService.findCandidates(any()))
                .thenReturn(new StoreCandidateListResponse(List.of(new StoreCandidateResponse(
                        42L,
                        "한스푼",
                        "강남점",
                        "서울특별시 강남구 테헤란로 1",
                        "B1",
                        37.4978,
                        127.0275,
                        42,
                        "I20101",
                        "한식 일반 음식점업",
                        3,
                        true,
                        StoreMatchMethod.NAME_SEARCH))));

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
                .andExpect(jsonPath("$.items[0].floorInfo").value("B1"))
                .andExpect(jsonPath("$.items[0].distanceMeters").value(42))
                .andExpect(jsonPath("$.items[0].reviewCount").value(3))
                .andExpect(jsonPath("$.items[0].verified").value(true))
                .andExpect(jsonPath("$.items[0].matchMethod").value("name_search"));

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
    void acceptsQueryWithoutCoordinates() throws Exception {
        when(storeSearchService.findCandidates(any())).thenReturn(new StoreCandidateListResponse(List.of()));

        mockMvc.perform(post("/api/v1/stores/candidates")
                        .contentType("application/json")
                        .content("{\"query\":\"김치찌개\"}"))
                .andExpect(status().isOk());

        verify(storeSearchService).findCandidates(any());
    }

    @Test
    void rejectsBlankQueryWithoutCoordinates() throws Exception {
        mockMvc.perform(post("/api/v1/stores/candidates")
                        .contentType("application/json")
                        .content("{\"query\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(storeSearchService, never()).findCandidates(any());
    }

    @Test
    void rejectsSingleCoordinate() throws Exception {
        mockMvc.perform(post("/api/v1/stores/candidates")
                        .contentType("application/json")
                        .content("{\"latitude\":37.4979,\"query\":\"한스푼\"}"))
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
                                  "radiusMeters": 3001,
                                  "limit": 101
                                }
                                """
                                        .formatted("가".repeat(101))))
                .andExpect(status().isBadRequest());

        verify(storeSearchService, never()).findCandidates(any());
    }
}
