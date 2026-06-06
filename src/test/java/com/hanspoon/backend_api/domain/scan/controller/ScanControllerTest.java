package com.hanspoon.backend_api.domain.scan.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanspoon.backend_api.domain.scan.dto.ScanCreatedResponse;
import com.hanspoon.backend_api.domain.scan.dto.ScanHistoryItem;
import com.hanspoon.backend_api.domain.scan.dto.ScanResultResponse;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.service.ScanService;
import com.hanspoon.backend_api.global.common.PageResponse;
import com.hanspoon.backend_api.global.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
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
                .setCustomArgumentResolvers(currentUserResolver, new PageableHandlerMethodArgumentResolver())
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
                .thenReturn(new ScanResultResponse(scanId, ScanStatus.COMPLETED, null, 2, 1, null, List.of(), null));

        mockMvc.perform(get("/api/v1/scans/{scanId}", scanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanId").value(scanId.toString()))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.menuCount").value(2));
    }

    @Test
    void getScansReturnsPage() throws Exception {
        UUID scanId = UUID.randomUUID();
        ScanHistoryItem item = new ScanHistoryItem(scanId, "강남 삼겹살집", ScanStatus.COMPLETED, 2, 1, null);
        when(scanService.getScans(eq(USER_ID), any())).thenReturn(new PageResponse<>(List.of(item), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/scans?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.items[0].scanId").value(scanId.toString()))
                .andExpect(jsonPath("$.items[0].title").value("강남 삼겹살집"));
    }

    @Test
    void updateTitleReturns200() throws Exception {
        UUID scanId = UUID.randomUUID();
        when(scanService.updateTitle(eq(USER_ID), eq(scanId), any()))
                .thenReturn(new ScanHistoryItem(scanId, "새 제목", ScanStatus.COMPLETED, 2, 1, null));

        mockMvc.perform(patch("/api/v1/scans/{scanId}", scanId)
                        .contentType("application/json")
                        .content("{\"title\":\"새 제목\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanId").value(scanId.toString()))
                .andExpect(jsonPath("$.title").value("새 제목"));
    }

    @Test
    void updateTitleRejectsBlankTitle() throws Exception {
        mockMvc.perform(patch("/api/v1/scans/{scanId}", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteScanReturns204() throws Exception {
        UUID scanId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/scans/{scanId}", scanId)).andExpect(status().isNoContent());

        verify(scanService).deleteScan(USER_ID, scanId);
    }
}
