# Phase 10 — Granular Permission System

## Objective
Replace the binary ADMIN/USER role system with a granular, per-user permission system. Instead of "USER can never do X", the ADMIN assigns specific capabilities to each user individually (e.g., "this user can view Terminal but not use Files write, can restart containers but not delete them"). This also fixes the existing bug where role-based restrictions aren't being enforced at all.

## Prerequisites
- Phases 1-9 completed
- Current bug: `hasRole("ADMIN")` in SecurityConfig is not blocking USER-role accounts from ANY endpoint (confirmed: DELETE /api/files and GET /api/terminal/sessions both return 200/204 for a USER-role token). This must be diagnosed and fixed as part of this phase — do not build the new permission system on top of a broken filter chain.

## Step 0: Diagnose the Existing Authorization Bug

Before building anything new, find out why `.hasRole("ADMIN")` in `SecurityConfig.filterChain()` has no effect. Check, in order:

1. **Multiple SecurityFilterChain beans** — search for any other `@Bean public SecurityFilterChain` across the codebase. If more than one exists, only the highest-`@Order` one may be applied to a given request, silently ignoring the other.
   ```
   grep -rn "SecurityFilterChain" src/main/java/
   ```

2. **Duplicate/stale config files** — check for any `.java` files with unusual extensions or duplicate class names (this has happened before in this project — a `SecurityConfig.javay` file existed at one point and was picked up by IDE tooling but not by Maven; verify there is exactly ONE `SecurityConfig.java`).

3. **JwtAuthenticationFilter authority population** — add temporary debug logging in `JwtAuthenticationFilter` right after building the `UsernamePasswordAuthenticationToken`, log `auth.getAuthorities()` to confirm `ROLE_USER` / `ROLE_ADMIN` are actually attached.

4. **Filter ordering** — confirm `JwtAuthenticationFilter` is added via `addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)` and that nothing later in the chain overwrites `SecurityContextHolder`'s authentication with a different (unauthenticated or wrongly-scoped) one before `authorizeHttpRequests` evaluates.

5. **CorsFilter short-circuiting** — the custom `CorsFilter` (added early in the project, `@Order(Ordered.HIGHEST_PRECEDENCE)`) always calls `chain.doFilter()` for non-OPTIONS requests, but confirm it isn't accidentally wrapping the response in a way that bypasses the security filter chain's decision (e.g. committing the response early).

Fix whatever is found. Confirm the fix with a manual test before proceeding:
```bash
# USER token hitting an ADMIN-only endpoint must now return 403
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8090/api/terminal/sessions \
  -H "Authorization: Bearer $USER_TOKEN"
# Expect: 403
```

## Step 1: Permission Model

### Permission catalog (fixed set, defined in code, not user-editable)

```
TERMINAL_ACCESS          — open and use the web terminal
FILES_VIEW               — browse and read files
FILES_WRITE              — create, edit, upload, chmod files
FILES_DELETE             — delete files and directories
DOCKER_VIEW              — view containers and stats
DOCKER_CONTROL           — start/stop/restart/pause containers
DOCKER_DELETE            — remove containers
GIT_VIEW                 — view repos, commits, diffs
GIT_WRITE                — clone, pull, push, checkout, branch operations
ALERTS_VIEW              — view alert rules and history
ALERTS_MANAGE            — create/edit/delete alert rules
USER_MANAGEMENT          — create/edit/disable/delete other users (effectively "admin")
```

`USER_MANAGEMENT` is special: only a user who already has it can grant or revoke permissions for others, including granting `USER_MANAGEMENT` itself. The seed `admin` account always has all permissions and this can't be revoked via the UI (protect against accidental lockout — enforce in backend, not just frontend).

## Step 2: Database Migration

### V4__user_permissions.sql
```sql
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

-- New users default to view-only permissions (adjust as desired)
-- This is just for any users created before this migration ran;
-- going forward, the create-user flow decides default permissions explicitly.
INSERT INTO user_permissions (user_id, permission, granted_by)
SELECT u.id, p.permission, (SELECT id FROM users WHERE username = 'admin')
FROM users u, (VALUES ('FILES_VIEW'), ('DOCKER_VIEW'), ('GIT_VIEW'), ('ALERTS_VIEW')) AS p(permission)
WHERE u.username != 'admin'
ON CONFLICT (user_id, permission) DO NOTHING;
```

