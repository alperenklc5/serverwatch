CREATE TABLE user_permissions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission      VARCHAR(50) NOT NULL,
    granted_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    granted_by      BIGINT REFERENCES users(id),
    UNIQUE(user_id, permission)
);

CREATE INDEX idx_user_permissions_user_id ON user_permissions(user_id);

-- Grant the seed admin account every permission
INSERT INTO user_permissions (user_id, permission, granted_by)
SELECT id, permission, id
FROM users, (VALUES
    ('TERMINAL_ACCESS'), ('FILES_VIEW'), ('FILES_WRITE'), ('FILES_DELETE'),
    ('DOCKER_VIEW'), ('DOCKER_CONTROL'), ('DOCKER_DELETE'),
    ('GIT_VIEW'), ('GIT_WRITE'), ('ALERTS_VIEW'), ('ALERTS_MANAGE'), ('USER_MANAGEMENT')
) AS perms(permission)
WHERE users.username = 'admin';

-- Default view-only permissions for any existing non-admin users
INSERT INTO user_permissions (user_id, permission, granted_by)
SELECT u.id, p.permission, (SELECT id FROM users WHERE username = 'admin')
FROM users u, (VALUES ('FILES_VIEW'), ('DOCKER_VIEW'), ('GIT_VIEW'), ('ALERTS_VIEW')) AS p(permission)
WHERE u.username != 'admin'
ON CONFLICT (user_id, permission) DO NOTHING;
