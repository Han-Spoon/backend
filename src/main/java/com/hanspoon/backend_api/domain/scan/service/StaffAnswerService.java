package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.scan.dto.StaffAnswerRequest;
import com.hanspoon.backend_api.domain.scan.dto.StaffAnswerResponse;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.scan.repository.StaffQuestionRepository;
import com.hanspoon.backend_api.domain.scan.repository.StaffQuestionRepository.AnswerTarget;
import com.hanspoon.backend_api.domain.scan.repository.StaffQuestionRepository.MenuAnswer;
import com.hanspoon.backend_api.domain.scan.repository.StaffQuestionRepository.RiskCounts;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 직원 응답 저장. AI 재판정 없이 위험 쪽으로만 반영(DECISIONS §2-3).
 * 메뉴 위험도 = 식단과 겹친 present 가 있으면 danger, 없으면 ai_risk_level.
 */
@Service
public class StaffAnswerService {

    static final String PRESENT = "present";
    static final String ABSENT = "absent";
    static final String UNKNOWN = "unknown";

    private final ScanSessionRepository scanSessionRepository;
    private final StaffQuestionRepository staffQuestionRepository;

    public StaffAnswerService(
            ScanSessionRepository scanSessionRepository, StaffQuestionRepository staffQuestionRepository) {
        this.scanSessionRepository = scanSessionRepository;
        this.staffQuestionRepository = staffQuestionRepository;
    }

    /** 다시 답하면 덮어씀. unknown 은 링크 해제 + 기존 확정값 사용 중지. */
    @Transactional
    public StaffAnswerResponse answer(UUID userId, UUID scanId, UUID questionId, StaffAnswerRequest request) {
        ScanSession session = scanSessionRepository
                .findByIdAndUserId(scanId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCAN_NOT_FOUND));
        if (session.getStoreId() == null) {
            throw new BusinessException(ErrorCode.STAFF_QUESTION_REQUIRES_STORE);
        }
        AnswerTarget target = staffQuestionRepository
                .lockForAnswer(scanId, questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_QUESTION_NOT_FOUND));
        if ("expired".equals(target.status())) {
            throw new BusinessException(ErrorCode.STAFF_QUESTION_EXPIRED);
        }

        saveAnswer(target, request.answer());

        List<MenuAnswer> answers = staffQuestionRepository.findMenuAnswers(scanId, target.storeMenuId());
        String staffAnswerStatus = staffAnswerStatus(answers);
        String riskLevel = riskLevel(
                answers, DietForbiddenTags.of(session.getDietSnapshot()), target.aiRiskLevel(), staffAnswerStatus);
        staffQuestionRepository.updateMenu(target.menuAnalysisId(), riskLevel, staffAnswerStatus);
        RiskCounts counts = staffQuestionRepository.recountScan(scanId);

        return new StaffAnswerResponse(
                staffQuestionRepository.findOne(scanId, questionId, session.getLocale()),
                new StaffAnswerResponse.Menu(target.menuAnalysisId(), RiskLevel.fromCode(riskLevel), staffAnswerStatus),
                new StaffAnswerResponse.Counts(counts.dangerCount(), counts.cautionCount(), counts.safeCount()));
    }

    private void saveAnswer(AnswerTarget target, String answer) {
        if (UNKNOWN.equals(answer)) {
            staffQuestionRepository.saveAnswer(target.questionId(), answer, null, null);
            staffQuestionRepository.invalidateConfirmations(target.confirmationId(), target.flagConfirmationId());
            return;
        }
        boolean present = PRESENT.equals(answer);
        if (target.ingredient()) {
            long id = staffQuestionRepository.upsertIngredientConfirmation(target.questionId(), present);
            staffQuestionRepository.saveAnswer(target.questionId(), answer, id, null);
        } else {
            long id = staffQuestionRepository.upsertFlagConfirmation(target.questionId(), present);
            staffQuestionRepository.saveAnswer(target.questionId(), answer, null, id);
        }
    }

    /** present·absent 가 모든 질문에 있으면 complete. 미답·unknown 이 남으면 partial. */
    static String staffAnswerStatus(List<MenuAnswer> answers) {
        boolean complete =
                answers.stream().allMatch(answer -> PRESENT.equals(answer.answer()) || ABSENT.equals(answer.answer()));
        return complete ? "complete" : "partial";
    }

    /** 안전 쪽으로 내리지 않음. SAFE 는 직원 응답 complete 일 때만(CHECK)이라 아니면 caution. */
    static String riskLevel(
            List<MenuAnswer> answers, Set<String> forbiddenTags, String aiRiskLevel, String staffAnswerStatus) {
        boolean dangerous = answers.stream()
                .anyMatch(answer -> PRESENT.equals(answer.answer())
                        && answer.tags() != null
                        && !Collections.disjoint(answer.tags(), forbiddenTags));
        if (dangerous) {
            return RiskLevel.DANGER.getCode();
        }
        if (RiskLevel.SAFE.getCode().equals(aiRiskLevel) && !"complete".equals(staffAnswerStatus)) {
            return RiskLevel.CAUTION.getCode();
        }
        return aiRiskLevel;
    }
}
