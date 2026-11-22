package com.hanspoon.backend_api.domain.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanspoon.backend_api.domain.ai.dto.analyze.DietProfile;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** AI 서비스 와이어 계약(snake_case)과 DTO 매핑 일치 검증. 프로덕션 HTTP 스택과 동일하게 Jackson 3 으로 검증. */
class AiDtoSerializationTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void serializesDietProfileInSnakeCase() throws Exception {
        DietProfile profile = new DietProfile("halal", true, "vegan", true, List.of("is_milk"), false);

        String json = objectMapper.writeValueAsString(profile);

        assertThat(json)
                .contains("\"religion_type\":\"halal\"")
                .contains("\"is_vegetarian\":true")
                .contains("\"vegetarian_type\":\"vegan\"")
                .contains("\"no_alcohol\":true")
                .contains("\"allergies\":[\"is_milk\"]")
                .contains("\"no_spicy\":false");
    }
}
