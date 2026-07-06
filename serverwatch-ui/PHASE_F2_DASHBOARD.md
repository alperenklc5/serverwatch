# Phase F2 — Real-Time Dashboard

## Objective
Build the main dashboard page with real-time system metrics using WebSocket (STOMP). Display CPU, RAM, disk, network, and process data with live-updating charts and status cards. This is the centerpiece of the application — it should feel alive.

## Prerequisites
- Phase F1 completed — auth, layout, routing all working

## Step 1: WebSocket Hook

### src/hooks/useWebSocket.ts

Create a custom React hook that manages the STOMP WebSocket connection.

```typescript
// Responsibilities:
// - Connect to WebSocket on mount (only when authenticated)
// - Send JWT token in STOMP CONNECT headers
// - Auto-reconnect on disconnect (exponential backoff: 1s, 2s, 4s, 8s, max 30s)
// - Expose connection status: 'connecting' | 'connected' | 'disconnected' | 'error'
// - Provide subscribe(topic, callback) and unsubscribe(subscriptionId) methods
// - Clean up all subscriptions on unmount
//
// Usage pattern:
// const { isConnected, subscribe } = useWebSocket();
//
// useEffect(() => {
//   const sub = subscribe('/topic/metrics/system', (data: SystemMetric) => {
//     setMetric(data);
//   });
//   return () => sub?.unsubscribe();
// }, [subscribe]);

// WebSocket URL: import.meta.env.VITE_WS_URL
// STOMP CONNECT frame header: { Authorization: 'Bearer ' + accessToken }
```

### src/hooks/useMetrics.ts

Higher-level hook that subscribes to all metric topics and maintains state.

```typescript
// Manages:
// - latestSystemMetric: SystemMetric | null
// - systemMetricHistory: SystemMetric[] (last 60 entries = 2 minutes at 2s interval)
// - latestNetworkMetrics: NetworkMetric[]
// - networkHistory: Map<string, NetworkMetric[]> (per interface)
// - latestProcesses: ProcessInfo[]
// - uptime: UptimeInfo | null
//
// Subscribes to:
// - /topic/metrics/system → updates latestSystemMetric, appends to history
// - /topic/metrics/network → updates latestNetworkMetrics
// - /topic/metrics/processes → updates latestProcesses
//
// History management:
// - Keep max 300 entries (10 minutes of data)
// - Each entry gets a sequential index for the x-axis
//
// Also fetches uptime on mount via REST: GET /api/metrics/uptime
```

## Step 2: Dashboard Layout

### src/pages/DashboardPage.tsx

The dashboard has these sections, top to bottom:

```
┌─── Server Info Bar ──────────────────────────────────────────────┐
│ 🖥 hostname  │  Ubuntu 24.04  │  ⏱ 14d 3h 22m  │  🟢 Connected │
└──────────────────────────────────────────────────────────────────┘

┌─── Quick Stats ─────────────────────────────────────────────────┐
│ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐            │
│ │ CPU      │ │ Memory   │ │ Disk     │ │ Network  │            │
│ │ 23.5%    │ │ 81.2%    │ │ 59%      │ │ 2.3 MB/s │            │
│ │ 16 cores │ │ 13/16 GB │ │ 412/999G │ │ ↑1.2 ↓1.1│            │
│ └──────────┘ └──────────┘ └──────────┘ └──────────┘            │
└──────────────────────────────────────────────────────────────────┘

┌─── CPU Chart ────────────────────┐ ┌─── Memory Chart ────────────┐
│                                  │ │                              │
│  Area chart, 2min window         │ │  Area chart, 2min window     │
│  Y: 0-100%, X: time             │ │  Y: 0-100%, X: time         │
│  Fill: blue gradient             │ │  Fill: green gradient        │
│                                  │ │                              │
└──────────────────────────────────┘ └──────────────────────────────┘

┌─── Network Traffic ──────────────┐ ┌─── Disk Usage ──────────────┐
│                                  │ │                              │
│  Dual line: upload/download      │ │  Bar chart per mount point   │
│  Y: bytes/s, X: time            │ │  Horizontal bars with %      │
│                                  │ │                              │
└──────────────────────────────────┘ └──────────────────────────────┘

┌─── Top Processes ────────────────────────────────────────────────┐
│ PID   │ Name      │ CPU%  │ Memory    │ User    │ State         │
│ 1234  │ chrome    │ 12.3  │ 302 MB    │ admin   │ RUNNING       │
│ 5678  │ node      │  8.1  │ 156 MB    │ admin   │ RUNNING       │
│ ...                                                              │
└──────────────────────────────────────────────────────────────────┘
```

## Step 3: Components

### src/components/charts/StatCard.tsx
A single metric card showing current value with mini sparkline.

```typescript
interface StatCardProps {
  title: string;            // "CPU"
  value: string;            // "23.5%"
  subtitle: string;         // "16 cores"
  icon: LucideIcon;         // Cpu, MemoryStick, HardDrive, Network
  color: string;            // "blue", "green", "amber", "cyan"
  trend?: number[];         // last 30 values for sparkline
  alertThreshold?: number;  // if value exceeds, card border turns red/amber
}

// Design:
// - bg-secondary, border, rounded-xl, p-4
// - Icon in top-left with colored bg circle (10% opacity)
// - Value in large font-mono text (text-3xl font-semibold)
// - Subtitle in text-secondary text-sm
// - Mini sparkline at bottom (30px height, same color as icon)
// - If value > alertThreshold: border becomes accent-red, subtle red glow
// - Smooth number transition animation (count up/down)
```

