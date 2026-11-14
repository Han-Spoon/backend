-- Menu favorites are independent of saved scan records. Keep a snapshot so a
-- favorite still describes the judgment the user saw when they saved it.
CREATE TABLE saved_menus (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scan_session_id UUID NOT NULL REFERENCES scan_sessions(id) ON DELETE CASCADE,
    display_order INTEGER NOT NULL,
    menu_name VARCHAR(255) NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    store_id BIGINT NULL,
    store_name VARCHAR(200) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_saved_menus_user_scan_order UNIQUE (user_id, scan_session_id, display_order)
);
CREATE INDEX idx_saved_menus_user_created ON saved_menus(user_id, created_at DESC);
