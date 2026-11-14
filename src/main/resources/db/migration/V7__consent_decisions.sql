CREATE TABLE user_consent_decisions (
    id UUID NOT NULL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    document_id VARCHAR(32) NOT NULL,
    document_version VARCHAR(32) NOT NULL,
    accepted BOOLEAN NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_consent_document_id CHECK (document_id IN ('service', 'privacy', 'sensitive', 'marketing'))
);

CREATE INDEX idx_user_consent_decisions_latest
    ON user_consent_decisions (user_id, document_id, decided_at DESC);
