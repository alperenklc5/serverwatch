# Phase F3 — Docker Container Management Panel

## Objective
Build a full Docker management interface showing all containers with live stats, action buttons (start/stop/restart), real-time log viewer, and container detail modal. Think of it as Portainer's container view with better design.

## Prerequisites
- Phase F2 completed — WebSocket, charts, dashboard working

## Step 1: API Layer

### src/api/docker.ts
```typescript
// listContainers(showAll?: boolean) → ContainerInfo[]
//   GET /api/docker/containers?all={showAll}
//
// getContainerStats(id: string) → ContainerStats
//   GET /api/docker/containers/{id}/stats
//
// inspectContainer(id: string) → any (full inspect response)
//   GET /api/docker/containers/{id}
//
// getContainerLogs(id: string, tail?: number) → { lines: string[] }
//   GET /api/docker/containers/{id}/logs?tail={tail}&stdout=true&stderr=true
//
// startContainer(id: string) → void
//   POST /api/docker/containers/{id}/start
//
// stopContainer(id: string, timeout?: number) → void
//   POST /api/docker/containers/{id}/stop
//
// restartContainer(id: string, timeout?: number) → void
//   POST /api/docker/containers/{id}/restart
//
// removeContainer(id: string, force?: boolean) → void
//   DELETE /api/docker/containers/{id}?force={force}
//
// getDockerInfo() → DockerInfoDTO
//   GET /api/docker/info
//
// listImages() → ImageDTO[]
//   GET /api/docker/images
```

## Step 2: Container List Page

### src/pages/ContainersPage.tsx

Layout:
```
┌─── Docker Overview ─────────────────────────────────────────────┐
│ 🟢 12 Running  │  🔴 3 Stopped  │  ⏸ 0 Paused  │  📦 8 Images │
└──────────────────────────────────────────────────────────────────┘

┌─── Filters & Search ────────────────────────────────────────────┐
│ [🔍 Search containers...]  [All ▼] [Running ▼] [Stopped ▼]     │
│                                         [☑ Show stopped]        │
└──────────────────────────────────────────────────────────────────┘

┌─── Container Grid ──────────────────────────────────────────────┐
│ ┌─────────────────────────┐  ┌─────────────────────────┐       │
│ │ 🟢 nginx                │  │ 🟢 postgres             │       │
│ │ nginx:1.25-alpine       │  │ postgres:16-alpine      │       │
│ │ Up 3 days               │  │ Up 3 days               │       │
│ │ CPU: 0.3%  RAM: 12 MB   │  │ CPU: 1.2%  RAM: 85 MB   │       │
│ │ Ports: 80→80, 443→443   │  │ Ports: 5432             │       │
│ │ [▶][⏹][🔄] [📋 Logs]    │  │ [▶][⏹][🔄] [📋 Logs]    │       │
│ └─────────────────────────┘  └─────────────────────────┘       │
│ ┌─────────────────────────┐  ┌─────────────────────────┐       │
│ │ 🔴 redis                │  │ 🟢 serverwatch          │       │
│ │ redis:7-alpine          │  │ serverwatch:latest      │       │
│ │ Exited (0) 2h ago       │  │ Up 1 hour               │       │
│ │ CPU: —     RAM: —       │  │ CPU: 4.5%  RAM: 256 MB  │       │
│ │                         │  │ 🛡 Protected             │       │
│ │ [▶][🗑]                  │  │ [📋 Logs]                │       │
│ └─────────────────────────┘  └─────────────────────────┘       │
└──────────────────────────────────────────────────────────────────┘
```

## Step 3: Components

### src/components/docker/ContainerCard.tsx
```typescript
interface ContainerCardProps {
  container: ContainerInfo;
  stats?: ContainerStats;
  onStart: () => void;
  onStop: () => void;
  onRestart: () => void;
  onRemove: () => void;
  onViewLogs: () => void;
  onInspect: () => void;
}

// Design details:
// - State indicator: colored dot (green=running, red=stopped, amber=paused)
// - Container name in font-semibold, image in text-secondary text-sm
// - Status text below image ("Up 3 days", "Exited (0) 2 hours ago")
// - CPU and RAM mini bars (thin progress bars) when running
// - Port mappings as small badges: "80:80" "443:443"
// - Action buttons row at bottom:
//   Running: Stop (square icon), Restart (refresh icon), Logs (file-text icon)
//   Stopped: Start (play icon), Remove (trash icon)
// - Protected containers (serverwatch.managed label): show shield icon, no stop/remove
// - Hover: subtle border color change to border-active
// - Click card body: opens inspect/detail modal
```

