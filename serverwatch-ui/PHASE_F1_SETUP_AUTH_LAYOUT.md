# Phase F1 — Project Setup, Authentication & Layout Shell

## Objective
Initialize the React project with Vite + TypeScript, configure Tailwind CSS with the ServerWatch design system, install Shadcn/ui components, implement JWT authentication flow (login page, token management, auto-refresh), and build the main layout shell (sidebar navigation, header, protected routes).

## Step 1: Initialize Project

```bash
npm create vite@latest serverwatch-ui -- --template react-ts
cd serverwatch-ui
npm install
```

### Install Dependencies
```bash
# Core UI
npm install tailwindcss @tailwindcss/vite
npm install class-variance-authority clsx tailwind-merge
npm install lucide-react
npm install @radix-ui/react-slot @radix-ui/react-dialog @radix-ui/react-dropdown-menu @radix-ui/react-tooltip @radix-ui/react-toast @radix-ui/react-avatar @radix-ui/react-separator

# Routing & State
npm install react-router-dom
npm install zustand

# HTTP & WebSocket
npm install axios
npm install @stomp/stompjs sockjs-client
npm install @types/sockjs-client -D

# Charts (for later phases but install now)
npm install recharts

# Terminal (for later phases)
npm install @xterm/xterm @xterm/addon-fit @xterm/addon-web-links

# Date formatting
npm install date-fns

# Dev
npm install @types/react @types/react-dom -D
```

### Tailwind CSS Configuration

`src/index.css`:
```css
@import "tailwindcss";

@theme {
  --color-bg-primary: #0a0a0f;
  --color-bg-secondary: #12121a;
  --color-bg-tertiary: #1a1a2e;
  --color-border: #2a2a3e;
  --color-border-active: #3a3a5e;
  --color-text-primary: #e4e4e7;
  --color-text-secondary: #a1a1aa;
  --color-text-tertiary: #71717a;
  --color-accent-blue: #3b82f6;
  --color-accent-green: #22c55e;
  --color-accent-amber: #f59e0b;
  --color-accent-red: #ef4444;
  --color-accent-purple: #8b5cf6;
  --color-accent-cyan: #06b6d4;
}

body {
  background-color: var(--color-bg-primary);
  color: var(--color-text-primary);
  font-family: "Inter", system-ui, -apple-system, sans-serif;
}

/* Scrollbar styling */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
::-webkit-scrollbar-track {
  background: var(--color-bg-primary);
}
::-webkit-scrollbar-thumb {
  background: var(--color-border-active);
  border-radius: 3px;
}
::-webkit-scrollbar-thumb:hover {
  background: var(--color-text-tertiary);
}

/* Monospace for data */
.font-mono {
  font-family: "JetBrains Mono", "Fira Code", "Cascadia Code", monospace;
}
```

Add Inter and JetBrains Mono fonts in `index.html`:
```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
```

## Step 2: TypeScript Types

### src/types/index.ts
Define ALL types used across the frontend matching the backend DTOs:

