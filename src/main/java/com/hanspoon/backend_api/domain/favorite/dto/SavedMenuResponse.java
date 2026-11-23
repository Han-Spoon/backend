package com.hanspoon.backend_api.domain.favorite.dto;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.scan.dto.ScanStoreSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * 찜한 메뉴 1건. 표시값은 스캔 메뉴·스캔에서 조회.
 *
 * @param scanMenuId 찜 키, 해제 경로 값
 * @param scanId 결과 화면 링크
 * @param displayName 메뉴판 표기
 * @param translatedName 스캔 언어 이름, 없으면 null
 * @param riskLevel 현재 판정(직원 응답 반영), 판정 전 null
 * @param store 스캔 가게, 없으면 기록에 연결한 가게. 둘 다 없으면 null
 * @param savedAt 찜한 시각
 */
@Schema(description = "찜한 메뉴")
public record SavedMenuResponse(
        UUID scanMenuId,
        UUID scanId,
        String displayName,
        String translatedName,
        RiskLevel riskLevel,
        ScanStoreSummary store,
        Instant savedAt) {}
