package com.hanspoon.backend_api.domain.scan.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.scan.dto.StaffAnswerRequest;
import com.hanspoon.backend_api.domain.scan.dto.StaffAnswerResponse;
import com.hanspoon.backend_api.domain.scan.dto.StaffQuestionResult;
import com.hanspoon.backend_api.domain.scan.service.StaffAnswerService;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import com.hanspoon.backend_api.global.exception.GlobalExceptionHandler;
import com.hanspoon.backend_api.global.security.CurrentUser;
import java.time.Instant;
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
class StaffQuestionControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SCAN_ID = UUID.randomUUID();
    private static final UUID QUESTION_ID = UUID.randomUUID();
    private static final String PATH = "/api/v1/scans/" + SCAN_ID + "/staff-questions/" + QUESTION_ID + "/answer";

    private final StaffAnswerService staffAnswerService = mock(StaffAnswerService.class);
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
        mockMvc = MockMvcBuilders.standaloneSetup(new StaffQuestionController(staffAnswerService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(currentUserResolver)
                .build();
    }

    @Test
    void answerReturnsQuestionMenuAndCounts() throws Exception {
        UUID menuId = UUID.randomUUID();
        StaffQuestionResult question = new StaffQuestionResult(
                QUESTION_ID,
                "card-1",
                "ingredient",
                Map.of("ko", "돼지고기"),
                null,
                null,
                Map.of("ko", "질문"),
                "answered",
                "present",
                Instant.parse("2026-11-23T00:00:00Z"));
        when(staffAnswerService.answer(eq(USER_ID), eq(SCAN_ID), eq(QUESTION_ID), any(StaffAnswerRequest.class)))
                .thenReturn(new StaffAnswerResponse(
                        question,
                        new StaffAnswerResponse.Menu(menuId, RiskLevel.DANGER, "partial"),
                        new StaffAnswerResponse.Counts(1, 0, 2)));

        mockMvc.perform(put(PATH).contentType("application/json").content("{\"answer\":\"present\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question.questionId").value(QUESTION_ID.toString()))
                .andExpect(jsonPath("$.question.answer").value("present"))
                .andExpect(jsonPath("$.menu.scanMenuId").value(menuId.toString()))
                .andExpect(jsonPath("$.menu.riskLevel").value("danger"))
                .andExpect(jsonPath("$.counts.dangerCount").value(1));
    }

    @Test
    void answerRejectsUnknownValue() throws Exception {
        mockMvc.perform(put(PATH).contentType("application/json").content("{\"answer\":\"maybe\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verify(staffAnswerService, never()).answer(any(), any(), any(), any());
    }

    @Test
    void answerRejectsMissingValue() throws Exception {
        mockMvc.perform(put(PATH).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void expiredQuestionReturns409() throws Exception {
        when(staffAnswerService.answer(any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.STAFF_QUESTION_EXPIRED));

        mockMvc.perform(put(PATH).contentType("application/json").content("{\"answer\":\"absent\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAFF_QUESTION_EXPIRED"));
    }
}
