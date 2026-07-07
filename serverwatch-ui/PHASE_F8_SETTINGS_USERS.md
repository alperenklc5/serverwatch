# Phase F8 — Settings & User Management

## Objective
Build a comprehensive Settings page covering account security (change password), user management (admin creates/manages users), appearance preferences, and notification settings. This closes the critical security gap of running with the default password and adds multi-user support.

## Prerequisites
- Phases F1-F7 completed
- Backend already has: `/api/auth/change-password`, `/api/auth/register`, `/api/auth/users` (GET/PATCH/DELETE) — all implemented in Phase 9, currently unused by frontend

## Step 1: API Layer

### src/api/settings.ts
```typescript
// changePassword(currentPassword, newPassword) → void
//   POST /api/auth/change-password
//
// getUsers() → User[]
//   GET /api/auth/users
//
// createUser(username, email, password, displayName) → User
//   POST /api/auth/register
//
// toggleUserEnabled(userId, enabled) → void
//   PATCH /api/auth/users/{id}/enable  or  /disable
//
// deleteUser(userId) → void
//   DELETE /api/auth/users/{id}
//
// logoutAllSessions() → void
//   POST /api/auth/logout-all
```

## Step 2: Page Layout

### src/pages/SettingsPage.tsx

Tabbed layout:
```
┌─── Tabs ────────────────────────────────────────────────────────┐
│ [Account]  [Users]  [Appearance]  [Notifications]  [About]      │
└──────────────────────────────────────────────────────────────────┘

--- Account Tab ---
┌─── Change Password ─────────────────────────────────────────────┐
│ Current Password  [••••••••••••]                                │
│ New Password       [••••••••••••]                                │
│ Confirm Password   [••••••••••••]                                │
│                                          [Change Password]       │
│                                                                    │
│ ⚠ Changing your password will log you out of all other devices  │
└──────────────────────────────────────────────────────────────────┘

┌─── Active Session ──────────────────────────────────────────────┐
│ Logged in as: admin (Administrator)                              │
│ Role: ADMIN                                                       │
│ Last login: 2 hours ago                                          │
│                                     [Log Out All Other Devices]  │
└──────────────────────────────────────────────────────────────────┘

--- Users Tab (ADMIN only) ---
┌─── Team Members ────────────────────────────────────────────────┐
│                                              [+ Add User]        │
│ ┌────────────────────────────────────────────────────────────┐  │
│ │ 🟢 admin (Administrator)          ADMIN                     │  │
│ │    admin@localhost                                          │  │
│ │    Last login: 2 hours ago              (you)               │  │
│ └────────────────────────────────────────────────────────────┘  │
│ ┌────────────────────────────────────────────────────────────┐  │
│ │ 🟢 john (John Doe)                 USER                     │  │
│ │    john@example.com                                         │  │
│ │    Last login: 3 days ago      [Disable] [Delete]           │  │
│ └────────────────────────────────────────────────────────────┘  │
│ ┌────────────────────────────────────────────────────────────┐  │
│ │ 🔴 jane (Jane Smith) — disabled    USER                     │  │
│ │    jane@example.com                                         │  │
│ │    Last login: never           [Enable] [Delete]            │  │
│ └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘

--- Appearance Tab ---
┌─── Theme ────────────────────────────────────────────────────────┐
│ ○ Dark (default)     ○ Light     ○ System                       │
└──────────────────────────────────────────────────────────────────┘
┌─── Dashboard ────────────────────────────────────────────────────┐
│ Visible widgets:                                                  │
│ ☑ CPU          ☑ Memory        ☑ Disk         ☑ Network         │
│ ☑ Processes    ☑ Server Info                                     │
└──────────────────────────────────────────────────────────────────┘
┌─── Terminal ─────────────────────────────────────────────────────┐
│ Font size:  [14px ▼]                                             │
│ Cursor style: ○ Block  ○ Bar  ○ Underline                        │
└──────────────────────────────────────────────────────────────────┘

--- Notifications Tab ---
┌─── Browser Notifications ───────────────────────────────────────┐
│ Get desktop notifications when alerts trigger                    │
│                                          [Enable Notifications]  │
│ Status: Not enabled                                              │
└──────────────────────────────────────────────────────────────────┘
┌─── Sound ────────────────────────────────────────────────────────┐
│ ☑ Play sound on new alerts                                       │
└──────────────────────────────────────────────────────────────────┘

--- About Tab ---
┌─── ServerWatch ──────────────────────────────────────────────────┐
│ Version 1.0.0                                                    │
│ Backend: Spring Boot 3.3.6 · Java 21                             │
│ Frontend: React 18 · TypeScript                                  │
│                                                                    │
│ Server: c09372910ca1 · Alpine Linux · Docker 29.6.1              │
└──────────────────────────────────────────────────────────────────┘
```