```typescript
// ==================== AUTH ====================
export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
}

export interface User {
  id: number;
  username: string;
  email: string;
  displayName: string;
  role: 'ADMIN' | 'USER';
  enabled: boolean;
  lastLoginAt: string;
  createdAt: string;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string | null;
  timestamp: string;
}

// ==================== METRICS ====================
export interface SystemMetric {
  cpuUsagePercent: number;
  cpuCoreCount: number;
  cpuModelName: string;
  memoryTotalBytes: number;
  memoryUsedBytes: number;
  memoryFreeBytes: number;
  memoryUsagePercent: number;
  swapTotalBytes: number;
  swapUsedBytes: number;
  diskInfos: DiskInfo[];
  timestamp: string;
}

export interface DiskInfo {
  name: string;
  mountPoint: string;
  totalBytes: number;
  usableBytes: number;
  usagePercent: number;
  type: string;
}

export interface NetworkMetric {
  interfaceName: string;
  displayName: string;
  macAddress: string;
  ipv4Addresses: string[];
  bytesReceived: number;
  bytesSent: number;
  receivedPerSecond: number;
  sentPerSecond: number;
  packetsReceived: number;
  packetsSent: number;
  speed: number;
  timestamp: string;
}

export interface ProcessInfo {
  pid: number;
  name: string;
  cpuPercent: number;
  memoryBytes: number;
  memoryPercent: number;
  user: string;
  state: string;
  startTime: string;
  commandLine: string;
}

export interface UptimeInfo {
  uptimeSeconds: number;
  bootTime: string;
  formattedUptime: string;
  osName: string;
  osVersion: string;
  hostname: string;
}

// ==================== DOCKER ====================
export interface ContainerInfo {
  containerId: string;
  containerIdFull: string;
  name: string;
  image: string;
  state: 'running' | 'stopped' | 'paused' | 'restarting' | 'exited';
  status: string;
  created: string;
  ports: PortMapping[];
  networks: string[];
  volumes: string[];
  labels: Record<string, string>;
  envVars: string[];
}

export interface PortMapping {
  privatePort: number;
  publicPort: number;
  type: string;
  ip: string;
}

export interface ContainerStats {
  containerId: string;
  containerName: string;
  cpuPercent: number;
  memoryUsageBytes: number;
  memoryLimitBytes: number;
  memoryPercent: number;
  networkRxBytes: number;
  networkTxBytes: number;
  blockReadBytes: number;
  blockWriteBytes: number;
  pidCount: number;
  timestamp: string;
}

// ==================== FILES ====================
export interface FileEntry {
  name: string;
  path: string;
  relativePath: string;
  type: 'FILE' | 'DIRECTORY' | 'SYMLINK';
  size: number;
  permissions: string;
  permissionsNumeric: string;
  owner: string;
  group: string;
  modifiedAt: string;
  createdAt: string;
  isHidden: boolean;
  isReadable: boolean;
  isWritable: boolean;
  isExecutable: boolean;
  mimeType: string;
  isEditable: boolean;
  symlinkTarget: string | null;
}

export interface DirectoryListing {
  path: string;
  parentPath: string | null;
  breadcrumbs: PathBreadcrumb[];
  entries: FileEntry[];
  totalCount: number;
  directoryCount: number;
  fileCount: number;
  totalSize: number;
  isReadOnly: boolean;
}

export interface PathBreadcrumb {
  name: string;
  path: string;
}

export interface FileContent {
  path: string;
  content: string;
  encoding: string;
  lineEnding: string;
  size: number;
  lineCount: number;
  isBinary: boolean;
}

// ==================== GIT ====================
export interface GitRepo {
  repoId: string;
  name: string;
  localPath: string;
  remoteUrl: string;
  currentBranch: string;
  isClean: boolean;
  lastCommitHash: string;
  lastCommitMessage: string;
  lastCommitDate: string;
  branches: string[];
  remoteBranches: string[];
}

export interface GitCommit {
  hash: string;
  shortHash: string;
  message: string;
  author: string;
  authorEmail: string;
  date: string;
  parentHashes: string[];
  filesChanged: number;
  insertions: number;
  deletions: number;
}

export interface GitDiff {
  commitHash: string;
  entries: GitDiffEntry[];
}

export interface GitDiffEntry {
  changeType: 'ADD' | 'MODIFY' | 'DELETE' | 'RENAME' | 'COPY';
  oldPath: string;
  newPath: string;
  patch: string;
}

export interface GitBranch {
  name: string;
  isRemote: boolean;
  isCurrent: boolean;
  lastCommitHash: string;
  lastCommitMessage: string;
  lastCommitDate: string;
  trackingBranch: string | null;
  ahead: number;
  behind: number;
}

export interface GitStatus {
  branch: string;
  isClean: boolean;
  added: string[];
  changed: string[];
  removed: string[];
  untracked: string[];
  modified: string[];
  missing: string[];
  conflicting: string[];
}

// ==================== ALERTS ====================
export interface AlertRule {
  id: number;
  name: string;
  metricType: string;
  operator: string;
  threshold: number;
  containerName?: string;
  networkInterface?: string;
  cooldownMinutes: number;
  notifyEmail: boolean;
  notifyWebhook: boolean;
  webhookUrl?: string;
  emailRecipients?: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AlertEvent {
  id: number;
  ruleId: number;
  ruleName: string;
  metricType: string;
  currentValue: number;
  threshold: number;
  operator: string;
  message: string;
  severity: 'WARNING' | 'CRITICAL';
  notified: boolean;
  notificationChannels: string[];
  triggeredAt: string;
}

// ==================== TERMINAL ====================
export interface TerminalSession {
  sessionId: string;
  shell: string;
  cwd: string;
  createdAt: string;
  lastActivityAt: string;
  pid: number;
  dimensions: TerminalDimensions;
}

export interface TerminalDimensions {
  cols: number;
  rows: number;
}
```

## Step 3: Axios Instance with JWT

