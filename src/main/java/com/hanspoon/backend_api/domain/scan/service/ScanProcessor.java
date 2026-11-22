package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.ai.dto.analyze.DietProfile;
import com.hanspoon.backend_api.domain.ai.mapper.AiProfileMapper;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.upload.dto.VerifiedUpload;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.entity.UserProfile;
import com.hanspoon.backend_api.domain.user.repository.UserProfileRepository;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 비동기 스캔 처리기. Prepare → Judge 순서 지휘({@link AnalyzeScanFlow}).
 *
 * <p> @Transactional 추가 금지.
 * DB 커넥션이 점유되어 비동기 워커 수만큼 커넥션 풀이 고갈되는 것 방지하기 위함.
 * 실패 기록은 {@link ScanStateWriter} 담당.
 */
@Component
public class ScanProcessor {

    private static final Logger log = LoggerFactory.getLogger(ScanProcessor.class);

    private final UserProfileRepository userProfileRepository;
    private final ScanSessionRepository scanSessionRepository;
    private final ScanStateWriter scanStateWriter;
    private final AnalyzeScanFlow analyzeScanFlow;
    private final UserRepository userRepository;

    public ScanProcessor(
            UserProfileRepository userProfileRepository,
            ScanSessionRepository scanSessionRepository,
            ScanStateWriter scanStateWriter,
            AnalyzeScanFlow analyzeScanFlow,
            UserRepository userRepository) {
        this.userProfileRepository = userProfileRepository;
        this.scanSessionRepository = scanSessionRepository;
        this.scanStateWriter = scanStateWriter;
        this.analyzeScanFlow = analyzeScanFlow;
        this.userRepository = userRepository;
    }

    @Async("scanTaskExecutor")
    public void process(UUID scanId, UUID userId, VerifiedUpload upload, String source) {
        long processingStartedAt = System.nanoTime();
        try {
            ScanSession session = scanSessionRepository.findById(scanId).orElse(null);
            if (session == null) {
                log.warn("Scan session not found, skip processing: {}", scanId);
                return;
            }
            analyze(scanId, userId, session.getStoreId(), upload, source);
            log.info("Scan completed: {} (totalMs={})", scanId, elapsedMillis(processingStartedAt));
        } catch (Exception exception) {
            log.error(
                    "Scan failed: {} (failureCode={}, totalMs={})",
                    scanId,
                    failureCode(exception),
                    elapsedMillis(processingStartedAt),
                    exception);
            markFailedQuietly(scanId, exception);
        }
    }

    /** 프로필·언어를 먼저 읽어 AI 호출 전에 실패. 재촬영·메뉴 없음이면 Judge 생략. */
    private void analyze(UUID scanId, UUID userId, Long storeId, VerifiedUpload upload, String source) {
        DietProfile dietProfile = loadDietProfile(userId);
        String locale = userRepository
                .findById(userId)
                .map(User::getLanguageCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        long prepareStartedAt = System.nanoTime();
        var prepared = analyzeScanFlow.prepare(scanId, upload, source, locale);
        logStageCompleted(scanId, "prepare", prepareStartedAt);
        if (prepared.isEmpty() || prepared.get().analyses().isEmpty()) {
            log.info("Scan stopped after prepare: {} (retake={})", scanId, prepared.isEmpty());
            return;
        }
        long judgeStartedAt = System.nanoTime();
        analyzeScanFlow.judge(scanId, storeId, prepared.get(), dietProfile, locale);
        logStageCompleted(scanId, "judge", judgeStartedAt);
    }

    private DietProfile loadDietProfile(UUID userId) {
        UserProfile profile = userProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));
        return AiProfileMapper.toDietProfile(profile);
    }

    private void markFailedQuietly(UUID scanId, Exception cause) {
        try {
            scanStateWriter.markFailed(scanId, failureCode(cause));
        } catch (Exception exception) {
            log.error("Failed to mark scan as FAILED: {} (cause: {})", scanId, cause.toString(), exception);
        }
    }

    private String failureCode(Exception exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().getCode();
        }
        return ErrorCode.INTERNAL_SERVER_ERROR.getCode();
    }

    private void logStageCompleted(UUID scanId, String stage, long startedAt) {
        log.info("AI stage completed: {} (stage={}, durationMs={})", scanId, stage, elapsedMillis(startedAt));
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
