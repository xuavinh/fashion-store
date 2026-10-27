CREATE TABLE reviews (
     review_id UUID PRIMARY KEY,
     product_id UUID NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
     variant_id UUID REFERENCES product_variants(variant_id) ON DELETE SET NULL,
     user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
     order_id UUID,
     rating SMALLINT NOT NULL CHECK (rating >= 1 AND rating <= 5),
     fit_rating VARCHAR(20) NOT NULL DEFAULT 'TRUE_TO_SIZE',
     quality_rating SMALLINT CHECK (quality_rating >= 1 AND quality_rating <= 5),
     body TEXT,
     images JSONB,
     helpful_count INT NOT NULL DEFAULT 0,
     reviewer_size VARCHAR(20),
     reviewer_height SMALLINT,
     status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
     created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reviews_product_id ON reviews(product_id);
CREATE INDEX idx_reviews_user_id ON reviews(user_id);
