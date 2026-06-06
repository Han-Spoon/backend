package com.hanspoon.backend_api.domain.scan.dto;

import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 스캔 결과 조회 응답. status 가 COMPLETED 일 때 menus 가 채워진다. NEEDS_RETAKE 면 menus 는 비어 있다.
 *
 * @param scanId 스캔 세션 id
 * @param status 스캔 상태 (processing | completed | failed | needs_retake)
 * @param title 유저가 수정한 제목(미수정이면 null). 기본 제목은 FE 가 scannedAt 을 로케일로 포맷해 표시
 * @param menuCount 추출된 메뉴 수
 * @param riskyMenuCount 위험/주의 메뉴 수
 * @param scannedAt 스캔 시각
 * @param menus 메뉴별 분석 결과
 */
@Schema(description = "스캔 결과 조회 응답")
public record ScanResultResponse(
        UUID scanId,
        ScanStatus status,
        String title,
        Integer menuCount,
        Integer riskyMenuCount,
        Instant scannedAt,
        List<MenuResult> menus) {}
