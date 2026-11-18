package com.hanspoon.backend_api.domain.card.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hanspoon.backend_api.global.validation.SupportedLanguages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 카드 표시 문구(언어 코드 → 문구). ko(직원 제시용) 필수 + 사용자 언어. JSONB 로 저장.
 *
 * <p>키는 지원 언어 7종만 받음. 값이 없는 언어는 내보내지 않음.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "카드 표시 문구(언어 코드 → 문구). ko 필수, 키는 7종 언어 코드만")
public record CardText(
        @NotBlank String ko,
        String en,
        String ar,
        @JsonProperty("zh-CN") String zhCn,
        @JsonProperty("zh-TW") String zhTw,
        String ja,
        String es) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static CardText fromJson(Map<String, String> values) {
        Set<String> unknown = new TreeSet<>(values.keySet());
        unknown.removeAll(SupportedLanguages.CODES);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported language keys in card text: " + unknown);
        }
        return new CardText(
                values.get("ko"),
                values.get("en"),
                values.get("ar"),
                values.get("zh-CN"),
                values.get("zh-TW"),
                values.get("ja"),
                values.get("es"));
    }

    /** 한국어 문구만 있는 카드. */
    public static CardText ko(String ko) {
        return new CardText(ko, null, null, null, null, null, null);
    }
}
