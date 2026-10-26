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
      search_vector tsvector,
      created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE OR REPLACE FUNCTION products_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('simple', unaccent(coalesce(NEW.name, ''))), 'A') ||
        setweight(to_tsvector('simple', coalesce(NEW.name, '')), 'A') ||
        setweight(to_tsvector('simple', unaccent(coalesce(NEW.material, ''))), 'B') ||
        setweight(to_tsvector('simple', coalesce(NEW.material, '')), 'B') ||
        setweight(to_tsvector('simple', unaccent(coalesce(NEW.description, ''))), 'C') ||
        setweight(to_tsvector('simple', coalesce(NEW.description, '')), 'C');
RETURN NEW;
END
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_products_search_vector_update ON products;
CREATE TRIGGER trg_products_search_vector_update
BEFORE INSERT OR UPDATE ON products
FOR EACH ROW EXECUTE FUNCTION products_search_vector_update();
CREATE INDEX IF NOT EXISTS idx_products_search_vector ON products USING GIN(search_vector);