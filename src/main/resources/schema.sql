CREATE TABLE IF NOT EXISTS users (
                                     id BIGSERIAL PRIMARY KEY,
                                     mobile_number VARCHAR(20) NOT NULL UNIQUE,
    pin VARCHAR(20) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    balance NUMERIC(15, 2) NOT NULL DEFAULT 0,
    CHECK (balance >= 0),
    CONSTRAINT valid_user_role CHECK (role IN ('ADMIN', 'USER'))
    );

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'valid_user_role'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT valid_user_role CHECK (role IN ('ADMIN', 'USER'));
    END IF;
END $$;

INSERT INTO users (mobile_number, pin, full_name, role, balance)
VALUES ('09990000000', '1234', 'JCash Administrator', 'ADMIN', 0)
ON CONFLICT (mobile_number) DO UPDATE SET
    pin = EXCLUDED.pin,
    full_name = EXCLUDED.full_name,
    role = EXCLUDED.role;

CREATE TABLE IF NOT EXISTS transactions (
                                            id BIGSERIAL PRIMARY KEY,
                                            user_id BIGINT NOT NULL REFERENCES users(id),
    type VARCHAR(30) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    CHECK (amount > 0),
    details VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT valid_transaction_type
    CHECK (type IN ('CASH_IN', 'TRANSFER_SENT', 'TRANSFER_RECEIVED'))
    );

CREATE INDEX IF NOT EXISTS idx_transactions_user_id
    ON transactions(user_id);
