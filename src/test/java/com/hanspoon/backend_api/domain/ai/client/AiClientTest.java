package com.hanspoon.backend_api.domain.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.ai.dto.ocr.OcrRequest;
import com.hanspoon.backend_api.domain.ai.dto.ocr.OcrResponse;
import com.hanspoon.backend_api.domain.ai.dto.result.FinalResultResponse;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleEngineRequest;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleEngineResponse;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleProfile;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiClientTest {

    private static final String BASE_URL = "http://ai-service";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private AiClient aiClient;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        aiClient = new AiClient(builder.build());
    }

    @Test
    void requestOcrParsesSuccessResponse() {
        String body =
                """
                {
                  "scan_session": {"title": "m.jpg", "menu_count": 1, "scan_status": "completed"},
                  "menu_image": {"source": "upload"},
                  "scan_quality": {"status": "usable", "score": 80},
                  "menu_analyses": [{"menu_name_ko": "x", "is_spicy": false, "display_order": 1}]
                }
                """;
        server.expect(requestTo(BASE_URL + "/v1/ocr"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        OcrResponse response = aiClient.requestOcr(new OcrRequest("upload", "k", "u"));

        assertThat(response.scanSession().menuCount()).isEqualTo(1);
        assertThat(response.menuAnalyses()).hasSize(1);
        server.verify();
    }

    @Test
    void requestOcrMapsErrorStatusToBusinessException() {
        server.expect(requestTo(BASE_URL + "/v1/ocr")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> aiClient.requestOcr(new OcrRequest("upload", "k", "u")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OCR_SERVICE_ERROR);
        server.verify();
    }

    @Test
    void requestOcrMapsConnectionFailureToUnavailable() {
        server.expect(requestTo(BASE_URL + "/v1/ocr")).andRespond(request -> {
            throw new IOException("connection refused");
        });

        assertThatThrownBy(() -> aiClient.requestOcr(new OcrRequest("upload", "k", "u")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE);
        server.verify();
    }

    @Test
    void judgeParsesRuleEngineResponse() {
        String body =
                """
                {
                  "scan_session": {"title": "m.jpg", "menu_count": 1, "risky_menu_count": 1, "scan_status": "completed"},
                  "menu_image": {"source": "upload"},
                  "scan_quality": {"status": "usable", "score": 80},
                  "menu_analyses": [
                    {"menu_name_ko": "x", "is_spicy": false, "risk_level": "danger", "need_gpt": false}
                  ]
                }
                """;
        server.expect(requestTo(BASE_URL + "/v1/ruleengine"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        RuleEngineRequest request =
                new RuleEngineRequest(new RuleProfile("halal", false, null, true, List.of(), false), null);
        RuleEngineResponse response = aiClient.judge(request);

        assertThat(response.scanSession().riskyMenuCount()).isEqualTo(1);
        assertThat(response.menuAnalyses().get(0).riskLevel()).isEqualTo(RiskLevel.DANGER);
        server.verify();
    }

    @Test
    void judgeMapsErrorStatusToBusinessException() {
        server.expect(requestTo(BASE_URL + "/v1/ruleengine")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        RuleEngineRequest request =
                new RuleEngineRequest(new RuleProfile(null, false, null, false, List.of(), false), null);

        assertThatThrownBy(() -> aiClient.judge(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RULE_ENGINE_ERROR);
        server.verify();
    }

    @Test
    void resultParsesFinalResponse() {
        String body =
                """
                {
                  "scan_session": {"title": "m.jpg", "menu_count": 1, "scan_status": "completed"},
                  "menu_image": {"source": "upload"},
                  "scan_quality": {"status": "usable", "score": 80},
                  "menu_analyses": [
                    {"menu_name": "x", "risk_level": "caution", "hits": [],
                     "message": {"ko": "broth?", "en": null, "ar": null},
                     "owner_card": {"menu_name": "x", "flag": "has_unclear_broth",
                                    "question": {"ko": "use anchovy?", "en": null, "ar": null}}}
                  ]
                }
                """;
        server.expect(requestTo(BASE_URL + "/v1/result"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        FinalResultResponse response = aiClient.result(new RuleEngineResponse(null, null, null, List.of()));

        assertThat(response.menuAnalyses()).hasSize(1);
        assertThat(response.menuAnalyses().get(0).riskLevel()).isEqualTo(RiskLevel.CAUTION);
        assertThat(response.menuAnalyses().get(0).message().ko()).isEqualTo("broth?");
        assertThat(response.menuAnalyses().get(0).ownerCard().question().ko()).isEqualTo("use anchovy?");
        server.verify();
    }

    @Test
    void resultMapsErrorStatusToBusinessException() {
        server.expect(requestTo(BASE_URL + "/v1/result")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> aiClient.result(new RuleEngineResponse(null, null, null, List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESULT_SERVICE_ERROR);
        server.verify();
    }
}
