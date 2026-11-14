ALTER TABLE users ADD COLUMN age_group VARCHAR(16) NOT NULL DEFAULT 'OVER_14';
ALTER TABLE users ADD CONSTRAINT ck_users_age_group CHECK (age_group IN ('OVER_14', 'UNDER_14'));

ALTER TABLE user_consent_decisions ADD COLUMN decision_actor VARCHAR(16) NOT NULL DEFAULT 'SELF';
ALTER TABLE user_consent_decisions ADD CONSTRAINT ck_consent_actor CHECK (decision_actor IN ('SELF', 'GUARDIAN'));

CREATE TABLE guardian_approvals (
    id UUID NOT NULL PRIMARY KEY,
    code_hash VARCHAR(64) NOT NULL UNIQUE,
    guardian_email VARCHAR(255) NOT NULL,
    document_version VARCHAR(32) NOT NULL,
    evidence_reference VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    user_id UUID NULL REFERENCES users (id) ON DELETE CASCADE,
    revoked_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    linked_at TIMESTAMPTZ NULL
);
