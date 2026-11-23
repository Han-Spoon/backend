package com.hanspoon.backend_api.domain.scan.controller;

import com.hanspoon.backend_api.domain.scan.dto.StaffAnswerRequest;
import com.hanspoon.backend_api.domain.scan.dto.StaffAnswerResponse;
import com.hanspoon.backend_api.domain.scan.service.StaffAnswerService;
import com.hanspoon.backend_api.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Staff Question", description = "스캔 결과 직원 질문 응답 API")
@RestController
@RequestMapping("/api/v1/scans/{scanId}/staff-questions")
public class StaffQuestionController {

    private final StaffAnswerService staffAnswerService;

    public StaffQuestionController(StaffAnswerService staffAnswerService) {
        this.staffAnswerService = staffAnswerService;
    }

    @Operation(summary = "직원 응답 저장(다시 답하면 덮어씀). 있어요+식단 겹침이면 그 메뉴 위험. 본인 스캔만")
    @PutMapping("/{questionId}/answer")
    public StaffAnswerResponse answer(
            @CurrentUser String userId,
            @PathVariable UUID scanId,
            @PathVariable UUID questionId,
            @Valid @RequestBody StaffAnswerRequest request) {
        return staffAnswerService.answer(UUID.fromString(userId), scanId, questionId, request);
    }
}
