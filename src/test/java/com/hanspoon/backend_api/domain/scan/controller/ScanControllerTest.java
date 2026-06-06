package com.hanspoon.backend_api.domain.scan.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanspoon.backend_api.domain.scan.dto.ScanCreatedResponse;
import com.hanspoon.backend_api.domain.scan.dto.ScanResultResponse;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.service.ScanService;
import com.hanspoon.backend_api.global.security.CurrentUser;
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

/** standalone 슬라이스. @CurrentUser 는 고정 userId 로 해석. 인증 강제는 SecurityConfig 담당(여기 범위 밖). */
class ScanControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private final ScanService scanService = mock(ScanService.class);
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
        mockMvc = MockMvcBuilders.standaloneSetup(new ScanController(scanService))
                .setCustomArgumentResolvers(currentUserResolver)
                .build();
    }

    @Test
    void startScanReturns202() throws Exception {
        UUID scanId = UUID.randomUUID();
        when(scanService.startScan(eq(USER_ID), any()))
                .thenReturn(new ScanCreatedResponse(scanId, ScanStatus.PROCESSING));

        mockMvc.perform(post("/api/v1/scans")
                        .contentType("application/json")
                        .content("{\"storageKey\":\"menu-x.jpg\",\"source\":\"upload\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.scanId").value(scanId.toString()))
                .andExpect(jsonPath("$.status").value("processing"));
    }

    @Test
    void startScanRejectsBlankStorageKey() throws Exception {
        mockMvc.perform(post("/api/v1/scans").contentType("application/json").content("{\"storageKey\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getScanReturnsResult() throws Exception {
        UUID scanId = UUID.randomUUID();
        when(scanService.getScan(eq(USER_ID), eq(scanId)))
                .thenReturn(new ScanResultResponse(scanId, ScanStatus.COMPLETED, null, 2, 1, null, List.of()));

        mockMvc.perform(get("/api/v1/scans/{scanId}", scanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanId").value(scanId.toString()))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.menuCount").value(2));
    }
}