## Step 3: Backend — Entities & Repository

### Permission.java (enum)
```java
public enum Permission {
    TERMINAL_ACCESS,
    FILES_VIEW, FILES_WRITE, FILES_DELETE,
    DOCKER_VIEW, DOCKER_CONTROL, DOCKER_DELETE,
    GIT_VIEW, GIT_WRITE,
    ALERTS_VIEW, ALERTS_MANAGE,
    USER_MANAGEMENT
}
```

### UserPermission.java (JPA entity)
Maps to `user_permissions` table: `id`, `userId`, `permission` (enum, `@Enumerated(EnumType.STRING)`), `grantedAt`, `grantedBy`.

### UserPermissionRepository.java
```java
@Repository
public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {
    List<UserPermission> findByUserId(Long userId);
    boolean existsByUserIdAndPermission(Long userId, Permission permission);
    void deleteByUserIdAndPermission(Long userId, Permission permission);

    @Query("SELECT p.permission FROM UserPermission p WHERE p.userId = :userId")
    Set<Permission> findPermissionsByUserId(@Param("userId") Long userId);
}
```

## Step 4: Backend — Permission Service

### PermissionService.java
```java
@Service
public class PermissionService {

    private final UserPermissionRepository permissionRepo;
    private final UserRepository userRepo;

    // Cache permissions per user for the lifetime of a request or short TTL
    // (Caffeine cache, 60s TTL, keyed by userId) to avoid a DB hit on every
    // single HTTP request going through the authorization check.

    public Set<Permission> getPermissions(Long userId) {
        // Check cache, fall back to DB, populate cache
    }

    public boolean hasPermission(Long userId, Permission permission) {
        return getPermissions(userId).contains(permission);
    }

    @Transactional
    public void grantPermission(Long userId, Permission permission, Long grantedBy) {
        // Insert if not exists, invalidate cache for userId
    }

    @Transactional
    public void revokePermission(Long userId, Permission permission) {
        // Special guard: refuse to revoke USER_MANAGEMENT from the last
        // remaining user who has it (prevent total lockout).
        // Also refuse to revoke ANY permission from the seed 'admin' account.
        // Invalidate cache for userId
    }

    @Transactional
    public void setPermissions(Long userId, Set<Permission> permissions, Long grantedBy) {
        // Bulk replace: used by the "edit user permissions" UI which submits
        // the full desired set at once. Diff against current, grant new ones,
        // revoke removed ones (respecting the same guards as revokePermission).
    }

    public void invalidateCache(Long userId) { ... }
}
```

## Step 5: Backend — Custom Authorization

Replace the role-based `hasRole(...)` checks with permission-based checks. Since `@PreAuthorize` is disabled (see Phase 9 fix history — `MethodSecurityAutoConfiguration` excluded due to WebSocket thread issues), implement this as a **custom AuthorizationManager** registered in `SecurityConfig`, not as method annotations.

### PermissionAuthorizationManager.java
```java
@Component
public class PermissionAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final PermissionService permissionService;
    private final Permission requiredPermission;

    // Constructed per-endpoint-group via a factory method, e.g.:
    // PermissionAuthorizationManager.requiring(Permission.FILES_DELETE)

    public static AuthorizationManager<RequestAuthorizationContext> requiring(Permission permission) {
        return (authSupplier, context) -> {
            Authentication auth = authSupplier.get();
            if (auth == null || !auth.isAuthenticated()) {
                return new AuthorizationDecision(false);
            }
            User user = (User) auth.getPrincipal();
            boolean granted = permissionService.hasPermission(user.getId(), permission);
            return new AuthorizationDecision(granted);
        };
    }
}
```

