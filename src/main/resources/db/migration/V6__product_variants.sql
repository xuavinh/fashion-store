CREATE TABLE product_variants (
      variant_id UUID PRIMARY KEY,
      product_id UUID NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
      color VARCHAR(50) NOT NULL,
      color_code VARCHAR(10),
      size VARCHAR(20) NOT NULL,
      sku_code VARCHAR(100) NOT NULL UNIQUE,
      price NUMERIC(12, 2) NOT NULL,
      compare_price NUMERIC(12, 2),
      stock_qty INT NOT NULL DEFAULT 0,
      weight_g INT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_variants_product_id ON product_variants(product_id);
CREATE INDEX idx_product_variants_sku_code ON product_variants(sku_code);
