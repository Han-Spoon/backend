package com.hanspoon.backend_api.domain.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanspoon.backend_api.domain.review.dto.ReviewRequest;
import com.hanspoon.backend_api.domain.review.dto.ReviewResponse;
import com.hanspoon.backend_api.domain.review.dto.ReviewUpdateRequest;
import com.hanspoon.backend_api.domain.review.service.ReviewService;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import com.hanspoon.backend_api.global.exception.GlobalExceptionHandler;
import com.hanspoon.backend_api.global.security.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** standalone 슬라이스. @CurrentUser 는 고정 userId. */
class ReviewControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String CREATE = "/api/v1/stores/12345/reviews";
    private static final String LINE = "{\"name\":\"김치찌개\",\"answers\":{\"religion:halal\":\"present\"}}";

    private final ReviewService reviewService = mock(ReviewService.class);
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
        mockMvc = MockMvcBuilders.standaloneSetup(new ReviewController(reviewService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(currentUserResolver)
                .build();
    }

    @Test
    void createReturns201() throws Exception {
        UUID reviewId = UUID.randomUUID();
        when(reviewService.create(eq(USER_ID), eq(12345L), any(ReviewRequest.class)))
                .thenReturn(new ReviewResponse(
                        reviewId,
                        12345L,
                        "한스푼",
                        null,
                        List.of(new ReviewResponse.Menu("김치찌개", null, Map.of("religion:halal", "present"), null, null)),
                        true,
                        false,
                        Instant.parse("2026-11-23T00:00:00Z"),
                        Instant.parse("2026-11-23T00:00:00Z")));

        mockMvc.perform(post(CREATE).contentType("application/json").content("{\"menus\":[" + LINE + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewId").value(reviewId.toString()))
                .andExpect(jsonPath("$.menus[0].answers['religion:halal']").value("present"))
                .andExpect(jsonPath("$.mine").value(true));
    }

    @Test
    void createRejectsInvalidBody() throws Exception {
        mockMvc.perform(post(CREATE).contentType("application/json").content("{\"menus\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(post(CREATE).contentType("application/json").content("{\"menus\":[{\"name\":\" \"}]}"))
                .andExpect(status().isBadRequest());
        String tooMany = "{\"menus\":[" + String.join(",", java.util.Collections.nCopies(31, LINE)) + "]}";
        mockMvc.perform(post(CREATE).contentType("application/json").content(tooMany))
                .andExpect(status().isBadRequest());
        String longComment = "{\"menus\":[{\"name\":\"잡채\",\"comment\":\"" + "a".repeat(501) + "\"}]}";
        mockMvc.perform(post(CREATE).contentType("application/json").content(longComment))
                .andExpect(status().isBadRequest());
        verify(reviewService, never()).create(any(), anyLong(), any());
    }

    @Test
    void storeRequiredReturns422() throws Exception {
        when(reviewService.create(any(), anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.REVIEW_SCAN_STORE_REQUIRED));

        mockMvc.perform(post(CREATE).contentType("application/json").content("{\"menus\":[" + LINE + "]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REVIEW_SCAN_STORE_REQUIRED"));
    }

    @Test
    void updateReturns200() throws Exception {
        UUID reviewId = UUID.randomUUID();
        when(reviewService.update(eq(USER_ID), eq(reviewId), any(ReviewUpdateRequest.class)))
                .thenReturn(new ReviewResponse(
                        reviewId,
                        12345L,
                        "한스푼",
                        null,
                        List.of(),
                        true,
                        false,
                        Instant.parse("2026-11-23T00:00:00Z"),
                        Instant.parse("2026-11-23T01:00:00Z")));

        mockMvc.perform(patch("/api/v1/reviews/" + reviewId)
                        .contentType("application/json")
                        .content("{\"menus\":[" + LINE + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedAt").value("2026-11-23T01:00:00Z"));
        mockMvc.perform(patch("/api/v1/reviews/" + reviewId)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturns204Or404() throws Exception {
        UUID reviewId = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/reviews/" + reviewId)).andExpect(status().isNoContent());
        verify(reviewService).delete(USER_ID, reviewId);

        doThrow(new BusinessException(ErrorCode.REVIEW_NOT_FOUND))
                .when(reviewService)
                .delete(any(), any());
        mockMvc.perform(delete("/api/v1/reviews/" + reviewId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
    }

    @Test
    void listsReturnArrays() throws Exception {
        ReviewResponse review = new ReviewResponse(
                UUID.randomUUID(),
                12345L,
                "한스푼",
                null,
                List.of(),
                false,
                false,
                Instant.parse("2026-11-23T00:00:00Z"),
                Instant.parse("2026-11-23T00:00:00Z"));
        when(reviewService.listByStore(USER_ID, 12345L)).thenReturn(List.of(review));
        when(reviewService.listMine(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get(CREATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reviewId").value(review.reviewId().toString()))
                .andExpect(jsonPath("$[0].mine").value(false));
        mockMvc.perform(get("/api/v1/reviews/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void reportReturns204AndValidatesReason() throws Exception {
        UUID reviewId = UUID.randomUUID();
        String path = "/api/v1/reviews/" + reviewId + "/reports";

        mockMvc.perform(post(path).contentType("application/json").content("{\"reason\":\"spam\",\"detail\":null}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(path).contentType("application/json").content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        String longDetail = "{\"reason\":\"other\",\"detail\":\"" + "a".repeat(501) + "\"}";
        mockMvc.perform(post(path).contentType("application/json").content(longDetail))
                .andExpect(status().isBadRequest());
        verify(reviewService).report(eq(USER_ID), eq(reviewId), any());
    }

    @Test
    void reportOwnReviewReturns400() throws Exception {
        doThrow(new BusinessException(ErrorCode.CANNOT_REPORT_OWN_REVIEW))
                .when(reviewService)
                .report(any(), any(), any());

        mockMvc.perform(post("/api/v1/reviews/" + UUID.randomUUID() + "/reports")
                        .contentType("application/json")
                        .content("{\"reason\":\"spam\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_REPORT_OWN_REVIEW"));
    }
}