### Updated SecurityConfig.java authorizeHttpRequests block
```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
    .requestMatchers("/api/health").permitAll()
    .requestMatchers("/ws/**").permitAll()
    .requestMatchers("/ws-test.html").permitAll()

    .requestMatchers("/api/auth/register", "/api/auth/users/**")
        .access(PermissionAuthorizationManager.requiring(Permission.USER_MANAGEMENT))

    .requestMatchers("/api/terminal/**")
        .access(PermissionAuthorizationManager.requiring(Permission.TERMINAL_ACCESS))

    .requestMatchers(HttpMethod.GET, "/api/files/**")
        .access(PermissionAuthorizationManager.requiring(Permission.FILES_VIEW))
    .requestMatchers(HttpMethod.POST, "/api/files/write", "/api/files/create", "/api/files/upload", "/api/files/chmod", "/api/files/move", "/api/files/copy")
        .access(PermissionAuthorizationManager.requiring(Permission.FILES_WRITE))
    .requestMatchers(HttpMethod.DELETE, "/api/files")
        .access(PermissionAuthorizationManager.requiring(Permission.FILES_DELETE))

    .requestMatchers(HttpMethod.GET, "/api/docker/**")
        .access(PermissionAuthorizationManager.requiring(Permission.DOCKER_VIEW))
    .requestMatchers(HttpMethod.POST, "/api/docker/containers/*/start", "/api/docker/containers/*/stop", "/api/docker/containers/*/restart", "/api/docker/containers/*/pause", "/api/docker/containers/*/unpause")
        .access(PermissionAuthorizationManager.requiring(Permission.DOCKER_CONTROL))
    .requestMatchers(HttpMethod.DELETE, "/api/docker/containers/**")
        .access(PermissionAuthorizationManager.requiring(Permission.DOCKER_DELETE))

    .requestMatchers(HttpMethod.GET, "/api/git/**")
        .access(PermissionAuthorizationManager.requiring(Permission.GIT_VIEW))
    .requestMatchers(HttpMethod.POST, "/api/git/repos/clone", "/api/git/repos/add", "/api/git/repos/*/pull", "/api/git/repos/*/push", "/api/git/repos/*/fetch", "/api/git/repos/*/checkout", "/api/git/repos/*/branches")
        .access(PermissionAuthorizationManager.requiring(Permission.GIT_WRITE))
    .requestMatchers(HttpMethod.DELETE, "/api/git/repos/**")
        .access(PermissionAuthorizationManager.requiring(Permission.GIT_WRITE))

    .requestMatchers(HttpMethod.GET, "/api/alerts/**")
        .access(PermissionAuthorizationManager.requiring(Permission.ALERTS_VIEW))
    .requestMatchers(HttpMethod.POST, "/api/alerts/rules/**", HttpMethod.PUT.name(), "/api/alerts/rules/**")
        .access(PermissionAuthorizationManager.requiring(Permission.ALERTS_MANAGE))
    .requestMatchers(HttpMethod.DELETE, "/api/alerts/rules/**")
        .access(PermissionAuthorizationManager.requiring(Permission.ALERTS_MANAGE))

    // Dashboard metrics are visible to any authenticated user - no specific
    // permission gates the core monitoring view
    .requestMatchers("/api/metrics/**").authenticated()

    .anyRequest().authenticated()
)
```

**Important:** `requestMatchers` order matters — Spring evaluates top to bottom and stops at the first match. Keep specific matchers (e.g. `POST /api/docker/containers/*/start`) before broader ones (e.g. `GET /api/docker/**`) so the more specific rule wins. Test this carefully after implementing — this exact ordering bug is a likely candidate for why the previous `hasRole` attempt silently failed.

## Step 6: Backend — WebSocket Permission Check