### src/components/charts/CpuChart.tsx
Real-time CPU usage area chart.

```typescript
// - Recharts AreaChart
// - X axis: time labels (HH:mm:ss), showing every 10th tick
// - Y axis: 0-100%
// - Area fill: blue gradient (accent-blue at 30% opacity → transparent)
// - Stroke: accent-blue, 2px
// - Tooltip: shows exact % and timestamp
// - Animated: false (real-time data, animation causes lag)
// - Grid: subtle dashed lines in border color
// - Reference line at 80% (warning threshold) — dashed amber
// - Data: systemMetricHistory.map(m => ({ time: m.timestamp, value: m.cpuUsagePercent }))
```

### src/components/charts/MemoryChart.tsx
Same pattern as CpuChart but for memory.
- Color: accent-green gradient
- Y axis: 0-100% (or 0 to totalMemory in GB)
- Shows used/total in tooltip

### src/components/charts/NetworkChart.tsx
Dual-line chart for upload and download speeds.

```typescript
// - Two lines: receivedPerSecond (cyan, download) and sentPerSecond (purple, upload)
// - Y axis: auto-scaled, formatted as bytes/s (KB/s, MB/s)
// - Legend: "↓ Download" (cyan), "↑ Upload" (purple)
// - Filter to only show the primary network interface (the one with most traffic)
//   Ignore loopback, docker bridges, virtual interfaces
```

### src/components/charts/DiskChart.tsx
Horizontal bar chart showing disk usage per mount point.

```typescript
// - One horizontal bar per diskInfo entry
// - Bar fill color changes based on usage:
//   < 60%: accent-green
//   60-80%: accent-amber
//   > 80%: accent-red
// - Show mount point name, used/total, percentage
// - Subtle background bar showing total capacity
```

### src/components/charts/ProcessTable.tsx
Sortable table of top processes.

```typescript
// Columns: PID, Name, CPU%, Memory, User, State
// - Sortable by clicking column headers (default: CPU% desc)
// - PID and CPU% in font-mono
// - Memory formatted as human-readable (MB/GB)
// - State column: colored badges
//   RUNNING → green
//   SLEEPING → gray
//   STOPPED → red
// - Alternating row backgrounds: bg-secondary / bg-primary
// - Hover: bg-tertiary
// - Show top 20 processes
// - Auto-updates every 5 seconds (from WebSocket)
// - No flickering on update — use React keys properly
```

### src/components/charts/ServerInfoBar.tsx
Top bar with server identity and status.

```typescript
// - Hostname (from uptime data)
// - OS name + version
// - Uptime (formatted, updates every second via local timer)
// - WebSocket connection indicator (green dot + "Connected" / red dot + "Disconnected")
// - Container count badge (from health endpoint)
//
// Style: bg-secondary, border-b, py-3 px-6
// Items separated by subtle vertical dividers (border-r)
```

## Step 4: Metrics Store (Zustand)

### src/stores/metricsStore.ts
```typescript
// Central store for metric data shared across components:
// - systemMetric: SystemMetric | null
// - systemHistory: SystemMetric[]
// - networkMetrics: NetworkMetric[]
// - processes: ProcessInfo[]
// - uptime: UptimeInfo | null
// - isConnected: boolean (WebSocket status)
//
// Actions:
// - updateSystemMetric(metric) — sets latest + appends to history (max 300)
// - updateNetworkMetrics(metrics)
// - updateProcesses(processes)
// - setUptime(uptime)
// - setConnected(status)
```

## Step 5: Number Animation

Create a hook or component for smooth number transitions:

### src/components/ui/AnimatedNumber.tsx
```typescript
// Smoothly animates from one number to another over 500ms
// Uses requestAnimationFrame for smooth animation
// Formats the number using the provided formatter function
//
// Usage: <AnimatedNumber value={85.3} format={(n) => `${n.toFixed(1)}%`} />
```

## Step 6: Connection Status in Header

Update the Header component to show:
- Green pulsing dot when WebSocket is connected
- Red dot when disconnected
- Alert bell icon with unread alert count (badge)
- Subscribe to `/topic/alerts` in the header to count new alerts

## Acceptance Criteria
- [ ] Dashboard shows 4 stat cards (CPU, Memory, Disk, Network) with live values
- [ ] CPU chart updates in real-time every 2 seconds with smooth line
- [ ] Memory chart updates in real-time
- [ ] Network chart shows upload/download speeds as dual lines
- [ ] Disk usage shows horizontal bars with color-coded thresholds
- [ ] Process table shows top 20 processes, sortable by column
- [ ] Server info bar shows hostname, OS, uptime, connection status
- [ ] WebSocket auto-reconnects on disconnect
- [ ] Connection status indicator in header updates correctly
- [ ] No flickering or jank when data updates
- [ ] Charts show 2-minute sliding window of data
- [ ] All numbers formatted properly (bytes, percentages, dates)
- [ ] Responsive layout — cards stack on smaller screens

## Files to Create
```
src/
├── hooks/
│   ├── useWebSocket.ts
│   └── useMetrics.ts
├── stores/
│   └── metricsStore.ts
├── components/
│   ├── charts/
│   │   ├── StatCard.tsx
│   │   ├── CpuChart.tsx
│   │   ├── MemoryChart.tsx
│   │   ├── NetworkChart.tsx
│   │   ├── DiskChart.tsx
│   │   ├── ProcessTable.tsx
│   │   └── ServerInfoBar.tsx
│   └── ui/
│       └── AnimatedNumber.tsx
└── pages/
    └── DashboardPage.tsx        (replace placeholder)
```
