package com.hanspoon.backend_api.domain.upload.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanspoon.backend_api.domain.upload.dto.UploadTicketResponse;
import com.hanspoon.backend_api.domain.upload.service.S3StorageService;
import com.hanspoon.backend_api.global.security.CurrentUser;
import java.time.Instant;
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

class UploadControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private final S3StorageService s3StorageService = mock(S3StorageService.class);
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
        mockMvc = MockMvcBuilders.standaloneSetup(new UploadController(s3StorageService))
                .setCustomArgumentResolvers(currentUserResolver)
                .build();
    }

    @Test
    void issuesUploadTicket() throws Exception {
        String key = "scans/" + USER_ID + "/abc.jpg";
        when(s3StorageService.createUploadUrl(eq(USER_ID), eq("image/jpeg")))
                .thenReturn(new UploadTicketResponse(
                        key,
                        "https://bucket.s3.ap-northeast-2.amazonaws.com/" + key + "?X-Amz-Signature=xyz",
                        Instant.parse("2026-06-06T00:10:00Z")));

        mockMvc.perform(post("/api/v1/uploads/sas")
                        .contentType("application/json")
                        .content("{\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageKey").value(key))
                .andExpect(jsonPath("$.uploadUrl").value(containsString("X-Amz-Signature=")))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void rejectsBlankContentType() throws Exception {
        mockMvc.perform(post("/api/v1/uploads/sas")
                        .contentType("application/json")
                        .content("{\"contentType\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
