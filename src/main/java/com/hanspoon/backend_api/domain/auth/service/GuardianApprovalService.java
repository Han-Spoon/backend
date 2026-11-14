package com.hanspoon.backend_api.domain.auth.service;

import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class GuardianApprovalService {
    private final JdbcTemplate jdbcTemplate;

    public GuardianApprovalService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Called inside the login transaction, before any child Google token is verified. */
    public UUID lockValidApproval(String code, String documentVersion) {
        if (code == null || !code.matches("hs-[A-Za-z0-9_-]{40,60}")) {
            throw new BusinessException(ErrorCode.GUARDIAN_CONSENT_REQUIRED);
        }
        return jdbcTemplate.query(
                "SELECT id FROM guardian_approvals WHERE code_hash = ? AND document_version = ? "
                        + "AND revoked_at IS NULL AND expires_at > now() FOR UPDATE",
                result -> result.next() ? (UUID) result.getObject("id") : null,
                sha256Hex(code), documentVersion);
    }

    public void linkToUser(UUID approvalId, UUID userId) {
        int linked = jdbcTemplate.update(
                "UPDATE guardian_approvals SET user_id = ?, linked_at = COALESCE(linked_at, now()) "
                        + "WHERE id = ? AND (user_id IS NULL OR user_id = ?)",
                userId, approvalId, userId);
        if (linked != 1) {
            throw new BusinessException(ErrorCode.GUARDIAN_CONSENT_REQUIRED);
        }
    }

    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