### src/components/docker/ContainerLogs.tsx
```typescript
// Full-screen modal or slide-over panel showing container logs
//
// Features:
// - Scrollable log output in monospace font
// - Auto-scroll to bottom (toggleable)
// - Color-coded: stderr in red text, stdout in normal text
// - Line numbers (optional toggle)
// - Search within logs (Ctrl+F style)
// - Tail selector: 100, 500, 1000, All
// - Auto-refresh toggle (re-fetches every 3s when enabled)
// - Copy all logs button
// - Download logs button
//
// Real-time streaming (WebSocket):
// - Subscribe to /app/container/logs/start with containerId
// - Receive lines on /user/queue/container-logs
// - Unsubscribe on close
//
// Design:
// - Dark background (darker than page bg: #08080d)
// - Green-tinted monospace text for that terminal feel
// - Timestamps in text-tertiary
// - Each log line is selectable
```

### src/components/docker/ContainerStats.tsx
```typescript
// Mini dashboard within container detail showing:
// - CPU usage line chart (last 60 readings)
// - Memory usage line chart
// - Network I/O (rx/tx bytes)
// - Block I/O (read/write)
// - Process count
//
// Uses the WebSocket /topic/containers stream
// Filter for the specific containerId
```

### src/components/docker/ContainerDetailModal.tsx
```typescript
// Full inspect view in a large modal:
// Tabs: Overview | Stats | Logs | Environment | Networking
//
// Overview: image, state, created, command, restart policy
// Stats: live charts (CPU, memory, network, block I/O)
// Logs: embedded ContainerLogs component
// Environment: list of env vars (sensitive values masked by default)
// Networking: IP addresses, ports, networks
```

### src/components/docker/DockerOverview.tsx
```typescript
// Summary bar at top of page:
// - Running count (green badge)
// - Stopped count (red badge)
// - Paused count (amber badge)
// - Image count (blue badge)
// - Docker version
// - Total CPU/RAM usage across all containers
```

## Step 4: Container Actions with Confirmation

For destructive actions (stop, remove), show a confirmation dialog:
```
┌─────────────────────────────────────────────┐
│  ⚠ Stop Container                          │
│                                             │
│  Are you sure you want to stop "nginx"?     │
│  The container will be gracefully stopped    │
│  with a 10 second timeout.                  │
│                                             │
│              [Cancel]  [Stop Container]      │
└─────────────────────────────────────────────┘
```

Use Radix Dialog/AlertDialog for this.

## Step 5: Real-time Stats via WebSocket

Subscribe to `/topic/containers` to receive live container stats every 3 seconds. Match stats to containers by containerId and update the cards in real-time.

```typescript
// In ContainersPage:
// useEffect(() => {
//   const sub = subscribe('/topic/containers', (stats: ContainerStats[]) => {
//     setContainerStats(statsMap);
//   });
//   return () => sub?.unsubscribe();
// }, []);
```

## Step 6: Toast Notifications

Add toast notifications for actions:
- "✓ Container 'nginx' started" (success, green)
- "✓ Container 'nginx' stopped" (success)
- "✗ Failed to stop container: ..." (error, red)

Use Radix Toast or a simple custom toast component.

## Acceptance Criteria
- [ ] Container list shows all containers with state, image, and status
- [ ] Running containers show live CPU/RAM stats from WebSocket
- [ ] Start/Stop/Restart buttons work and update container state
- [ ] Confirmation dialog appears before destructive actions
- [ ] Log viewer shows container logs with auto-scroll
- [ ] Real-time log streaming works via WebSocket
- [ ] Container detail modal shows full inspect info with tabs
- [ ] Search/filter containers by name
- [ ] Toggle to show/hide stopped containers
- [ ] Toast notifications on action success/failure
- [ ] Protected containers (serverwatch) can't be stopped/removed
- [ ] Stats update every 3 seconds without flickering

## Files to Create
```
src/
├── api/
│   └── docker.ts
├── components/
│   ├── docker/
│   │   ├── ContainerCard.tsx
│   │   ├── ContainerLogs.tsx
│   │   ├── ContainerStats.tsx
│   │   ├── ContainerDetailModal.tsx
│   │   └── DockerOverview.tsx
│   └── ui/
│       ├── ConfirmDialog.tsx
│       └── Toast.tsx
└── pages/
    └── ContainersPage.tsx       (replace placeholder)
```
