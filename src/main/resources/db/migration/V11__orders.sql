CREATE TABLE orders (
    order_id UUID PRIMARY KEY,
    order_code VARCHAR(50) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(user_id),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    payment_method VARCHAR(30) NOT NULL DEFAULT 'COD',
    payment_status VARCHAR(30) NOT NULL DEFAULT 'UNPAID',
    shipping_name VARCHAR(100) NOT NULL,
    shipping_phone VARCHAR(20) NOT NULL,
    shipping_address VARCHAR(500) NOT NULL,
    shipping_province VARCHAR(100),
    shipping_district VARCHAR(100),
    shipping_ward VARCHAR(100),
    note TEXT,
    subtotal NUMERIC(12, 2) NOT NULL CHECK (subtotal >= 0),
    shipping_fee NUMERIC(12, 2) NOT NULL DEFAULT 0.00 CHECK (shipping_fee >= 0),
    discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00 CHECK (discount_amount >= 0),
    total_amount NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at);

DROP TRIGGER IF EXISTS trg_orders_updated_at ON orders;
CREATE TRIGGER trg_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
