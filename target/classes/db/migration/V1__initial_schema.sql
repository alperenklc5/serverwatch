CREATE TABLE alert_rules (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    metric_type      VARCHAR(50)  NOT NULL,
    operator         VARCHAR(10)  NOT NULL,
    threshold        DOUBLE PRECISION NOT NULL,
    cooldown_minutes INTEGER NOT NULL DEFAULT 5,
    notify_email     BOOLEAN NOT NULL DEFAULT false,
    notify_webhook   BOOLEAN NOT NULL DEFAULT false,
    webhook_url      VARCHAR(500),
    email_recipients VARCHAR(1000),
    enabled          BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE alert_history (
    id           BIGSERIAL PRIMARY KEY,
    rule_id      BIGINT REFERENCES alert_rules(id) ON DELETE CASCADE,
    metric_type  VARCHAR(50)      NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    threshold    DOUBLE PRECISION NOT NULL,
    message      TEXT,
    notified     BOOLEAN NOT NULL DEFAULT false,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE metric_snapshots (
    id          BIGSERIAL PRIMARY KEY,
    metric_type VARCHAR(50)      NOT NULL,
    metric_key  VARCHAR(255),
    value       DOUBLE PRECISION NOT NULL,
    unit        VARCHAR(20),
    recorded_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_alert_history_rule_id   ON alert_history(rule_id);
CREATE INDEX idx_alert_history_created_at ON alert_history(created_at);
CREATE INDEX idx_metric_snapshots_type_time ON metric_snapshots(metric_type, recorded_at);
