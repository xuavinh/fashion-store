CREATE TABLE size_guides (
     guide_id UUID PRIMARY KEY,
     category_id UUID NOT NULL REFERENCES categories(category_id) ON DELETE CASCADE,
     gender VARCHAR(20) NOT NULL DEFAULT 'UNISEX',
     size_chart JSONB NOT NULL,
     created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_size_guides_category_id ON size_guides(category_id);
