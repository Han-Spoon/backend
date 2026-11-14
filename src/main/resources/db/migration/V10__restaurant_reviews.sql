CREATE TABLE restaurant_reviews (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    store_id BIGINT NOT NULL REFERENCES stores(id),
    scan_session_id UUID NULL REFERENCES scan_sessions(id) ON DELETE SET NULL,
    menus JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_restaurant_reviews_menus_array CHECK (jsonb_typeof(menus) = 'array')
);
CREATE INDEX idx_restaurant_reviews_store_created ON restaurant_reviews(store_id, created_at DESC);
CREATE INDEX idx_restaurant_reviews_user_created ON restaurant_reviews(user_id, created_at DESC);

CREATE TABLE restaurant_review_reports (
    id UUID PRIMARY KEY,
    review_id UUID NOT NULL REFERENCES restaurant_reviews(id) ON DELETE CASCADE,
    reporter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_restaurant_review_report UNIQUE (review_id, reporter_id)
);
CREATE INDEX idx_restaurant_review_reports_created ON restaurant_review_reports(created_at DESC);
