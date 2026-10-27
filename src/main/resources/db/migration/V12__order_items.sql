CREATE TABLE order_items (
     item_id UUID PRIMARY KEY,
     order_id UUID NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
     variant_id UUID NOT NULL REFERENCES product_variants(variant_id),
     product_name VARCHAR(255) NOT NULL,
     color VARCHAR(50) NOT NULL,
     size VARCHAR(20) NOT NULL,
     sku_code VARCHAR(50) NOT NULL,
     price NUMERIC(12, 2) NOT NULL,
     quantity INT NOT NULL CHECK (quantity > 0),
     subtotal NUMERIC(12, 2) NOT NULL,
     image_url TEXT,
     created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);