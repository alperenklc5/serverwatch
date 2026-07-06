# ServerWatch Frontend — Claude Code Instructions

## Project Context
Read FRONTEND_OVERVIEW.md for the full design system, tech stack, and file structure.
This is a React 18 + TypeScript + Vite + Tailwind CSS project.
Backend API runs at http://localhost:8090

## Rules
- Always use TypeScript — no `any` types, proper interfaces for everything
- All types are defined in src/types/index.ts — import from there, don't redefine
- Use Tailwind utility classes only — no inline styles, no CSS modules
- Colors always from the design system variables (bg-bg-primary, text-text-secondary, etc.)
- Icons always from lucide-react — no other icon libraries
- All API calls go through src/api/ files — never call axios directly in components
- Use Zustand stores for global state, useState for local component state
- Format bytes/dates using src/lib/formatters.ts helpers
- Components are functional with hooks — no class components
- File naming: PascalCase for components, camelCase for hooks and utils

## Design System
Dark theme — near-black backgrounds, subtle borders, accent colors for status:
- bg-bg-primary (#0a0a0f) — page background
- bg-bg-secondary (#12121a) — cards and panels
- bg-bg-tertiary (#1a1a2e) — hover states
- text-text-primary (#e4e4e7) — main text
- text-text-secondary (#a1a1aa) — muted text
- accent-blue (#3b82f6) — primary actions
- accent-green (#22c55e) — success/running
- accent-amber (#f59e0b) — warnings
- accent-red (#ef4444) — errors/stopped

## Current Phase
Currently working on: Phase F1

## Completed Phases
- [x] Phase F1 — Setup, Auth & Layout
- [ ] Phase F2 — Dashboard
- [ ] Phase F3 — Docker Panel
- [ ] Phase F4 — File Manager
- [ ] Phase F5 — Web Terminal
- [ ] Phase F6 — Git Panel
- [ ] Phase F7 — Alerts