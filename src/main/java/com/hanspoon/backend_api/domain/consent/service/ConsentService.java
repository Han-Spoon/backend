package com.hanspoon.backend_api.domain.consent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanspoon.backend_api.domain.consent.dto.ConsentDecisionRequest;
import com.hanspoon.backend_api.domain.consent.dto.ConsentStatusResponse;
import com.hanspoon.backend_api.domain.consent.dto.LegalDocumentBundle;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentService {
    private static final Set<String> REQUIRED = Set.of("service", "privacy", "sensitive");
    private static final Set<String> DOCUMENT_IDS = Set.of("service", "privacy", "sensitive", "marketing");

    private final JdbcTemplate jdbcTemplate;
    private final LegalDocumentBundle documents;

    public ConsentService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) throws IOException {
        this.jdbcTemplate = jdbcTemplate;
        try (var input = new ClassPathResource("legal/2026-11-14.json").getInputStream()) {
            this.documents = objectMapper.readValue(input, LegalDocumentBundle.class);
        }
        if (!documents.documents().stream().map(LegalDocumentBundle.LegalDocument::id).collect(java.util.stream.Collectors.toSet()).equals(DOCUMENT_IDS)) {
            throw new IllegalStateException("Legal document catalog does not match consent IDs.");
        }
    }

    public LegalDocumentBundle getDocuments() {
        return documents;
    }

    @Transactional(readOnly = true)
    public ConsentStatusResponse getStatus(UUID userId) {
        Map<String, Boolean> decisions = new HashMap<>();
        jdbcTemplate.query(
                "SELECT DISTINCT ON (document_id) document_id, accepted FROM user_consent_decisions "
                        + "WHERE user_id = ? AND document_version = ? "
                        + "ORDER BY document_id, decided_at DESC, id DESC",
                (org.springframework.jdbc.core.RowCallbackHandler) result -> {
                    decisions.put(result.getString("document_id"), result.getBoolean("accepted"));
                },
                userId,
                documents.version());
        boolean requiredAccepted = REQUIRED.stream().allMatch(id -> Boolean.TRUE.equals(decisions.get(id)));
        boolean guardianManaged = "UNDER_14".equals(jdbcTemplate.queryForObject(
                "SELECT age_group FROM users WHERE id = ?", String.class, userId));
        boolean guardianActive = !guardianManaged || hasActiveGuardianApprovalIfMinor(userId);
        return new ConsentStatusResponse(documents.version(), decisions,
                requiredAccepted && guardianActive, guardianManaged, guardianManaged && !guardianActive);
    }

    @Transactional
    public ConsentStatusResponse decide(UUID userId, ConsentDecisionRequest request) {
        String ageGroup = jdbcTemplate.queryForObject("SELECT age_group FROM users WHERE id = ?", String.class, userId);
        if ("UNDER_14".equals(ageGroup)) {
            throw new BusinessException(ErrorCode.GUARDIAN_CONSENT_REQUIRED);
        }
        if (!documents.version().equals(request.version())
                || request.decisions() == null
                || !request.decisions().keySet().equals(DOCUMENT_IDS)
                || request.decisions().values().stream().anyMatch(value -> value == null)
                || REQUIRED.stream().anyMatch(id -> !Boolean.TRUE.equals(request.decisions().get(id)))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Current version and all required consents are needed.");
        }
        request.decisions().forEach((id, accepted) -> jdbcTemplate.update(
                "INSERT INTO user_consent_decisions (id, user_id, document_id, document_version, accepted) "
                        + "VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, id, documents.version(), accepted));
        return getStatus(userId);
    }

    @Transactional
    public void recordGuardianConsent(UUID userId) {
        if (getStatus(userId).hasRequiredConsents()) {
            return;
        }
        for (String id : DOCUMENT_IDS) {
            jdbcTemplate.update(
                    "INSERT INTO user_consent_decisions (id, user_id, document_id, document_version, accepted, decision_actor) "
                            + "VALUES (?, ?, ?, ?, ?, 'GUARDIAN')",
                    UUID.randomUUID(), userId, id, documents.version(), !id.equals("marketing"));
        }
    }

    public void requireSensitiveConsent(UUID userId) {
        if (!getStatus(userId).hasRequiredConsents()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Required consent is needed before saving a profile.");
        }
    }

    public boolean hasActiveGuardianApprovalIfMinor(UUID userId) {
        String ageGroup = jdbcTemplate.queryForObject("SELECT age_group FROM users WHERE id = ?", String.class, userId);
        if (!"UNDER_14".equals(ageGroup)) {
            return true;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM guardian_approvals WHERE user_id = ? AND document_version = ? "
                        + "AND revoked_at IS NULL AND expires_at > now()",
                Integer.class, userId, documents.version());
        return count != null && count > 0;
    }
}