### src/api/axios.ts
```typescript
// Create an Axios instance with:
// - baseURL from environment variable: VITE_API_URL (default http://localhost:8090)
// - Request interceptor: attach "Authorization: Bearer <token>" from localStorage
// - Response interceptor: on 401 error, try to refresh the token
//   - If refresh succeeds, retry the original request
//   - If refresh fails, clear tokens and redirect to /login
// - Store tokens in localStorage: "accessToken", "refreshToken"
//
// Export helper functions:
//   setTokens(access, refresh) — saves to localStorage
//   clearTokens() — removes from localStorage
//   getAccessToken() — reads from localStorage
```

### src/api/auth.ts
```typescript
// login(username, password) → AuthResponse
//   POST /api/auth/login
//   On success: setTokens(), return user
//
// refresh(refreshToken) → AuthResponse
//   POST /api/auth/refresh
//
// logout() → void
//   POST /api/auth/logout with refreshToken in body
//   clearTokens()
//
// getMe() → User
//   GET /api/auth/me
//
// changePassword(currentPassword, newPassword) → void
//   POST /api/auth/change-password
```

## Step 4: Auth Store (Zustand)

### src/stores/authStore.ts
```typescript
// Zustand store managing:
// - user: User | null
// - isAuthenticated: boolean
// - isLoading: boolean (initial auth check)
//
// Actions:
// - login(username, password) — calls auth API, sets user
// - logout() — calls auth API, clears user, redirects
// - checkAuth() — on app load, tries getMe() with stored token
//   If fails, tries refresh. If refresh fails, clear everything.
// - setUser(user) — update user state
```

## Step 5: Login Page

### src/pages/LoginPage.tsx

Design specifications:
- Full-screen centered card on the dark background
- ServerWatch logo/name at the top with a subtle glow effect
- "Server Monitoring Dashboard" subtitle in text-secondary
- Username input with user icon
- Password input with lock icon, show/hide toggle
- "Sign In" button — accent-blue, full width
- Error message display below button (red text)
- Loading spinner on button during authentication
- If already authenticated, redirect to /dashboard

Visual details:
- Card: bg-secondary, border border-color, rounded-xl, shadow-2xl
- Card width: max-w-md
- Subtle grid pattern or noise texture on the background
- Small "v1.0.0" version badge at bottom right of screen

```
┌──────────────────────────────────────────────┐
│                                              │
│              ◉ ServerWatch                   │
│         Server Monitoring Dashboard          │
│                                              │
│   ┌──────────────────────────────────────┐   │
│   │  👤  Username                        │   │
│   └──────────────────────────────────────┘   │
│   ┌──────────────────────────────────────┐   │
│   │  🔒  Password                   👁   │   │
│   └──────────────────────────────────────┘   │
│                                              │
│   ┌──────────────────────────────────────┐   │
│   │            Sign In                   │   │
│   └──────────────────────────────────────┘   │
│                                              │
│   ⚠ Invalid credentials                     │
│                                              │
└──────────────────────────────────────────────┘
```

## Step 6: Main Layout Shell

### src/components/layout/Sidebar.tsx

The sidebar is the main navigation. Design:
- Fixed left, full height
- Collapsed: 64px wide (icons only with tooltips)
- Expanded: 240px wide (icons + labels)
- Toggle button at bottom
- Animated transition (transform, not width change for performance)

Navigation items:
```
◉ ServerWatch          ← logo/brand (top)
─────────────────
📊 Dashboard           → /dashboard
🐳 Containers          → /containers
📁 Files               → /files
⌨  Terminal            → /terminal
🔀 Git                 → /git
🔔 Alerts              → /alerts
─────────────────
⚙  Settings            → /settings    (bottom section)
🚪 Logout              → action
```

Each nav item:
- Icon from Lucide (LayoutDashboard, Container, FolderOpen, Terminal, GitBranch, Bell, Settings, LogOut)
- Active state: bg-tertiary, left accent-blue border (3px), text-primary
- Hover: bg-tertiary with 50% opacity
- Inactive: text-secondary

Bottom of sidebar:
- User avatar (initials) + username (when expanded)
- Collapse/expand toggle button

### src/components/layout/Header.tsx

Top bar within the content area:
- Page title (dynamic based on route)
- Right side: connection status indicator (green dot = WebSocket connected), alert badge (bell icon with unread count), user dropdown menu

### src/components/layout/MainLayout.tsx
```typescript
// Layout structure:
// <div className="flex h-screen">
//   <Sidebar />
//   <div className="flex flex-col flex-1 overflow-hidden">
//     <Header />
//     <main className="flex-1 overflow-y-auto p-6">
//       <Outlet />    ← React Router renders page here
//     </main>
//   </div>
// </div>
```

