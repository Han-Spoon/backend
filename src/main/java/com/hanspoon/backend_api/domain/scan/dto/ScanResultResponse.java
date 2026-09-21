package com.hanspoon.backend_api.domain.scan.dto;

import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 스캔 결과 조회 응답. status 가 COMPLETED 일 때 menus 가 채워짐.
 *
 * @param scanId 스캔 세션 id
 * @param status 스캔 상태 (processing | completed | failed | needs_retake)
 * @param title 유저가 수정한 제목(미수정이면 null). 기본 제목은 FE 가 scannedAt 을 로케일로 포맷해 표시
 * @param store 스캔 시점 가게 정보. 가게 도입 전 레거시 스캔은 null
 * @param record 사용자가 명시적으로 보관한 기록. 아직 저장하지 않았으면 null
 * @param menuCount 추출된 메뉴 수
 * @param riskyMenuCount 위험/주의 메뉴 수
 * @param scannedAt 스캔 시각
 * @param menus 메뉴별 분석 결과
 * @param retakeReasons 재촬영 사유 (status 가 NEEDS_RETAKE 일 때만 채워짐, 그 외 null). OCR 이 제공한 문자열 그대로(언어 혼재 가능, i18n 키 아님)
 * @param retakeSuggestions 재촬영 방법 안내 (status 가 NEEDS_RETAKE 일 때만 채워짐, 그 외 null)
 * @param failureCode 실패 원인 코드 (status 가 FAILED 일 때만 채워짐)
 */
@Schema(description = "스캔 결과 조회 응답")
public record ScanResultResponse(
        UUID scanId,
        ScanStatus status,
        String title,
        ScanStoreSummary store,
        ScanRecordResponse record,
        Integer menuCount,
        Integer riskyMenuCount,
        Instant scannedAt,
        List<MenuResult> menus,
        List<String> retakeReasons,
        List<String> retakeSuggestions,
        String failureCode) {}