## Step 3: Components

### src/components/settings/ChangePasswordForm.tsx
```typescript
// Form with 3 password fields
// Validation:
// - New password min 8 characters
// - Confirm password must match new password
// - Show password strength indicator (weak/medium/strong) based on length + character variety
// - Show/hide toggle on each field
// On submit: call changePassword API
// On success: show toast "Password changed. Please log in again on other devices."
// On error: show inline error (e.g., "Current password incorrect")
```

### src/components/settings/ActiveSessionCard.tsx
```typescript
// Shows current user info from authStore
// "Log Out All Other Devices" button:
// - Confirmation dialog: "This will sign you out on all devices except this one"
// - Calls logoutAllSessions API
// - Shows success toast
```

### src/components/settings/UserManagementPanel.tsx
```typescript
// ADMIN only - hide entire tab for USER role
// List all users with:
// - Avatar (initials), username, display name, email
// - Role badge (ADMIN=purple, USER=blue)
// - Enabled/disabled status (green/red dot)
// - Last login (relative time)
// - "(you)" label on current user's row
// - Actions: Enable/Disable toggle, Delete (with confirmation)
// - Current user cannot disable/delete themselves - hide those actions on own row
```

### src/components/settings/CreateUserDialog.tsx
```typescript
// Modal form:
// - Username (required, alphanumeric + underscore, min 3 chars)
// - Email (required, valid email format)
// - Display Name (required)
// - Password (required, min 8 chars, show strength indicator)
// - Role is always USER (backend defaults to USER; only way to get ADMIN is direct DB edit,
//   mention this in a small note: "New users get the USER role. Contact a system administrator to grant ADMIN access.")
// On submit: call createUser API
// On success: close dialog, refresh user list, show toast
```

### src/components/settings/ThemeSelector.tsx
```typescript
// Radio group: Dark / Light / System
// Store preference in settingsStore (Zustand, persisted to localStorage)
// NOTE: Only implement Dark theme fully in this phase (it's the existing design).
// Light and System options can be present in UI but show a "Coming soon" badge -
// don't attempt a full light theme redesign in this phase.
```

### src/components/settings/DashboardWidgetToggle.tsx
```typescript
// Checkboxes for each dashboard widget: CPU, Memory, Disk, Network, Processes, ServerInfo
// Store visibility state in settingsStore
// DashboardPage.tsx should read this and conditionally render each widget
```

### src/components/settings/TerminalPreferences.tsx
```typescript
// Font size dropdown: 10-20px
// Cursor style radio: block/bar/underline
// Store in settingsStore
// Terminal.tsx should read these values when creating the xterm instance
```

### src/components/settings/NotificationSettings.tsx
```typescript
// Browser notification permission request:
// - Check Notification.permission status
// - "Enable Notifications" button calls Notification.requestPermission()
// - Show current status: "Not enabled" / "Enabled" / "Blocked" (with instructions to unblock in browser settings)
// - Sound toggle checkbox (persisted in settingsStore)
//
// Wire into AlertLiveFeed.tsx from Phase F7:
// - When a new alert arrives AND notifications are enabled, show a browser Notification
//   with title = rule name, body = alert message
// - When sound is enabled, play a short alert sound (use a simple base64 embedded audio
//   or an Audio object with a data URI - no external file needed)
```

