CREATE TABLE voucher_usages (
    usage_id UUID PRIMARY KEY,
    voucher_id UUID NOT NULL REFERENCES vouchers(voucher_id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    order_id UUID NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    discount_amount NUMERIC(12, 2) NOT NULL CHECK (discount_amount >= 0),
    status VARCHAR(30) NOT NULL DEFAULT 'RESERVED',
    reserved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT chk_voucher_usage_status CHECK (status IN ('RESERVED', 'USED', 'CANCELLED')),
    CONSTRAINT uq_voucher_order UNIQUE (voucher_id, order_id)
);

CREATE INDEX idx_voucher_usages_user ON voucher_usages(user_id, voucher_id, status);
CREATE INDEX idx_voucher_usages_order ON voucher_usages(order_id);
