package com.hanspoon.backend_api.domain.consent.controller;

import com.hanspoon.backend_api.domain.consent.dto.ConsentDecisionRequest;
import com.hanspoon.backend_api.domain.consent.dto.ConsentStatusResponse;
import com.hanspoon.backend_api.domain.consent.dto.LegalDocumentBundle;
import com.hanspoon.backend_api.domain.consent.service.ConsentService;
import com.hanspoon.backend_api.global.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/consents")
public class ConsentController {
    private final ConsentService consentService;

    public ConsentController(ConsentService consentService) {
        this.consentService = consentService;
    }

    @GetMapping("/documents")
    public LegalDocumentBundle documents() {
        return consentService.getDocuments();
    }

    @GetMapping("/me")
    public ConsentStatusResponse status(@CurrentUser String userId) {
        return consentService.getStatus(UUID.fromString(userId));
    }

    @PostMapping("/me")
    public ConsentStatusResponse decide(@CurrentUser String userId, @Valid @RequestBody ConsentDecisionRequest request) {
        return consentService.decide(UUID.fromString(userId), request);
    }
}
