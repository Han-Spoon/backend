package com.hanspoon.backend_api.domain.upload.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanspoon.backend_api.domain.upload.dto.UploadTicketResponse;
import com.hanspoon.backend_api.domain.upload.service.BlobStorageService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 컨트롤러 standalone 슬라이스. 인증 강제는 SecurityConfig(anyRequest authenticated, PUBLIC_ENDPOINTS 미포함)가
 * 담당하므로 여기서는 위임/검증/응답 스키마만 확인한다.
 */
class UploadControllerTest {

    private final BlobStorageService blobStorageService = mock(BlobStorageService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UploadController(blobStorageService))
                .build();
    }

    @Test
    void issuesUploadSas() throws Exception {
        when(blobStorageService.createUploadSas("image/jpeg"))
                .thenReturn(new UploadTicketResponse(
                        "menu-abc.jpg",
                        "https://testacct.blob.core.windows.net/board-images/menu-abc.jpg?sig=xyz",
                        "https://testacct.blob.core.windows.net/board-images/menu-abc.jpg",
                        Instant.parse("2026-06-06T00:10:00Z")));

        mockMvc.perform(post("/api/v1/uploads/sas")
                        .contentType("application/json")
                        .content("{\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageKey").value("menu-abc.jpg"))
                .andExpect(jsonPath("$.uploadUrl").value(containsString("sig=")))
                .andExpect(jsonPath("$.readUrl").value(containsString("board-images/menu-abc.jpg")));
    }

    @Test
    void rejectsBlankContentType() throws Exception {
        mockMvc.perform(post("/api/v1/uploads/sas")
                        .contentType("application/json")
                        .content("{\"contentType\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