### src/components/layout/MobileNav.tsx
- Bottom tab bar on screens < 768px
- Shows 5 main nav items as icons
- Active item has accent-blue color

## Step 7: Protected Routes

### src/App.tsx
```typescript
// Router structure:
// /login              → LoginPage (public)
// /                   → redirect to /dashboard
// /dashboard          → DashboardPage (protected)
// /containers         → ContainersPage (protected)
// /files              → FilesPage (protected)
// /terminal           → TerminalPage (protected)
// /git                → GitPage (protected)
// /alerts             → AlertsPage (protected)
// /settings           → SettingsPage (protected)
//
// ProtectedRoute component:
// - Checks authStore.isAuthenticated
// - If not authenticated and still loading → show loading spinner
// - If not authenticated and done loading → redirect to /login
// - If authenticated → render children
```

## Step 8: Environment Configuration

### .env.development
```
VITE_API_URL=http://localhost:8090
VITE_WS_URL=ws://localhost:8090/ws
```

### .env.production
```
VITE_API_URL=http://164.68.113.20:8090
VITE_WS_URL=ws://164.68.113.20:8090/ws
```

## Step 9: Utility Functions

### src/lib/formatters.ts
```typescript
// formatBytes(bytes: number): string
//   → "0 B", "1.5 KB", "3.2 MB", "1.1 GB", "2.0 TB"
//
// formatPercent(value: number): string
//   → "85.2%"
//
// formatUptime(seconds: number): string
//   → "3d 14h 22m"
//
// formatDate(isoString: string): string
//   → "Jul 5, 2026, 14:30"
//
// formatRelative(isoString: string): string
//   → "2 minutes ago", "3 hours ago", "yesterday"
//
// truncate(str: string, maxLength: number): string
//   → "Long text that ge..."
```

### src/lib/utils.ts
```typescript
// cn(...inputs) — Tailwind class merger (clsx + tailwind-merge)
// sleep(ms) — Promise-based delay
// getInitials(name: string) — "Admin" → "A", "John Doe" → "JD"
```

## Step 10: Placeholder Pages

Create minimal placeholder pages for all routes so navigation works:
- DashboardPage: "Dashboard — Coming in Phase F2"
- ContainersPage: "Containers — Coming in Phase F3"
- FilesPage: "Files — Coming in Phase F4"
- TerminalPage: "Terminal — Coming in Phase F5"
- GitPage: "Git — Coming in Phase F6"
- AlertsPage: "Alerts — Coming in Phase F7"
- SettingsPage: basic settings layout

Each placeholder should use the consistent page structure:
```tsx
<div>
  <h1 className="text-2xl font-semibold mb-6">Page Title</h1>
  <div className="rounded-xl border border-border bg-bg-secondary p-8 text-center text-text-secondary">
    Coming soon
  </div>
</div>
```

## Acceptance Criteria
- [ ] `npm run dev` starts the app on localhost:5173
- [ ] Login page renders with the dark theme design
- [ ] Logging in with admin/changeme redirects to /dashboard
- [ ] Invalid credentials show error message on login page
- [ ] JWT token is stored in localStorage
- [ ] Refreshing the page preserves the authenticated session
- [ ] Sidebar navigation works — clicking items changes route
- [ ] Sidebar collapses/expands with smooth animation
- [ ] Active nav item is visually highlighted
- [ ] Logout clears tokens and redirects to login
- [ ] Unauthenticated access to /dashboard redirects to /login
- [ ] All placeholder pages render with consistent layout
- [ ] Responsive: sidebar becomes bottom tab bar on mobile
- [ ] Design matches the specified color palette and typography

## Files to Create
```
serverwatch-ui/
├── .env.development
├── .env.production
├── index.html                  (add font links)
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── index.css               (Tailwind + custom theme)
│   ├── types/
│   │   └── index.ts
│   ├── api/
│   │   ├── axios.ts
│   │   └── auth.ts
│   ├── stores/
│   │   └── authStore.ts
│   ├── lib/
│   │   ├── utils.ts
│   │   ├── formatters.ts
│   │   └── constants.ts
│   ├── components/
│   │   └── layout/
│   │       ├── Sidebar.tsx
│   │       ├── Header.tsx
│   │       ├── MainLayout.tsx
│   │       └── MobileNav.tsx
│   └── pages/
│       ├── LoginPage.tsx
│       ├── DashboardPage.tsx
│       ├── ContainersPage.tsx
│       ├── FilesPage.tsx
│       ├── TerminalPage.tsx
│       ├── GitPage.tsx
│       ├── AlertsPage.tsx
│       └── SettingsPage.tsx
```
