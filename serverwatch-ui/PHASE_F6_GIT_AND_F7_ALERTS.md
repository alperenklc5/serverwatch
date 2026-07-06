# Phase F6 — Git Panel

## Objective
Build a GitKraken-inspired Git management interface with repo list, commit history graph, diff viewer, branch management, and remote operations (pull/push). Users should never need to touch the CLI for Git.

## Prerequisites
- Phase F5 completed — Terminal working

## Step 1: API Layer

### src/api/git.ts
```typescript
// listRepos() → GitRepo[]                          GET /api/git/repos
// getRepo(id) → GitRepo                            GET /api/git/repos/{id}
// cloneRepo(url, name, branch?) → GitRepo           POST /api/git/repos/clone
// addExistingRepo(path, name) → GitRepo             POST /api/git/repos/add
// removeRepo(id) → void                             DELETE /api/git/repos/{id}
// getStatus(id) → GitStatus                         GET /api/git/repos/{id}/status
// getLog(id, branch?, limit?, skip?) → GitCommit[]  GET /api/git/repos/{id}/log
// getDiff(id, hash) → GitDiff                       GET /api/git/repos/{id}/commits/{hash}/diff
// getBranches(id) → GitBranch[]                     GET /api/git/repos/{id}/branches
// createBranch(id, name, startPoint?) → GitBranch   POST /api/git/repos/{id}/branches
// deleteBranch(id, name) → void                     DELETE /api/git/repos/{id}/branches/{name}
// checkout(id, branch, createNew?) → GitRepo        POST /api/git/repos/{id}/checkout
// pull(id, remote?) → GitRepo                       POST /api/git/repos/{id}/pull
// push(id, remote?, branch?) → void                 POST /api/git/repos/{id}/push
// fetch(id, remote?) → void                         POST /api/git/repos/{id}/fetch
```

## Step 2: Page Layout

### src/pages/GitPage.tsx

```
┌─── Repo Selector ───────────────────────────────────────────────┐
│ [📦 serverwatch ▼]  [🔄 Fetch] [⬇ Pull] [⬆ Push] [+ Clone]   │
│ main ← origin/main (✓ clean, 0↑ 0↓)                           │
└──────────────────────────────────────────────────────────────────┘

┌─── Left: Branch List ────────┐ ┌─── Right: Commit History ──────┐
│ LOCAL BRANCHES               │ │ ● abc1234 Fix login bug        │
│  ● main (current)            │ │ │ John · 2h ago                │
│    feature/auth              │ │ ● def5678 Add user validation  │
│    fix/docker-stats          │ │ │ John · 5h ago                │
│                              │ │ ● 789abcd Merge branch 'fix'  │
│ REMOTE BRANCHES              │ │ │╲                              │
│    origin/main               │ │ │ ● aaa1111 Fix typo           │
│    origin/feature/auth       │ │ │╱                              │
│                              │ │ ● bbb2222 Initial commit       │
│                              │ │   John · 3d ago                │
└──────────────────────────────┘ └──────────────────────────────────┘

┌─── Bottom: Diff Viewer ─────────────────────────────────────────┐
│ Selected commit: abc1234 — "Fix login bug"                      │
│ 3 files changed, +12 insertions, -5 deletions                   │
│                                                                  │
│ ┌── src/auth/AuthService.java ─── MODIFIED ──────────────────┐  │
│ │ @@ -45,7 +45,9 @@                                         │  │
│ │   public AuthResponse login(LoginRequest req) {            │  │
│ │ -     if (user == null) {                                   │  │
│ │ +     if (user == null || !user.isEnabled()) {              │  │
│ │ +         log.warn("Login failed for: {}", req.username()); │  │
│ │           throw new SecurityException("Invalid");           │  │
│ │       }                                                     │  │
│ └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

## Step 3: Components

### src/components/git/RepoSelector.tsx
- Dropdown listing all registered repos
- Current branch name with ahead/behind badges
- Clean/dirty indicator (green check or orange dot)
- Action buttons: Fetch, Pull, Push, Clone new

### src/components/git/BranchPanel.tsx
- Tree view of local and remote branches
- Current branch highlighted with dot indicator
- Double-click branch → checkout
- Right-click → context menu: Checkout, Delete, Create branch from here
- Ahead/behind counts for tracked branches
- Create branch button at top

### src/components/git/CommitHistory.tsx
- Vertical list of commits
- Each commit: short hash (mono, colored), message, author, relative date
- Commit graph lines on the left (SVG) showing merge topology
- Click commit → load diff in bottom panel
- Selected commit highlighted
- Pagination: "Load more" at bottom or infinite scroll

### src/components/git/DiffViewer.tsx
- Shows diff for selected commit
- File list sidebar: changed files with +/- counts and change type badges
- Unified diff view with syntax highlighting
- Added lines: green background
- Removed lines: red background
- Line numbers for old and new
- Collapsible file sections
- "No changes" state when nothing selected

### src/components/git/CloneDialog.tsx
- Repository URL input
- Name input (auto-generated from URL)
- Branch input (default: main)
- Clone button with progress indicator

### src/components/git/GitStatusBar.tsx
- Shows working tree status: modified, untracked, staged file counts
- Clean indicator when no changes

## Acceptance Criteria
- [ ] Repo list shows all registered repos with current branch
- [ ] Commit history loads with hash, message, author, date
- [ ] Clicking a commit shows file-by-file diff
- [ ] Diff viewer shows added/removed lines with colors
- [ ] Branch list shows local and remote branches
- [ ] Checkout branch works from branch panel
- [ ] Pull and Push buttons work with success/error toasts
- [ ] Clone dialog clones a public repo successfully
- [ ] Branch create/delete works
- [ ] Clean/dirty status indicator updates correctly

## Files to Create
```
src/
├── api/
│   └── git.ts
├── components/
│   └── git/
│       ├── RepoSelector.tsx
│       ├── BranchPanel.tsx
│       ├── CommitHistory.tsx
│       ├── DiffViewer.tsx
│       ├── CloneDialog.tsx
│       └── GitStatusBar.tsx
└── pages/
    └── GitPage.tsx              (replace placeholder)
