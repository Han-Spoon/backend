package com.hanspoon.backend_api.domain.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 스캔 당시 식단. 유저 프로필과 같은 코드값.
 *
 * @param religionType 종교 식단 코드, null 가능
 * @param isVegan 채식 여부
 * @param veganType 채식 유형 코드, null 가능
 * @param allergies 알레르기 코드 (egg, milk, ...)
 * @param noSpicy 매운맛 비선호
 * @param noAlcohol 금주
 */
@Schema(description = "스캔 당시 식단")
public record ScanDietSnapshot(
        String religionType,
        Boolean isVegan,
        String veganType,
        List<String> allergies,
        Boolean noSpicy,
        Boolean noAlcohol) {}
