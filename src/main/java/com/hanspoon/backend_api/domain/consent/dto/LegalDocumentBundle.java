package com.hanspoon.backend_api.domain.consent.dto;

import java.util.List;

public record LegalDocumentBundle(String version, String effectiveDate, List<LegalDocument> documents) {
    public record LegalDocument(String id, String title, boolean required, List<Section> sections) {}
    public record Section(String heading, String body) {}
}
