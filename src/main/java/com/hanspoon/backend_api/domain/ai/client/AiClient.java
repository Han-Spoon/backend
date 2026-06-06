package com.hanspoon.backend_api.domain.ai.client;

import com.hanspoon.backend_api.domain.ai.dto.ocr.OcrRequest;
import com.hanspoon.backend_api.domain.ai.dto.ocr.OcrResponse;
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
 *
 * <p>HTTP 4xx/5xx 응답은 {@code onStatus} 에서 {@link BusinessException} 으로 변환하고, 연결 실패/타임아웃
 * ({@link ResourceAccessException})은 {@link ErrorCode#AI_SERVICE_UNAVAILABLE} 로 변환한다.
 */
@Component
public class AiClient {

    private static final String OCR_PATH = "/v1/ocr";
    private static final String RULE_ENGINE_PATH = "/v1/ruleengine";

    private final RestClient aiServiceRestClient;

    public AiClient(@Qualifier("aiServiceRestClient") RestClient aiServiceRestClient) {
        this.aiServiceRestClient = aiServiceRestClient;
    }

    /** OCR 호출: 이미지 → 메뉴 추출 + 품질 평가. */
    public OcrResponse requestOcr(OcrRequest request) {
        try {
            return aiServiceRestClient
                    .post()
                    .uri(OCR_PATH)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new BusinessException(
                                ErrorCode.OCR_SERVICE_ERROR, "OCR service responded with " + res.getStatusCode());
                    })
                    .body(OcrResponse.class);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "OCR service unreachable.", exception);
        }
    }

    /** Rule Engine 호출: OCR 결과 + 프로필 → 메뉴별 위험도 판정. */
    public RuleEngineResponse judge(RuleEngineRequest request) {
        try {
            return aiServiceRestClient
                    .post()
                    .uri(RULE_ENGINE_PATH)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new BusinessException(
                                ErrorCode.RULE_ENGINE_ERROR, "Rule engine responded with " + res.getStatusCode());
                    })
                    .body(RuleEngineResponse.class);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "Rule engine unreachable.", exception);
        }
    }
}
