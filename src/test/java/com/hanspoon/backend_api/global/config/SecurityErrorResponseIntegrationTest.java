package com.hanspoon.backend_api.global.config;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import com.hanspoon.backend_api.global.security.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 에러 응답 계약(ProblemDetail). 보안 필터·MVC 예외 처리가 같은 모양인지 확인. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityErrorResponseIntegrationTest {

    private static final String PROTECTED_ENDPOINT = "/api/v1/users/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("Authorization 헤더 없음 → ProblemDetail 본문")
    void missingTokenReturnsProblemDetail() throws Exception {
        mockMvc.perform(get(PROTECTED_ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.han-spoon.site/problems/INVALID_TOKEN"))
                .andExpect(jsonPath("$.title").value("Invalid or expired token."))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.instance").value(PROTECTED_ENDPOINT))
                .andExpect(jsonPath("$.timestamp").value(endsWith("Z")))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.errorId").doesNotExist());
    }

    @Test
    @DisplayName("토큰이 있으나 무효 → 빈 본문이 아니라 ProblemDetail 본문")
    void invalidTokenReturnsProblemDetailNotEmptyBody() throws Exception {
        // 본문이 비면 클라이언트가 만료·권한 부족을 구분할 수 없다
        mockMvc.perform(get(PROTECTED_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("비즈니스 예외 → 같은 ProblemDetail 모양")
    void businessErrorUsesSameShape() throws Exception {
        String path = "/api/v1/scans/" + UUID.randomUUID();
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.han-spoon.site/problems/SCAN_NOT_FOUND"))
                .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value(path))
                .andExpect(jsonPath("$.timestamp").value(endsWith("Z")))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("깨진 JSON → 400 INVALID_REQUEST")
    void malformedJsonReturnsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/stores/candidates")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.han-spoon.site/problems/INVALID_REQUEST"))
                .andExpect(jsonPath("$.title").value("Invalid Request"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("모르는 enum 값 → 400 INVALID_REQUEST")
    void unknownEnumReturnsInvalidRequest() throws Exception {
        mockMvc.perform(put("/api/v1/scans/{scanId}/record", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":1,\"storeMatchMethod\":\"unknown_method\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("잘못된 경로 변수 타입 → 400 INVALID_REQUEST")
    void pathTypeMismatchReturnsInvalidRequest() throws Exception {
        mockMvc.perform(get("/api/v1/scans/not-a-uuid").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private String bearer() {
        return "Bearer " + jwtTokenProvider.createAccessToken(UUID.randomUUID());
    }
}
