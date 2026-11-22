package com.hanspoon.backend_api.domain.ai.client;

import com.hanspoon.backend_api.domain.ai.dto.analyze.JudgeRequest;
import com.hanspoon.backend_api.domain.ai.dto.analyze.JudgeResponse;
import com.hanspoon.backend_api.domain.ai.dto.analyze.JudgeResult;
import com.hanspoon.backend_api.domain.ai.dto.analyze.PrepareRequest;
import com.hanspoon.backend_api.domain.ai.dto.analyze.PrepareResponse;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** AI Supervisor Prepare/Judge(schema 3.0) HTTP 클라이언트. */
@Component
public class AiAnalyzeClient {

    private static final String PREPARE_PATH = "/v1/analyze/prepare";
    private static final String JUDGE_PATH = "/v1/analyze/judge";
    private static final int MAX_ERROR_BODY_BYTES = 4096;

    private final RestClient prepareClient;
    private final RestClient judgeClient;
    private final JsonMapper jsonMapper;

    public AiAnalyzeClient(
            @Qualifier("aiPrepareRestClient") RestClient prepareClient,
            @Qualifier("aiJudgeRestClient") RestClient judgeClient,
            JsonMapper jsonMapper) {
        this.prepareClient = prepareClient;
        this.judgeClient = judgeClient;
        this.jsonMapper = jsonMapper;
    }

    /** OCR → 메뉴명 정제. */
    public PrepareResponse prepare(PrepareRequest request) {
        try {
            return prepareClient
                    .post()
                    .uri(PREPARE_PATH)
                    .body(request)
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::isError, (req, res) -> throwForStatus(res, ErrorCode.PREPARE_SERVICE_ERROR))
                    .body(PrepareResponse.class);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "Prepare unreachable.", exception);
        }
    }

    /** 후보 선택 → 메뉴별 판정. 원본 JSON 함께 반환. */
    public JudgeResult judge(JudgeRequest request) {
        JsonNode raw;
        try {
            raw = judgeClient
                    .post()
                    .uri(JUDGE_PATH)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> throwForStatus(res, ErrorCode.JUDGE_SERVICE_ERROR))
                    .body(JsonNode.class);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "Judge unreachable.", exception);
        }
        if (raw == null || !raw.isObject()) {
            throw new BusinessException(ErrorCode.JUDGE_SERVICE_ERROR, "Judge returned an empty response.");
        }
        try {
            return new JudgeResult(jsonMapper.treeToValue(raw, JudgeResponse.class), raw);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.JUDGE_SERVICE_ERROR, "Judge response is not readable.", exception);
        }
    }

    /** 409·422: 계약 거부(재시도 불가), 429·503: 과부하. */
    private void throwForStatus(ClientHttpResponse response, ErrorCode fallback) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        String detail = status + " " + errorCode(response);
        if (status.value() == 409 || status.value() == 422) {
            throw new BusinessException(ErrorCode.AI_CONTRACT_REJECTED, detail);
        }
        if (status.value() == 429 || status.value() == 503) {
            throw new BusinessException(ErrorCode.AI_SERVICE_OVERLOADED, detail);
        }
        throw new BusinessException(fallback, detail);
    }

    /** 오류 본문의 error.code. 읽지 못하면 빈 문자열. */
    private String errorCode(ClientHttpResponse response) {
        try (InputStream body = response.getBody()) {
            JsonNode node = jsonMapper.readTree(body.readNBytes(MAX_ERROR_BODY_BYTES));
            JsonNode code = node.path("error").path("code");
            return code.isString() ? code.asString() : "";
        } catch (IOException | JacksonException exception) {
            return "";
        }
    }
}
