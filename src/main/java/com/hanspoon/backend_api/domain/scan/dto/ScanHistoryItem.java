package com.hanspoon.backend_api.domain.scan.dto;

import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * 스캔 이력 목록/제목수정 응답 1건. 목록에선 {@code menus} 를 싣지 않는다(상세는 GET /scans/{scanId} 재사용).
 *
 * @param scanId 스캔 세션 id
 * @param title 유저 편집 제목(미편집이면 null → FE 가 scannedAt 로케일 포맷)
 * @param status 스캔 상태
 * @param menuCount 추출 메뉴 수
 * @param riskyMenuCount 위험/주의 메뉴 수
 * @param scannedAt 스캔 시각
 */
@Schema(description = "스캔 이력 항목")
public record ScanHistoryItem(
        UUID scanId, String title, ScanStatus status, Integer menuCount, Integer riskyMenuCount, Instant scannedAt) {

    public static ScanHistoryItem from(ScanSession session) {
        return new ScanHistoryItem(
                session.getId(),
                session.getTitle(),
                session.getScanStatus(),
                session.getMenuCount(),
                session.getRiskyMenuCount(),
                session.getScannedAt());
    }
}
