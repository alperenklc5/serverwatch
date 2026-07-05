-- Phase 9: Users, refresh tokens, and login audit trail

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100),
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    enabled         BOOLEAN      NOT NULL DEFAULT true,
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT false,
    user_agent  VARCHAR(500),
    ip_address  VARCHAR(45),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE login_attempts (
    id           BIGSERIAL PRIMARY KEY,
    username     VARCHAR(50),
    ip_address   VARCHAR(45),
    success      BOOLEAN   NOT NULL,
    user_agent   VARCHAR(500),
    attempted_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id    ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
CREATE INDEX idx_login_attempts_username_time ON login_attempts(username, attempted_at);
CREATE INDEX idx_login_attempts_ip_time       ON login_attempts(ip_address, attempted_at);

-- Default admin user — password is bcrypt("changeme"), MUST BE CHANGED ON FIRST LOGIN
INSERT INTO users (username, email, password_hash, display_name, role)
VALUES ('admin', 'admin@localhost',
        '$2a$10$rBV2JDeWW3.vKyeQcM8fFOZUZuKBWtj8YRDcyR7HpOhOxLGvRvSPq',
        'Administrator', 'ADMIN');