Update `WebSocketAuthInterceptor` to also validate `TERMINAL_ACCESS` permission before allowing `/app/terminal/**` SEND frames (URL-based `requestMatchers` don't apply to STOMP message routing, only to the initial HTTP handshake).

```java
// In preSend, after re-binding SecurityContext for non-CONNECT frames:
if (destination != null && destination.startsWith("/app/terminal/")) {
    User user = (User) ((Authentication) principal).getPrincipal();
    if (!permissionService.hasPermission(user.getId(), Permission.TERMINAL_ACCESS)) {
        throw new AccessDeniedException("Terminal access not permitted for this user");
    }
}
```

## Step 7: Backend — API for Managing Permissions

### PermissionDTO.java
```java
public record PermissionDTO(String permission, String label, String category, boolean granted) {}
```

Categories for grouping in the UI: `"Terminal"`, `"Files"`, `"Docker"`, `"Git"`, `"Alerts"`, `"Administration"`.

### Extend AuthController.java (or new PermissionController.java)
```
GET    /api/auth/users/{id}/permissions      → List<PermissionDTO> (all 12 permissions with granted flag)
PUT    /api/auth/users/{id}/permissions      → 200 OK (body: {"permissions": ["FILES_VIEW", "DOCKER_VIEW", ...]})
                                                 replaces the full set for that user
```

Both endpoints require `USER_MANAGEMENT` permission (enforced by the same `PermissionAuthorizationManager` pattern, or a simple manual check inside the controller method if these are the only two endpoints not covered by URL patterns).

### Update UserDTO to include permissions
```java
public record UserDTO(
    Long id, String username, String email, String displayName, String role,
    boolean enabled, Instant lastLoginAt, Instant createdAt,
    Set<String> permissions   // NEW - flat set of granted permission names
) {}
```

### Update createUser (register) flow
When an admin creates a new user via `/api/auth/register`, accept an optional `permissions` array in the request body. Default to `{FILES_VIEW, DOCKER_VIEW, GIT_VIEW, ALERTS_VIEW}` (view-only) if not provided.

## Step 8: Frontend — Permission-Aware UI

### src/types/index.ts additions
```typescript
export type Permission =
  | 'TERMINAL_ACCESS'
  | 'FILES_VIEW' | 'FILES_WRITE' | 'FILES_DELETE'
  | 'DOCKER_VIEW' | 'DOCKER_CONTROL' | 'DOCKER_DELETE'
  | 'GIT_VIEW' | 'GIT_WRITE'
  | 'ALERTS_VIEW' | 'ALERTS_MANAGE'
  | 'USER_MANAGEMENT';

export interface PermissionInfo {
  permission: Permission;
  label: string;
  category: string;
  granted: boolean;
}
```

Update `User` interface to include `permissions: Permission[]`.

### src/stores/authStore.ts
Add a helper: `hasPermission(permission: Permission): boolean` reading from `user.permissions`.

### src/api/settings.ts additions
```typescript
// getUserPermissions(userId) → PermissionInfo[]
//   GET /api/auth/users/{id}/permissions
//
// setUserPermissions(userId, permissions: Permission[]) → void
//   PUT /api/auth/users/{id}/permissions
```

### src/components/settings/PermissionEditor.tsx
```typescript
// Modal or expandable panel shown when clicking "Edit Permissions" on a user row
// in UserManagementPanel (from Phase F8).
//
// Layout: grouped checkboxes by category
//
// ┌─── Edit Permissions: john ──────────────────────────────────────┐
// │                                                                  │
// │ TERMINAL                                                        │
// │ ☐ Access web terminal                                          │
// │                                                                  │
// │ FILES                                                           │
// │ ☑ View files          ☐ Write/upload files    ☐ Delete files   │
// │                                                                  │
// │ DOCKER                                                          │
// │ ☑ View containers     ☐ Start/stop/restart    ☐ Remove         │
// │                                                                  │
// │ GIT                                                             │
// │ ☑ View repos          ☐ Clone/pull/push                        │
// │                                                                  │
// │ ALERTS                                                          │
// │ ☑ View alerts         ☐ Manage alert rules                     │
// │                                                                  │
// │ ADMINISTRATION                                                  │
// │ ☐ Manage users (grants full admin access)                     │
// │                                                                  │
// │                                        [Cancel]  [Save Changes] │
// └──────────────────────────────────────────────────────────────────┘
//
// If USER_MANAGEMENT is being checked, show an inline warning:
// "This will let the user manage other accounts and permissions."
//
// Disable all checkboxes (with tooltip "Cannot be changed") if this is
// the built-in 'admin' account — reflect the backend's protection.
```

### Update UserManagementPanel.tsx (from Phase F8)
Add an "Edit Permissions" button per user row (next to Enable/Disable, Delete) that opens `PermissionEditor`.

### Update CreateUserDialog.tsx (from Phase F8)
Add the same grouped-checkbox permission picker to the create-user form, so an admin sets initial permissions at creation time instead of editing immediately after.

### Route/Nav guards based on permissions
Update `Sidebar.tsx` (Phase F1) to hide nav items the current user has zero relevant permission for:
- Hide "Terminal" if no `TERMINAL_ACCESS`
- Git/Files/Containers/Alerts stay visible if the user has at least the `_VIEW` permission for that area; individual action buttons within those pages should already be disabled/hidden based on granular checks (next step)

### Gate individual actions in existing components
Go back through Phases F3 (Docker), F4 (Files), F6 (Git), F7 (Alerts) and conditionally render/disable action buttons based on `authStore.hasPermission(...)`:
- `ContainerCard.tsx`: hide Start/Stop/Restart if no `DOCKER_CONTROL`, hide Remove if no `DOCKER_DELETE`
- `FileTable.tsx` / `FileContextMenu.tsx`: hide Create/Upload/Rename/Move if no `FILES_WRITE`, hide Delete if no `FILES_DELETE`
- `RepoSelector.tsx` / `BranchPanel.tsx`: hide Clone/Pull/Push/Checkout/Create-branch if no `GIT_WRITE`
- `AlertRuleList.tsx`: hide Create/Edit/Delete rule if no `ALERTS_MANAGE`

This is defense-in-depth UI polish — the backend is the actual enforcement boundary (Step 5), but hiding controls the user can't use avoids confusing 403 errors.

## Acceptance Criteria
- [ ] Root cause of the pre-existing `hasRole` bypass bug is identified and documented
- [ ] After the fix, a token without the required permission gets 403 on protected endpoints (verified via curl, not just UI)
- [ ] Database migration creates `user_permissions` table and seeds admin with all permissions
- [ ] `PermissionService.hasPermission` correctly reflects DB state (with working cache invalidation on grant/revoke)
- [ ] Every previously role-gated endpoint (`/api/terminal/**`, docker control/delete, files write/delete, git write, alerts manage, user management) now enforces the correct specific permission
- [ ] WebSocket terminal creation is blocked for users without `TERMINAL_ACCESS`, even though the initial WS handshake succeeds
- [ ] Built-in `admin` account permissions cannot be revoked (backend rejects the attempt even if the UI is bypassed)
- [ ] Cannot revoke `USER_MANAGEMENT` from the last user who holds it
- [ ] `GET /api/auth/users/{id}/permissions` returns all 12 permissions with correct granted flags
- [ ] `PUT /api/auth/users/{id}/permissions` correctly replaces the permission set
- [ ] PermissionEditor UI in Settings → Users lets an admin grant/revoke permissions per user
- [ ] CreateUserDialog lets admin set initial permissions when creating a new account
- [ ] Sidebar nav hides Terminal for users without TERMINAL_ACCESS
- [ ] Action buttons (start/stop containers, delete files, push git, manage alerts) are hidden/disabled per-permission in their respective pages
- [ ] A user with only `FILES_VIEW` + `DOCKER_VIEW` can browse files and see containers but cannot delete a file, cannot start/stop a container, and gets no Terminal nav item at all

## Files to Create/Modify

### Backend
```
CREATE:
src/main/resources/db/migration/V4__user_permissions.sql
src/main/java/com/serverwatch/model/entity/Permission.java (enum)
src/main/java/com/serverwatch/model/entity/UserPermission.java
src/main/java/com/serverwatch/repository/UserPermissionRepository.java
src/main/java/com/serverwatch/service/PermissionService.java
src/main/java/com/serverwatch/security/PermissionAuthorizationManager.java
src/main/java/com/serverwatch/model/dto/PermissionDTO.java

MODIFY:
src/main/java/com/serverwatch/config/SecurityConfig.java   — replace hasRole with PermissionAuthorizationManager, fix root filter-chain bug
src/main/java/com/serverwatch/security/WebSocketAuthInterceptor.java — add TERMINAL_ACCESS check
src/main/java/com/serverwatch/controller/AuthController.java — permission endpoints, updated register flow
src/main/java/com/serverwatch/model/dto/UserDTO.java — add permissions field
src/main/java/com/serverwatch/service/AuthService.java — populate permissions in UserDTO responses
```

### Frontend
```
MODIFY:
src/types/index.ts                              — Permission type, PermissionInfo, User.permissions
src/stores/authStore.ts                         — hasPermission() helper
src/api/settings.ts                             — getUserPermissions, setUserPermissions
src/components/settings/UserManagementPanel.tsx — "Edit Permissions" button
src/components/settings/CreateUserDialog.tsx    — permission picker at creation
src/components/layout/Sidebar.tsx               — hide Terminal nav if no TERMINAL_ACCESS
src/components/docker/ContainerCard.tsx         — gate action buttons
src/components/files/FileTable.tsx              — gate write/delete actions
src/components/files/FileContextMenu.tsx        — gate menu items
src/components/git/RepoSelector.tsx             — gate write actions
src/components/git/BranchPanel.tsx              — gate write actions
src/components/alerts/AlertRuleList.tsx         — gate manage actions

CREATE:
src/components/settings/PermissionEditor.tsx
```