```

---

# Phase F7 — Alert Management

## Objective
Build the alert management interface for creating rules, viewing live alert feed, and browsing alert history. Rules should be easy to create with a form, and alerts should appear in real-time via WebSocket.

## Prerequisites
- Phase F6 completed — Git panel working

## Step 1: API Layer

### src/api/alerts.ts
```typescript
// listRules() → AlertRule[]                     GET /api/alerts/rules
// getRule(id) → AlertRule                       GET /api/alerts/rules/{id}
// createRule(rule) → AlertRule                  POST /api/alerts/rules
// updateRule(id, rule) → AlertRule              PUT /api/alerts/rules/{id}
// deleteRule(id) → void                         DELETE /api/alerts/rules/{id}
// toggleRule(id, enabled) → void                PATCH /api/alerts/rules/{id}/toggle
// testRule(id) → void                           POST /api/alerts/rules/{id}/test
// getHistory(hours?, limit?) → AlertEvent[]     GET /api/alerts/history?hours={h}&limit={l}
// getRuleHistory(ruleId, limit?) → AlertEvent[] GET /api/alerts/history/rule/{id}?limit={l}
```

## Step 2: Page Layout

### src/pages/AlertsPage.tsx

```
┌─── Tabs ────────────────────────────────────────────────────────┐
│ [Rules]  [Live Feed]  [History]                                 │
└──────────────────────────────────────────────────────────────────┘

--- Rules Tab ---
┌─── Alert Rules ─────────────────────────────────────────────────┐
│                                              [+ Create Rule]    │
│ ┌───────────────────────────────────────────────────────────┐   │
│ │ 🟢 High CPU Warning                                       │   │
│ │ CPU_USAGE > 80%  │  Cooldown: 5min  │  📧 Email  💬 Webhook│   │
│ │ Last triggered: 2h ago                    [Edit] [🗑]     │   │
│ └───────────────────────────────────────────────────────────┘   │
│ ┌───────────────────────────────────────────────────────────┐   │
│ │ 🟢 Memory Critical                                        │   │
│ │ MEMORY_USAGE > 90%  │  Cooldown: 10min  │  📧 Email       │   │
│ │ Last triggered: never                     [Edit] [🗑]     │   │
│ └───────────────────────────────────────────────────────────┘   │
│ ┌───────────────────────────────────────────────────────────┐   │
│ │ 🔴 Disk Space Low (disabled)                              │   │
│ │ DISK_USAGE > 85%  │  Cooldown: 30min  │  💬 Webhook       │   │
│ │ [Enable]                                  [Edit] [🗑]     │   │
│ └───────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘

