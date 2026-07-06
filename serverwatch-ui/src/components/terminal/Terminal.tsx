import { useEffect, useRef } from 'react'
import { Terminal as XTerm } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import { WebLinksAddon } from '@xterm/addon-web-links'
import '@xterm/xterm/css/xterm.css'
import { getSessionBuffer } from '../../api/terminal'
import type { TerminalSocketHook } from '../../hooks/useTerminalSocket'
import type { TerminalSettings } from './TerminalSettings'
import { cn } from '../../lib/utils'

interface TerminalProps {
  sessionId:      string
  isActive:       boolean
  socket:         TerminalSocketHook
  settings:       TerminalSettings
  onClose:        () => void
  onTitleChange:  (title: string) => void
  onNewTab:       () => void
}

const THEME = {
  background:          '#08080d',
  foreground:          '#e4e4e7',
  cursor:              '#3b82f6',
  cursorAccent:        '#08080d',
  selectionBackground: '#3b82f640',
  selectionForeground: '#e4e4e7',
  black:               '#1a1a2e',
  red:                 '#ef4444',
  green:               '#22c55e',
  yellow:              '#f59e0b',
  blue:                '#3b82f6',
  magenta:             '#8b5cf6',
  cyan:                '#06b6d4',
  white:               '#e4e4e7',
  brightBlack:         '#71717a',
  brightRed:           '#f87171',
  brightGreen:         '#4ade80',
  brightYellow:        '#fbbf24',
  brightBlue:          '#60a5fa',
  brightMagenta:       '#a78bfa',
  brightCyan:          '#22d3ee',
  brightWhite:         '#ffffff',
}

export default function Terminal({
  sessionId, isActive, socket, settings, onClose, onTitleChange, onNewTab,
}: TerminalProps) {
  const containerRef   = useRef<HTMLDivElement>(null)
  const termRef        = useRef<XTerm | null>(null)
  const fitRef         = useRef<FitAddon | null>(null)
  const resizeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const closedRef      = useRef(false)

  // Stable refs for callbacks that might change
  const onCloseRef       = useRef(onClose)
  const onTitleChangeRef = useRef(onTitleChange)
  const onNewTabRef      = useRef(onNewTab)
  useEffect(() => { onCloseRef.current = onClose },             [onClose])
  useEffect(() => { onTitleChangeRef.current = onTitleChange }, [onTitleChange])
  useEffect(() => { onNewTabRef.current = onNewTab },           [onNewTab])

  // ── create xterm instance (once on mount) ────────────────────────────────
  useEffect(() => {
    if (!containerRef.current) return

    const term = new XTerm({
      theme:            THEME,
      fontFamily:       'JetBrains Mono, Fira Code, Cascadia Code, monospace',
      fontSize:         settings.fontSize,
      cursorBlink:      settings.cursorBlink,
      cursorStyle:      settings.cursorStyle,
      scrollback:       settings.scrollback,
      allowTransparency: true,
      convertEol:       false,
      macOptionIsMeta:  true,
    })

    const fitAddon      = new FitAddon()
    const webLinksAddon = new WebLinksAddon()

    term.loadAddon(fitAddon)
    term.loadAddon(webLinksAddon)
    term.open(containerRef.current)
    fitAddon.fit()

    termRef.current = term
    fitRef.current  = fitAddon

    // Route keystrokes to backend
    term.onData(data => socket.sendInput(sessionId, data))

    // OSC title changes
    term.onTitleChange(title => onTitleChangeRef.current(title))

    // Ctrl+Shift shortcuts (intercepted before PTY)
    term.attachCustomKeyEventHandler(e => {
      if (e.type !== 'keydown') return true
      if (e.ctrlKey && e.shiftKey) {
        if (e.key === 'C') {
          const sel = term.getSelection()
          if (sel) void navigator.clipboard.writeText(sel)
          return false
        }
        if (e.key === 'V') {
          void navigator.clipboard.readText().then(text => socket.sendInput(sessionId, text))
          return false
        }
        if (e.key === 'T') {
          onNewTabRef.current()
          return false
        }
        if (e.key === 'W') {
          onCloseRef.current()
          return false
        }
      }
      return true
    })

    // Fetch buffer for reconnection
    void getSessionBuffer(sessionId)
      .then(buf => { if (buf) term.write(buf) })
      .catch(() => { /* fresh session, no buffer */ })

    return () => {
      if (!closedRef.current) socket.closeSession(sessionId)
      term.dispose()
      termRef.current = null
      fitRef.current  = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []) // intentionally empty — terminal is created once per sessionId

  // ── output subscription ──────────────────────────────────────────────────
  useEffect(() => {
    return socket.addOutputListener(sessionId, (data, closed) => {
      const term = termRef.current
      if (!term) return
      if (closed) {
        closedRef.current = true
        term.write('\r\n\x1b[2m[Session ended]\x1b[0m\r\n')
        onCloseRef.current()
      } else {
        term.write(data)
      }
    })
  }, [sessionId, socket.addOutputListener])

  // ── refit when tab becomes active ───────────────────────────────────────
  useEffect(() => {
    if (!isActive) return
    const fit    = fitRef.current
    const sendRz = socket.sendResize
    setTimeout(() => {
      if (!fit) return
      fit.fit()
      const dims = fit.proposeDimensions()
      if (dims) sendRz(sessionId, dims.cols, dims.rows)
    }, 50)
  }, [isActive, sessionId, socket.sendResize])

  // ── settings changes ─────────────────────────────────────────────────────
  useEffect(() => {
    const term = termRef.current
    if (!term) return
    term.options = {
      fontSize:    settings.fontSize,
      cursorBlink: settings.cursorBlink,
      cursorStyle: settings.cursorStyle,
      scrollback:  settings.scrollback,
    }
    fitRef.current?.fit()
  }, [settings])

  // ── resize observer ──────────────────────────────────────────────────────
  useEffect(() => {
    const el      = containerRef.current
    const sendRz  = socket.sendResize
    if (!el) return
    const ro = new ResizeObserver(() => {
      if (resizeTimerRef.current) clearTimeout(resizeTimerRef.current)
      resizeTimerRef.current = setTimeout(() => {
        if (!isActive) return
        const fit = fitRef.current
        if (!fit) return
        fit.fit()
        const dims = fit.proposeDimensions()
        if (dims) sendRz(sessionId, dims.cols, dims.rows)
      }, 100)
    })
    ro.observe(el)
    return () => { ro.disconnect(); if (resizeTimerRef.current) clearTimeout(resizeTimerRef.current) }
  }, [isActive, sessionId, socket.sendResize])

  // ── PING keep-alive (30s) ────────────────────────────────────────────────
  useEffect(() => {
    if (!isActive) return
    const id = setInterval(() => socket.sendPing(sessionId), 30_000)
    return () => clearInterval(id)
  }, [isActive, sessionId, socket.sendPing])

  return (
    <div
      ref={containerRef}
      className={cn('h-full w-full', !isActive && 'hidden')}
    />
  )
}
