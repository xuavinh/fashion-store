ALTER TABLE vouchers
ADD COLUMN target_type VARCHAR(30) NOT NULL DEFAULT 'ALL';

ALTER TABLE vouchers
ADD CONSTRAINT chk_voucher_target_type CHECK (target_type IN ('ALL', 'SPECIFIC_PRODUCTS', 'SPECIFIC_CATEGORIES'));

CREATE TABLE voucher_products (
    voucher_id UUID NOT NULL REFERENCES vouchers(voucher_id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
    PRIMARY KEY (voucher_id, product_id)
);

CREATE TABLE voucher_categories (
    voucher_id UUID NOT NULL REFERENCES vouchers(voucher_id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(category_id) ON DELETE CASCADE,
    PRIMARY KEY (voucher_id, category_id)
);

CREATE INDEX idx_voucher_products_product ON voucher_products(product_id);
CREATE INDEX idx_voucher_categories_category ON voucher_categories(category_id);
