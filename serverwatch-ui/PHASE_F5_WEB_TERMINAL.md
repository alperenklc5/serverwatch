# Phase F5 — Web Terminal

## Objective
Implement a fully interactive web terminal using xterm.js that connects to the backend PTY via WebSocket. Support multiple terminal tabs, resize, themes, and reconnection. This should feel identical to using SSH.

## Prerequisites
- Phase F4 completed — File manager working
- xterm.js already installed in Phase F1

## Step 1: Terminal Page Layout

### src/pages/TerminalPage.tsx

```
┌─── Terminal Tabs ───────────────────────────────────────────────┐
│ [bash ×] [bash ×] [+ New Terminal]     [Shell: bash ▼] [⚙]    │
└──────────────────────────────────────────────────────────────────┘
┌─── Terminal Content ────────────────────────────────────────────┐
│ root@vps:~# ls -la                                             │
│ total 32                                                        │
│ drwxr-xr-x  5 root root 4096 Jul  5 14:00 .                   │
│ drwxr-xr-x 19 root root 4096 Jun 15 08:00 ..                  │
│ -rw-r--r--  1 root root 3106 Jul  5 14:00 .bashrc             │
│ drwxr-xr-x  3 root root 4096 Jul  5 14:00 .config             │
│ root@vps:~# _                                                   │
│                                                                  │
│                                                                  │
│                                                                  │
│                                                                  │
│                                                                  │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

## Step 2: Components

### src/components/terminal/Terminal.tsx

Core terminal component wrapping xterm.js.

```typescript
interface TerminalProps {
  sessionId: string;
  isActive: boolean;     // only active tab processes input
  onClose: () => void;
  onTitleChange: (title: string) => void;
}

// Implementation:
// 1. Create xterm.Terminal instance with options:
//    - theme: custom dark theme matching ServerWatch palette
//    - fontFamily: 'JetBrains Mono, Fira Code, monospace'
//    - fontSize: 14
//    - cursorBlink: true
//    - cursorStyle: 'bar'
//    - scrollback: 5000
//    - allowTransparency: true
//
// 2. Load addons:
//    - FitAddon — auto-fit terminal to container size
//    - WebLinksAddon — clickable URLs in terminal output
//
// 3. WebSocket integration:
//    - terminal.onData(data) → send to /app/terminal/input as INPUT message
//    - Receive OUTPUT on /user/queue/terminal → terminal.write(data)
//    - Receive CLOSED → show "Session ended" message, disable input
//
// 4. Resize handling:
//    - FitAddon.fit() on container resize (ResizeObserver)
//    - On fit, send RESIZE message with new cols/rows to /app/terminal/input
//    - Debounce resize events (100ms)
//
// 5. Reconnection:
//    - On mount, fetch buffer: GET /api/terminal/sessions/{id}/buffer
//    - Write buffer content to terminal (re-render previous output)
//
// 6. Cleanup:
//    - On unmount: send /app/terminal/close
//    - Dispose xterm instance

// xterm.js theme matching ServerWatch:
const terminalTheme = {
  background: '#08080d',       // slightly darker than app bg
  foreground: '#e4e4e7',
  cursor: '#3b82f6',           // accent-blue cursor
  cursorAccent: '#08080d',
  selectionBackground: '#3b82f640',
  selectionForeground: '#e4e4e7',
  black: '#1a1a2e',
  red: '#ef4444',
  green: '#22c55e',
  yellow: '#f59e0b',
  blue: '#3b82f6',
  magenta: '#8b5cf6',
  cyan: '#06b6d4',
  white: '#e4e4e7',
  brightBlack: '#71717a',
  brightRed: '#f87171',
  brightGreen: '#4ade80',
  brightYellow: '#fbbf24',
  brightBlue: '#60a5fa',
  brightMagenta: '#a78bfa',
  brightCyan: '#22d3ee',
  brightWhite: '#ffffff',
};
```

### src/components/terminal/TerminalTabs.tsx

Tab bar managing multiple terminal sessions.

```typescript
// State:
// - sessions: TerminalSession[] (from API)
// - activeSessionId: string
//
// Features:
// - Tab per session showing shell name ("bash", "sh", "zsh")
// - Close button (×) on each tab
// - "+" button to create new session
// - Active tab: bg-bg-primary (blends with terminal), text-primary
// - Inactive tab: bg-bg-tertiary, text-secondary
// - Tab order maintained by creation time
// - Max sessions: show warning when limit reached
//
// Creating a new session:
// 1. Send to /app/terminal/create: { shell: selectedShell, cols, rows }
// 2. Receive TerminalSessionDTO on /user/queue/terminal-created
// 3. Add to sessions, switch to new tab
//
// Shell selector dropdown:
// - Fetches available shells: GET /api/terminal/shells
// - Shows dropdown next to "+" button
```

### src/components/terminal/TerminalSettings.tsx

Settings popover (gear icon):
```typescript
// - Font size slider: 10-20px
// - Scrollback lines: 1000-10000
// - Cursor style: block | bar | underline
// - Cursor blink: on/off
// - Apply button (re-creates terminal options)
```

## Step 3: Keyboard Shortcuts

Important: the terminal must capture ALL keyboard input when focused. Prevent app-level keyboard shortcuts from interfering.

```typescript
// When terminal is focused:
// - All keystrokes go to xterm → PTY
// - Ctrl+Shift+T: new terminal tab (app shortcut, not sent to PTY)
// - Ctrl+Shift+W: close current tab (app shortcut)
// - Ctrl+Shift+←/→: switch tabs (app shortcut)
// - Ctrl+Shift+C: copy from terminal
// - Ctrl+Shift+V: paste to terminal
```

## Step 4: Full-Screen Mode

Add a maximize button that makes the terminal take the full viewport (hiding sidebar and header):

```typescript
// Toggle full-screen:
// - ESC or button to exit
// - Terminal re-fits to new dimensions
// - Sidebar and header hidden with CSS (not unmounted, just display:none)
```

## Step 5: PING Keep-Alive

Send PING messages to keep the WebSocket session alive and prevent idle timeout:

```typescript
// Every 30 seconds while terminal tab is active:
// Send to /app/terminal/input: { sessionId, type: "PING" }
```

## Acceptance Criteria
- [ ] Terminal renders with dark theme matching ServerWatch design
- [ ] Typing sends keystrokes to VPS shell in real-time
- [ ] Output from shell renders in terminal immediately
- [ ] Arrow keys, tab completion, command history all work
- [ ] Interactive commands work: vim, nano, htop, top, less
- [ ] Colored output renders correctly (ls --color, git log)
- [ ] Terminal resizes when window resizes (FitAddon)
- [ ] Multiple terminal tabs — create, switch, close
- [ ] New terminal defaults to /bin/bash
- [ ] Shell selector allows choosing sh/zsh/bash
- [ ] Copy/paste works (Ctrl+Shift+C/V or right-click)
- [ ] URLs in terminal are clickable (WebLinksAddon)
- [ ] Session reconnect loads buffer (re-renders previous output)
- [ ] Full-screen mode toggles correctly
- [ ] Closing tab sends close command to backend
- [ ] Font size and cursor style settings work

## Files to Create
```
src/
├── components/
│   └── terminal/
│       ├── Terminal.tsx
│       ├── TerminalTabs.tsx
│       └── TerminalSettings.tsx
└── pages/
    └── TerminalPage.tsx         (replace placeholder)
```
