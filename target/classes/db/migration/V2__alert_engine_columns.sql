-- Add container/network targeting columns to alert_rules
ALTER TABLE alert_rules
    ADD COLUMN IF NOT EXISTS container_name    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS network_interface VARCHAR(255);

-- Add per-event metadata columns to alert_history
ALTER TABLE alert_history
    ADD COLUMN IF NOT EXISTS severity              VARCHAR(20),
    ADD COLUMN IF NOT EXISTS notification_channels VARCHAR(500),
    ADD COLUMN IF NOT EXISTS operator              VARCHAR(10);
