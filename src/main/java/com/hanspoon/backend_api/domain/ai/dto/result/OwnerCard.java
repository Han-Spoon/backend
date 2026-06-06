package com.hanspoon.backend_api.domain.ai.dto.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 사장님 소통 카드. 애매함 케이스에서 사장님에게 물어볼 질문. 불필요 시 null.
 *
 * @param menuName 메뉴명
 * @param flag 발동 애매함 플래그 (예: has_unclear_jeotgal)
 * @param question 다국어 확인 질문
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record OwnerCard(String menuName, String flag, OwnerQuestion question) {}
