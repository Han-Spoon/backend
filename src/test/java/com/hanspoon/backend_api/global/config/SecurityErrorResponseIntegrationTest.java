package com.hanspoon.backend_api.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 인증 실패 응답 계약. 토큰 부재와 토큰 무효는 서로 다른 entry point 를 타므로 둘 다 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityErrorResponseIntegrationTest {

    private static final String PROTECTED_ENDPOINT = "/api/v1/users/me";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Authorization 헤더 없음 → ProblemDetail 본문")
    void missingTokenReturnsProblemDetail() throws Exception {
        mockMvc.perform(get(PROTECTED_ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.errorId").isNotEmpty());
    }

    @Test
    @DisplayName("토큰이 있으나 무효 → 빈 본문이 아니라 ProblemDetail 본문")
    void invalidTokenReturnsProblemDetailNotEmptyBody() throws Exception {
        // 본문이 비면 클라이언트가 만료·권한 부족을 구분할 수 없다
        mockMvc.perform(get(PROTECTED_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.errorId").isNotEmpty());
    }
}
