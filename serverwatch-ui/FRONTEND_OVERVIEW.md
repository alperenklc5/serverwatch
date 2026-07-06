# ServerWatch Frontend — Overview

## Vision
A dark-themed, data-dense monitoring dashboard inspired by Grafana's clarity and Vercel's polish. The UI should feel like a mission control center — information-rich but never overwhelming. Every pixel earns its place.

## Tech Stack
- **React 18+** with TypeScript
- **Vite** — fast dev server and build
- **Tailwind CSS 3.4+** — utility-first styling
- **Shadcn/ui** — headless component library (built on Radix)
- **Recharts** — metric charts and graphs
- **xterm.js** — web terminal
- **Monaco Editor** — file editing (same editor as VS Code)
- **STOMP.js + SockJS** — WebSocket connection to backend
- **Axios** — HTTP client with JWT interceptor
- **React Router v6** — client-side routing
- **Lucide React** — icon library
- **date-fns** — date formatting
- **Zustand** — lightweight state management

## Design System

### Color Palette
```
--bg-primary:     #0a0a0f    (near-black, main background)
--bg-secondary:   #12121a    (card/panel backgrounds)
--bg-tertiary:    #1a1a2e    (hover states, active items)
--border:         #2a2a3e    (subtle borders)
--border-active:  #3a3a5e    (focused/active borders)

--text-primary:   #e4e4e7    (main text)
--text-secondary: #a1a1aa    (secondary/muted text)
--text-tertiary:  #71717a    (labels, captions)

--accent-blue:    #3b82f6    (primary actions, links)
--accent-green:   #22c55e    (healthy/running/success)
--accent-amber:   #f59e0b    (warnings)
--accent-red:     #ef4444    (errors, critical alerts, stopped)
--accent-purple:  #8b5cf6    (git/branch indicators)
--accent-cyan:    #06b6d4    (network/traffic)

--chart-1:        #3b82f6    (CPU)
--chart-2:        #22c55e    (Memory)
--chart-3:        #f59e0b    (Disk)
--chart-4:        #06b6d4    (Network)
```

### Typography
```
--font-display:   "Inter", sans-serif    (headings, navigation)
--font-mono:      "JetBrains Mono", "Fira Code", monospace  (metrics, terminal, code)
--font-body:      "Inter", sans-serif    (body text)
```

### Layout Principles
- Sidebar navigation (collapsible, 64px collapsed / 240px expanded)
- Content area with max-width 1600px
- Cards with 1px border, subtle shadow, rounded-lg
- Consistent 16px/24px/32px spacing grid
- Responsive: sidebar collapses to bottom tab bar on mobile

## App Structure
```
src/
├── main.tsx
├── App.tsx
├── api/
│   ├── axios.ts              (Axios instance with JWT interceptor)
│   ├── auth.ts               (login, refresh, logout)
│   ├── metrics.ts            (system, network, process endpoints)
│   ├── docker.ts             (container CRUD)
│   ├── files.ts              (file manager endpoints)
│   ├── git.ts                (git operations)
│   ├── alerts.ts             (alert rules CRUD)
│   └── terminal.ts           (session management)
├── hooks/
│   ├── useAuth.ts
│   ├── useWebSocket.ts
│   ├── useMetrics.ts
│   └── useContainers.ts
├── stores/
│   ├── authStore.ts
│   ├── metricsStore.ts
│   └── settingsStore.ts
├── components/
│   ├── layout/
│   │   ├── Sidebar.tsx
│   │   ├── Header.tsx
│   │   ├── MainLayout.tsx
│   │   └── MobileNav.tsx
│   ├── ui/                   (shadcn components)
│   ├── charts/
│   │   ├── CpuChart.tsx
│   │   ├── MemoryChart.tsx
│   │   ├── NetworkChart.tsx
│   │   └── DiskChart.tsx
│   ├── docker/
│   │   ├── ContainerList.tsx
│   │   ├── ContainerCard.tsx
│   │   ├── ContainerLogs.tsx
│   │   └── ContainerStats.tsx
│   ├── files/
│   │   ├── FileBrowser.tsx
│   │   ├── FileEditor.tsx
│   │   ├── FileTree.tsx
│   │   └── UploadModal.tsx
│   ├── terminal/
│   │   ├── Terminal.tsx
│   │   └── TerminalTabs.tsx
│   ├── git/
│   │   ├── RepoList.tsx
│   │   ├── CommitGraph.tsx
│   │   ├── DiffViewer.tsx
│   │   └── BranchPanel.tsx
│   └── alerts/
│       ├── AlertRuleList.tsx
│       ├── AlertRuleForm.tsx
│       └── AlertHistory.tsx
├── pages/
│   ├── LoginPage.tsx
│   ├── DashboardPage.tsx
│   ├── ContainersPage.tsx
│   ├── FilesPage.tsx
│   ├── TerminalPage.tsx
│   ├── GitPage.tsx
│   ├── AlertsPage.tsx
│   └── SettingsPage.tsx
├── types/
│   └── index.ts              (all TypeScript interfaces)
└── lib/
    ├── utils.ts
    ├── formatters.ts         (bytes, percentages, dates)
    └── constants.ts
```

## Frontend Phases

| Phase | Name | Description |
|-------|------|-------------|
| F1 | Setup, Auth & Layout | Vite project, Tailwind, Shadcn, login page, JWT auth, sidebar layout |
| F2 | Dashboard | Real-time metrics with WebSocket, CPU/RAM/Disk/Network charts |
| F3 | Docker Panel | Container list, stats, start/stop/restart, log viewer |
| F4 | File Manager | Directory browser, text editor, upload/download |
| F5 | Web Terminal | xterm.js terminal with WebSocket PTY |
| F6 | Git Panel | Repo list, commit history, diff viewer, branch management |
| F7 | Alerts | Alert rule CRUD, live alert feed, history |

## Backend API Base URL
```
Development: http://localhost:8090
Production:  http://164.68.113.20:8090
```
