package com.hanspoon.backend_api.domain.ai.client;

import com.hanspoon.backend_api.domain.ai.dto.ocr.OcrRequest;
import com.hanspoon.backend_api.domain.ai.dto.ocr.OcrResponse;
import com.hanspoon.backend_api.domain.ai.dto.result.FinalResultResponse;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleEngineRequest;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleEngineResponse;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Han-Spoon AI 서비스(OCR + Rule Engine) HTTP 클라이언트.
 * 내부 AI 서비스의
 * /v1/ocr를 호출하여 OCR 결과를 가져오고,
 * /v1/ruleengine을 호출하여 룰엔진 판정을 수행하며,
 * /v1/result를 호출하여 최종 결과를 가져오는 역할을 합니다.
 */
@Component
public class AiClient {

    private static final String OCR_PATH = "/v1/ocr";
    private static final String RULE_ENGINE_PATH = "/v1/ruleengine";
    private static final String RESULT_PATH = "/v1/result";

    private final RestClient aiOcrRestClient;
    private final RestClient aiRuleEngineRestClient;
    private final RestClient aiResultRestClient;

    public AiClient(
            @Qualifier("aiOcrRestClient") RestClient aiOcrRestClient,
            @Qualifier("aiRuleEngineRestClient") RestClient aiRuleEngineRestClient,
            @Qualifier("aiResultRestClient") RestClient aiResultRestClient) {
        this.aiOcrRestClient = aiOcrRestClient;
        this.aiRuleEngineRestClient = aiRuleEngineRestClient;
        this.aiResultRestClient = aiResultRestClient;
    }

    /** OCR 호출: 이미지 → 메뉴 추출 + 품질 평가. */
    public OcrResponse requestOcr(OcrRequest request) {
        try {
            return aiOcrRestClient
                    .post()
                    .uri(OCR_PATH)
                    .body(request)
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::isError,
                            (req, res) -> throwForStatus(res.getStatusCode(), ErrorCode.OCR_SERVICE_ERROR, "OCR"))
                    .body(OcrResponse.class);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "OCR service unreachable.", exception);
        }
    }

    /** Rule Engine 호출: OCR 결과 + 프로필 → 메뉴별 위험도 판정. */
    public RuleEngineResponse judge(RuleEngineRequest request) {
        try {
            return aiRuleEngineRestClient
                    .post()
                    .uri(RULE_ENGINE_PATH)
                    .body(request)
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::isError,
                            (req, res) ->
                                    throwForStatus(res.getStatusCode(), ErrorCode.RULE_ENGINE_ERROR, "Rule engine"))
                    .body(RuleEngineResponse.class);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "Rule engine unreachable.", exception);
        }
    }

    /** Result 호출: 룰엔진 판정(judged) → 최종 표시 결과(message/owner_card). body 는 judged 를 그대로 전송. */
    public FinalResultResponse result(RuleEngineResponse judged) {
        try {
            return aiResultRestClient
                    .post()
                    .uri(RESULT_PATH)
                    .body(judged)
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::isError,
                            (req, res) -> throwForStatus(
                                    res.getStatusCode(), ErrorCode.RESULT_SERVICE_ERROR, "Result service"))
                    .body(FinalResultResponse.class);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "Result service unreachable.", exception);
        }
    }

    private void throwForStatus(HttpStatusCode status, ErrorCode fallback, String serviceName) {
        if (status.value() == 429 || status.value() == 503) {
            throw new BusinessException(
                    ErrorCode.AI_SERVICE_OVERLOADED, serviceName + " is temporarily overloaded (" + status + ").");
        }
        throw new BusinessException(fallback, serviceName + " responded with " + status + ".");
    }
}
