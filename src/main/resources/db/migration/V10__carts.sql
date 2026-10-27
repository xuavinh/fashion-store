CREATE TABLE carts (
    cart_id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE cart_items (
    item_id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES carts(cart_id) ON DELETE CASCADE,
    variant_id UUID NOT NULL REFERENCES product_variants(variant_id) ON DELETE CASCADE,
    quantity INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cart_variant UNIQUE (cart_id, variant_id)
);

CREATE INDEX idx_cart_items_cart_id ON cart_items(cart_id);
