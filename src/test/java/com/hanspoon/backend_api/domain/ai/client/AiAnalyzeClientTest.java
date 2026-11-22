package com.hanspoon.backend_api.domain.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.hanspoon.backend_api.domain.ai.dto.analyze.JudgeRequest;
import com.hanspoon.backend_api.domain.ai.dto.analyze.JudgeResult;
import com.hanspoon.backend_api.domain.ai.dto.analyze.PrepareRequest;
import com.hanspoon.backend_api.domain.ai.dto.analyze.PrepareResponse;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AiAnalyzeClientTest {

    private static final String BASE_URL = "http://ai-service";
    private static final UUID TRACE_ID = UUID.fromString("0199a2f0-0000-7000-8000-000000000001");

    private final JsonMapper mapper = JsonMapper.builder().build();
    private MockRestServiceServer server;
    private AiAnalyzeClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        client = new AiAnalyzeClient(restClient, restClient, mapper);
    }

    @Test
    void prepareSendsSnakeCaseRequestAndKeepsRawItems() throws IOException {
        String body = fixture("prepare-response.json");
        server.expect(requestTo(BASE_URL + "/v1/analyze/prepare"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.schema_version").value("3.0"))
                .andExpect(jsonPath("$.scan_session_id").value("scan-123"))
                .andExpect(jsonPath("$.image.storage_key").value("scans/menu.jpg"))
                .andExpect(jsonPath("$.image.version_id").isEmpty())
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        PrepareResponse response = client.prepare(PrepareRequest.of(
                TRACE_ID, "scan-123", new PrepareRequest.Image("camera", "scans/menu.jpg", null, null), "ko"));

        assertThat(response.preparedItems()).isEqualTo(mapper.readTree(body).get("prepared_items"));
        server.verify();
    }

    @Test
    void judgePassesPreparedItemsThroughUnchanged() throws IOException {
        JsonNode documented = mapper.readTree(fixture("judge-request.json"));
        JsonNode preparedItems = documented.get("prepared_items");
        server.expect(requestTo(BASE_URL + "/v1/analyze/judge"))
                .andExpect(content().json("{\"store_id\":null,\"prepared_items\":" + preparedItems + "}"))
                .andExpect(jsonPath("$.prepared_items[0].origin_text").value((Object) null))
                .andRespond(withSuccess(fixture("judge-response.json"), MediaType.APPLICATION_JSON));

        JudgeResult result = client.judge(new JudgeRequest(
                "3.0",
                TRACE_ID,
                TRACE_ID,
                "scan-123",
                null,
                documented.get("prepared_items_hash").asString(),
                "rev",
                documented.get("prepare_signals"),
                preparedItems,
                null,
                null,
                "ko"));

        assertThat(result.response().items()).hasSize(1);
        assertThat(result.raw().get("bundle_hash").asString())
                .isEqualTo(result.response().bundleHash());
        server.verify();
    }

    @Test
    void contractRejectionIsNotRetryable() {
        server.expect(requestTo(BASE_URL + "/v1/analyze/judge"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"prepared_items_hash_mismatch\"}}"));

        assertThatThrownBy(() -> client.judge(emptyJudgeRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("prepared_items_hash_mismatch")
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_CONTRACT_REJECTED);
    }

    @Test
    void capacityResponseMapsToOverloaded() {
        server.expect(requestTo(BASE_URL + "/v1/analyze/prepare"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.prepare(PrepareRequest.of(
                        TRACE_ID, "scan-123", new PrepareRequest.Image("upload", "k", null, null), "ko")))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_SERVICE_OVERLOADED);
    }

    @Test
    void otherErrorsMapToStageError() {
        server.expect(requestTo(BASE_URL + "/v1/analyze/judge"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.judge(emptyJudgeRequest()))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.JUDGE_SERVICE_ERROR);
    }

    private JudgeRequest emptyJudgeRequest() {
        return new JudgeRequest("3.0", TRACE_ID, TRACE_ID, "scan-123", 1L, "h", "rev", null, null, null, null, "ko");
    }

    private String fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/ai/analyze/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
