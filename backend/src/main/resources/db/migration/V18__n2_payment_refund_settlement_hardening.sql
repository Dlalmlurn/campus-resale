ALTER TABLE refund_orders
    ADD COLUMN IF NOT EXISTS status_before_refund VARCHAR(40),
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS provider_refund_no VARCHAR(80),
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(500);

CREATE TABLE IF NOT EXISTS refund_evidence_files (
    refund_order_id BIGINT NOT NULL REFERENCES refund_orders (id) ON DELETE CASCADE,
    file_id BIGINT NOT NULL REFERENCES stored_files (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (refund_order_id, file_id)
);

CREATE INDEX IF NOT EXISTS idx_refund_evidence_files_file
    ON refund_evidence_files (file_id);

CREATE INDEX IF NOT EXISTS idx_payment_transactions_payment_occurred
    ON payment_transactions (payment_order_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_payment_callback_logs_payment_created
    ON payment_callback_logs (payment_order_id, created_at DESC);
