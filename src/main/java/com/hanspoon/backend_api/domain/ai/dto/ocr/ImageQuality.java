package com.hanspoon.backend_api.domain.ai.dto.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 이미지 품질 세부 분석 ({@code ai_ocr/image_quality.analyze_image_quality}). 느슨한 구조라
 * 알려진 필드만 매핑하고 나머지는 무시한다.
 *
 * @param available 분석 가능 여부 (PIL/cv2 미존재 시 false)
 * @param score 이미지 품질 점수
 * @param blurScore 흐림/초점 상태
 * @param brightness 밝기
 * @param contrast 대비
 * @param glareRatio 빛 반사 후보 비율
 * @param skewAngle 기울기 각도
 * @param reasons 품질 저하 사유
 * @param suggestions 개선 제안
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ImageQuality(
        Boolean available,
        Integer score,
        Double blurScore,
        Double brightness,
        Double contrast,
        Double glareRatio,
        Double skewAngle,
        List<String> reasons,
        List<String> suggestions) {}