### src/components/settings/AboutPanel.tsx
```typescript
// Static info display
// Fetch server info from GET /api/docker/info for hostname/docker version
// Fetch from GET /api/metrics/uptime for OS name
// Show frontend version from package.json (import.meta.env or hardcoded constant)
```

## Step 4: Settings Store

### src/stores/settingsStore.ts
```typescript
// Zustand store persisted to localStorage:
// - theme: 'dark' | 'light' | 'system' (default 'dark')
// - dashboardWidgets: { cpu: bool, memory: bool, disk: bool, network: bool, processes: bool, serverInfo: bool }
//   (all default true)
// - terminalFontSize: number (default 14)
// - terminalCursorStyle: 'block' | 'bar' | 'underline' (default 'bar')
// - notificationsEnabled: boolean (default false)
// - soundEnabled: boolean (default true)
//
// Use zustand's persist middleware to save to localStorage under key 'serverwatch-settings'
```

## Step 5: Wire Dashboard Widget Visibility

Update `DashboardPage.tsx` from Phase F2 to conditionally render each section based on `settingsStore.dashboardWidgets`:

```typescript
const { dashboardWidgets } = useSettingsStore();

{dashboardWidgets.cpu && <StatCard title="CPU" ... />}
{dashboardWidgets.cpu && <CpuChart ... />}
// etc.
```

## Step 6: Wire Terminal Preferences

Update `Terminal.tsx` from Phase F5 to read font size and cursor style from settingsStore when creating the xterm.Terminal instance, instead of hardcoded values.

## Step 7: Sidebar Update

No new nav item needed — Settings already exists in the sidebar from Phase F1. Just ensure it routes to the new tabbed SettingsPage.

## Acceptance Criteria
- [ ] Change password form validates and successfully changes password
- [ ] After password change, other device sessions are invalidated (refresh tokens revoked)
- [ ] "Log Out All Other Devices" works and shows confirmation
- [ ] Users tab is hidden for USER role, visible for ADMIN
- [ ] User list shows all users with correct status badges
- [ ] Create user dialog successfully creates a new USER-role account
- [ ] Enable/Disable toggle works on user rows
- [ ] Delete user works with confirmation dialog
- [ ] Current user cannot disable/delete their own account (actions hidden)
- [ ] Theme selector shows Dark as active, Light/System show "Coming soon"
- [ ] Dashboard widget toggles actually show/hide sections on the Dashboard page
- [ ] Terminal font size and cursor style changes apply to new terminal sessions
- [ ] Browser notification permission request works
- [ ] New alerts trigger a browser notification when enabled
- [ ] Sound plays on new alerts when enabled
- [ ] About tab shows correct version, server, and stack info
- [ ] All settings persist across page reloads (localStorage)

## Files to Create
```
src/
├── api/
│   └── settings.ts
├── stores/
│   └── settingsStore.ts
├── components/
│   └── settings/
│       ├── ChangePasswordForm.tsx
│       ├── ActiveSessionCard.tsx
│       ├── UserManagementPanel.tsx
│       ├── CreateUserDialog.tsx
│       ├── ThemeSelector.tsx
│       ├── DashboardWidgetToggle.tsx
│       ├── TerminalPreferences.tsx
│       ├── NotificationSettings.tsx
│       └── AboutPanel.tsx
└── pages/
    └── SettingsPage.tsx        (replace placeholder)

MODIFY:
├── pages/DashboardPage.tsx     (read dashboardWidgets from settingsStore)
└── components/terminal/Terminal.tsx  (read font/cursor prefs from settingsStore)
```
