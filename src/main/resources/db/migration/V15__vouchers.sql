CREATE TABLE vouchers (
      voucher_id UUID PRIMARY KEY,
      code VARCHAR(50) NOT NULL UNIQUE,
      name VARCHAR(150) NOT NULL,
      description TEXT,
      discount_type VARCHAR(30) NOT NULL,
      discount_value NUMERIC(12, 2) NOT NULL CHECK (discount_value > 0),
      max_discount_amount NUMERIC(12, 2) CHECK (max_discount_amount IS NULL OR max_discount_amount > 0),
      min_order_value NUMERIC(12, 2) NOT NULL DEFAULT 0.00 CHECK (min_order_value >= 0),
      usage_limit INT NOT NULL CHECK (usage_limit > 0),
      usage_limit_per_user INT NOT NULL DEFAULT 1 CHECK (usage_limit_per_user > 0),
      used_count INT NOT NULL DEFAULT 0 CHECK (used_count >= 0),
      start_date TIMESTAMPTZ NOT NULL,
      end_date TIMESTAMPTZ NOT NULL,
      is_active BOOLEAN NOT NULL DEFAULT TRUE,
      created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
      CONSTRAINT chk_voucher_discount_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
      CONSTRAINT chk_voucher_dates CHECK (end_date > start_date),
      CONSTRAINT chk_voucher_percentage CHECK (discount_type = 'FIXED_AMOUNT' OR (discount_type = 'PERCENTAGE' AND discount_value <= 100)),
      CONSTRAINT chk_voucher_usage_limit CHECK (used_count <= usage_limit)
);

CREATE INDEX idx_vouchers_is_active ON vouchers(is_active);
CREATE INDEX idx_vouchers_dates ON vouchers(start_date, end_date);

CREATE TRIGGER trigger_update_vouchers_updated_at
BEFORE UPDATE ON vouchers
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
