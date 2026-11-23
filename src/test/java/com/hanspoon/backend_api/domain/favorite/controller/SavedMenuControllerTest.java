package com.hanspoon.backend_api.domain.favorite.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.favorite.dto.SavedMenuResponse;
import com.hanspoon.backend_api.domain.favorite.service.SavedMenuService;
import com.hanspoon.backend_api.domain.scan.dto.ScanStoreSummary;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import com.hanspoon.backend_api.global.exception.GlobalExceptionHandler;
import com.hanspoon.backend_api.global.security.CurrentUser;
import java.time.Instant;
import java.util.List;
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
class SavedMenuControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MENU_ID = UUID.randomUUID();
    private static final String PATH = "/api/v1/saved-menus";

    private final SavedMenuService savedMenuService = mock(SavedMenuService.class);
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
        mockMvc = MockMvcBuilders.standaloneSetup(new SavedMenuController(savedMenuService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(currentUserResolver)
                .build();
    }

    @Test
    void saveReturnsSavedMenu() throws Exception {
        UUID scanId = UUID.randomUUID();
        when(savedMenuService.save(USER_ID, MENU_ID))
                .thenReturn(new SavedMenuResponse(
                        MENU_ID,
                        scanId,
                        "김치찌개",
                        "Kimchi stew",
                        RiskLevel.DANGER,
                        new ScanStoreSummary(12345L, "한스푼"),
                        Instant.parse("2026-11-23T00:00:00Z")));

        mockMvc.perform(post(PATH).contentType("application/json").content(body(MENU_ID.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanMenuId").value(MENU_ID.toString()))
                .andExpect(jsonPath("$.scanId").value(scanId.toString()))
                .andExpect(jsonPath("$.displayName").value("김치찌개"))
                .andExpect(jsonPath("$.riskLevel").value("danger"))
                .andExpect(jsonPath("$.store.storeId").value(12345))
                .andExpect(jsonPath("$.savedAt").value("2026-11-23T00:00:00Z"));
    }

    @Test
    void listReturnsArray() throws Exception {
        when(savedMenuService.list(USER_ID))
                .thenReturn(List.of(new SavedMenuResponse(
                        MENU_ID,
                        UUID.randomUUID(),
                        "잡채",
                        null,
                        RiskLevel.CAUTION,
                        null,
                        Instant.parse("2026-11-22T00:00:00Z"))));

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scanMenuId").value(MENU_ID.toString()))
                .andExpect(jsonPath("$[0].riskLevel").value("caution"))
                .andExpect(jsonPath("$[0].store").isEmpty());
    }

    @Test
    void saveRejectsMissingOrInvalidId() throws Exception {
        mockMvc.perform(post(PATH).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(post(PATH).contentType("application/json").content(body("not-a-uuid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verify(savedMenuService, never()).save(any(), any());
    }

    @Test
    void saveUnknownMenuReturns404() throws Exception {
        when(savedMenuService.save(any(), any())).thenThrow(new BusinessException(ErrorCode.SCAN_MENU_NOT_FOUND));

        mockMvc.perform(post(PATH).contentType("application/json").content(body(MENU_ID.toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCAN_MENU_NOT_FOUND"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete(PATH + "/" + MENU_ID)).andExpect(status().isNoContent());
        verify(savedMenuService).delete(eq(USER_ID), eq(MENU_ID));
    }

    @Test
    void deleteMissingReturns404() throws Exception {
        doThrow(new BusinessException(ErrorCode.SAVED_MENU_NOT_FOUND))
                .when(savedMenuService)
                .delete(any(), any());

        mockMvc.perform(delete(PATH + "/" + MENU_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SAVED_MENU_NOT_FOUND"));
    }

    private static String body(String scanMenuId) {
        return "{\"scanMenuId\":\"" + scanMenuId + "\"}";
    }
}
