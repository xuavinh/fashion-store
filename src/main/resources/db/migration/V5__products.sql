CREATE TABLE products (
      product_id UUID PRIMARY KEY,
      name VARCHAR(500) NOT NULL,
      slug VARCHAR(500) NOT NULL UNIQUE,
      description TEXT,
      brand_id UUID REFERENCES brands(brand_id) ON DELETE SET NULL,
      category_id UUID REFERENCES categories(category_id) ON DELETE SET NULL,
      gender VARCHAR(20) NOT NULL DEFAULT 'UNISEX',
      material VARCHAR(200),
      care_guide TEXT,
      is_active BOOLEAN NOT NULL DEFAULT TRUE,
      avg_rating NUMERIC(3,2) NOT NULL DEFAULT 0.00,
      total_sold INT NOT NULL DEFAULT 0,
      created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);