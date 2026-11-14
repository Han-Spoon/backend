package com.hanspoon.backend_api.domain.consent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanspoon.backend_api.domain.consent.dto.ConsentDecisionRequest;
import com.hanspoon.backend_api.global.exception.BusinessException;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class ConsentServiceTest {
    private JdbcTemplate jdbcTemplate;
    private ConsentService service;

    @BeforeEach
    void setUp() throws IOException {
        jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        service = new ConsentService(jdbcTemplate, new ObjectMapper());
    }

    @Test
    void catalogContainsVersionedBodyForEveryConsent() {
        assertThat(service.getDocuments().version()).isEqualTo("2026-11-14");
        assertThat(service.getDocuments().documents()).hasSize(4).allSatisfy(document -> assertThat(document.sections())
                .isNotEmpty());
    }

    @Test
    void rejectsOutdatedVersionAndMissingRequiredConsent() {
        var approved = Map.of("service", true, "privacy", true, "sensitive", true, "marketing", false);
        assertThatThrownBy(() -> service.decide(UUID.randomUUID(), new ConsentDecisionRequest("old", approved)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.decide(
                        UUID.randomUUID(),
                        new ConsentDecisionRequest(
                                "2026-11-14",
                                Map.of("service", true, "privacy", true, "sensitive", false, "marketing", false))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void savesSeparateOptionalDecisionWithCurrentVersion() {
        service.decide(
                UUID.randomUUID(),
                new ConsentDecisionRequest(
                        "2026-11-14", Map.of("service", true, "privacy", true, "sensitive", true, "marketing", false)));
        verify(jdbcTemplate, times(4))
                .update(
                        eq("INSERT INTO user_consent_decisions (id, user_id, document_id, document_version, accepted) "
                                + "VALUES (?, ?, ?, ?, ?)"),
                        any(),
                        any(),
                        any(),
                        eq("2026-11-14"),
                        any());
    }

    @Test
    void childCannotRecordOwnConsent() {
        UUID userId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject("SELECT age_group FROM users WHERE id = ?", String.class, userId))
                .thenReturn("UNDER_14");
        assertThatThrownBy(() -> service.decide(
                        userId,
                        new ConsentDecisionRequest(
                                "2026-11-14",
                                Map.of("service", true, "privacy", true, "sensitive", true, "marketing", true))))
                .isInstanceOf(BusinessException.class);
    }
}