--- Live Feed Tab ---
┌─── Live Alerts (WebSocket) ─────────────────────────────────────┐
│ 🔴 CRITICAL  CPU usage is 96.3% (threshold: > 80%)    14:30:22 │
│ 🟡 WARNING   Memory usage is 85.1% (threshold: > 80%) 14:28:15 │
│ 🔴 CRITICAL  CPU usage is 92.8% (threshold: > 80%)    14:25:10 │
│                                                                  │
│ Waiting for alerts...                                            │
└──────────────────────────────────────────────────────────────────┘

--- History Tab ---
┌─── Alert History ───────────────────────────────────────────────┐
│ Time Range: [Last 24h ▼]  Rule: [All ▼]  Severity: [All ▼]    │
│                                                                  │
│ Time     │ Severity │ Rule          │ Value  │ Threshold │ Sent  │
│ 14:30:22 │ CRITICAL │ High CPU      │ 96.3%  │ > 80%     │ ✓    │
│ 14:28:15 │ WARNING  │ Memory Crit   │ 85.1%  │ > 80%     │ ✓    │
│ 12:15:00 │ WARNING  │ High CPU      │ 82.5%  │ > 80%     │ ✓    │
│ ...                                                              │
└──────────────────────────────────────────────────────────────────┘
```

## Step 3: Components

### src/components/alerts/AlertRuleList.tsx
- List of all rules with status indicator
- Toggle switch to enable/disable
- Edit and Delete buttons
- "Test" button to send a test notification
- Create Rule button opens form

### src/components/alerts/AlertRuleForm.tsx
Create/edit rule form in a modal:

```typescript
// Fields:
// - Name (text input)
// - Metric Type (select): CPU_USAGE, MEMORY_USAGE, DISK_USAGE, SWAP_USAGE,
//   CONTAINER_CPU, CONTAINER_MEMORY
// - Operator (select): > (GT), >= (GTE), < (LT), <= (LTE), = (EQ)
// - Threshold (number input with % suffix)
// - Container Name (text, shown only for CONTAINER_* metrics)
// - Cooldown Minutes (number input, min 1)
// - Notify Email (toggle) → email recipients input (comma-separated)
// - Notify Webhook (toggle) → webhook URL input
//   Auto-detect: "Discord webhook" / "Slack webhook" / "Generic" label
// - Enabled (toggle)
//
// Validation:
// - Name required
// - Threshold must be positive
// - If email enabled, recipients required
// - If webhook enabled, URL required and must be valid
//
// Submit: POST for create, PUT for update
```

### src/components/alerts/AlertLiveFeed.tsx
Real-time alert display from WebSocket.

```typescript
// Subscribe to /topic/alerts
// Each alert appears as a card that slides in from the top
// Severity colors:
//   CRITICAL: red border-left, red icon
//   WARNING: amber border-left, amber icon
// Shows: severity badge, message, timestamp, notification channels used
// Sound notification option (toggle): play a subtle alert sound
// Max 50 items in feed (FIFO)
```

### src/components/alerts/AlertHistory.tsx
Historical alert table with filters.

```typescript
// Filters:
// - Time range: 1h, 6h, 24h, 7d, 30d
// - Rule filter: dropdown of all rules
// - Severity filter: All, WARNING, CRITICAL
//
// Table columns: Time, Severity, Rule, Metric Value, Threshold, Notified
// Pagination or infinite scroll
// Click row: expand to show full details
```

### Alert Badge in Header
Update Header.tsx to show unread alert count. Subscribe to `/topic/alerts` and increment badge. Clicking bell icon navigates to /alerts.

## Acceptance Criteria
- [ ] Alert rules list displays all rules with correct status
- [ ] Create rule form validates and saves new rules
- [ ] Edit rule pre-fills form with existing values
- [ ] Delete rule with confirmation dialog
- [ ] Toggle rule enabled/disabled works
- [ ] Test notification button triggers test alert
- [ ] Live feed shows alerts in real-time via WebSocket
- [ ] Severity colors (red=critical, amber=warning) display correctly
- [ ] Alert history loads with filters working
- [ ] Time range filter changes results
- [ ] Header bell icon shows unread alert count
- [ ] Clicking bell icon navigates to alerts page

## Files to Create
```
src/
├── api/
│   └── alerts.ts
├── components/
│   └── alerts/
│       ├── AlertRuleList.tsx
│       ├── AlertRuleForm.tsx
│       ├── AlertLiveFeed.tsx
│       └── AlertHistory.tsx
└── pages/
    └── AlertsPage.tsx           (replace placeholder)
```
