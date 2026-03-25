-- FinGuard Database Schema

CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id VARCHAR(64) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    merchant VARCHAR(255),
    merchant_category VARCHAR(64),
    location VARCHAR(255),
    ip_address VARCHAR(45),
    device_id VARCHAR(128),
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status VARCHAR(32) DEFAULT 'PENDING',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS fraud_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID REFERENCES transactions(id),
    fraud_score DOUBLE PRECISION NOT NULL,
    rule_triggered VARCHAR(255),
    explanation TEXT,
    status VARCHAR(32) DEFAULT 'OPEN',
    created_at TIMESTAMPTZ DEFAULT NOW()
    );

CREATE TABLE IF NOT EXISTS risk_profiles (
    account_id VARCHAR(64) PRIMARY KEY,
    risk_score DOUBLE PRECISION DEFAULT 0.0,
    transaction_count INT DEFAULT 0,
    avg_amount DECIMAL(18,2) DEFAULT 0.0,
    last_updated TIMESTAMPTZ DEFAULT NOW()
    );

CREATE INDEX idx_transactions_account ON transactions(account_id);
CREATE INDEX idx_transactions_timestamp ON transactions(timestamp);
CREATE INDEX idx_fraud_alerts_transaction ON fraud_alerts(transaction_id);
