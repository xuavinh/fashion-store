CREATE TABLE product_tags (
      product_id UUID NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
      tag VARCHAR(100) NOT NULL,
      PRIMARY KEY (product_id, tag)
);

CREATE INDEX idx_product_tags_tag ON product_tags(tag);