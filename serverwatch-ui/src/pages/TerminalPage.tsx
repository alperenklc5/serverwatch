import { useCallback, useEffect, useRef, useState } from 'react'
import { Maximize2, Minimize2, Wifi, WifiOff } from 'lucide-react'
import { getTerminalShells } from '../api/terminal'
import { useTerminalSocket } from '../hooks/useTerminalSocket'
import { useMetricsStore } from '../stores/metricsStore'
import Terminal from '../components/terminal/Terminal'
import TerminalTabs, { type TabInfo } from '../components/terminal/TerminalTabs'
import TerminalSettingsPanel, { DEFAULT_SETTINGS, type TerminalSettings } from '../components/terminal/TerminalSettings'
import { cn } from '../lib/utils'

export default function TerminalPage() {
  const [tabs, setTabs]                   = useState<TabInfo[]>([])
  const [activeId, setActiveId]           = useState<string | null>(null)
  const [shells, setShells]               = useState<string[]>(['bash'])
  const [selectedShell, setSelectedShell] = useState('bash')
  const [isCreating, setIsCreating]       = useState(false)
  const [settings, setSettings]           = useState<TerminalSettings>(DEFAULT_SETTINGS)
  const [isFullscreen, setIsFullscreen]   = useState(false)

  const socket          = useTerminalSocket()
  const wsConnected     = useMetricsStore(s => s.isConnected)
  const creationPending = useRef(false)

  // ── fetch available shells on load ──────────────────────────────────────
  useEffect(() => {
    getTerminalShells()
      .then(list => {
        if (list.length > 0) { setShells(list); setSelectedShell(list[0]) }
      })
      .catch(() => { /* keep default */ })
  }, [])

  // ── create a new session ────────────────────────────────────────────────
  const createTab = useCallback(() => {
    if (isCreating || creationPending.current) return
    setIsCreating(true)
    creationPending.current = true
    socket.createSession(selectedShell, 80, 24)
  }, [socket.createSession, isCreating, selectedShell])

  // ── cancel a stuck creation ─────────────────────────────────────────────
  const cancelCreating = useCallback(() => {
    setIsCreating(false)
    creationPending.current = false
  }, [])

  // ── receive session-created ──────────────────────────────────────────────
  useEffect(() => {
    return socket.addSessionCreatedListener(session => {
      setTabs(prev => [
        ...prev,
        { sessionId: session.sessionId, shell: session.shell, title: session.shell },
      ])
      setActiveId(session.sessionId)
      setIsCreating(false)
      creationPending.current = false
    })
  }, [socket.addSessionCreatedListener])

  // ── auto-open first terminal when socket connects ───────────────────────
  const autoStarted = useRef(false)
  useEffect(() => {
    if (wsConnected && socket.isConnected && !autoStarted.current) {
      autoStarted.current = true
      createTab()
    }
  }, [wsConnected, socket.isConnected, createTab])

  // ── close a tab ─────────────────────────────────────────────────────────
  const closeTab = useCallback((sessionId: string) => {
    setTabs(prev => {
      const next = prev.filter(t => t.sessionId !== sessionId)
      setActiveId(curr => {
        if (curr !== sessionId) return curr
        return next.length > 0 ? next[next.length - 1].sessionId : null
      })
      return next
    })
  }, [])

  // ── tab title from OSC sequences ────────────────────────────────────────
  const updateTitle = useCallback((sessionId: string, title: string) => {
    setTabs(prev => prev.map(t => t.sessionId === sessionId ? { ...t, title } : t))
  }, [])

  // ── global keyboard shortcuts (Ctrl+Shift+←/→ tab switch) ───────────────
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (!e.ctrlKey || !e.shiftKey) return
      if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return
      e.preventDefault()
      setTabs(current => {
        setActiveId(id => {
          const idx = current.findIndex(t => t.sessionId === id)
          if (idx < 0) return id
          const next = e.key === 'ArrowRight'
            ? current[(idx + 1) % current.length]
            : current[(idx - 1 + current.length) % current.length]
          return next?.sessionId ?? id
        })
        return current
      })
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [])

  // ── ESC exits fullscreen ─────────────────────────────────────────────────
  useEffect(() => {
    if (!isFullscreen) return
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') setIsFullscreen(false) }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [isFullscreen])

  // ── layout ───────────────────────────────────────────────────────────────
  const terminalBlock = (
    <div className={cn(
      'flex flex-col bg-bg-primary overflow-hidden',
      isFullscreen
        ? 'fixed inset-0 z-[9999]'
        : 'h-[calc(100vh-8rem)] rounded-xl border border-border',
    )}>
      {/* Tab bar */}
      <div className="flex items-center bg-bg-secondary border-b border-border flex-shrink-0">
        <div className="flex-1 min-w-0 overflow-hidden border-b-0">
          <TerminalTabs
            tabs={tabs}
            activeId={activeId}
            shells={shells}
            selectedShell={selectedShell}
            isCreating={isCreating}
            onSelect={setActiveId}
            onClose={closeTab}
            onNew={createTab}
            onCancel={cancelCreating}
            onShellChange={setSelectedShell}
          />
        </div>
        {/* Right controls — inline with tab bar */}
        <div className="flex items-center gap-1 px-2 h-10 flex-shrink-0">
          {wsConnected
            ? <span title="Connected">   <Wifi    className="w-3.5 h-3.5 text-accent-green"  /></span>
            : <span title="Disconnected"><WifiOff className="w-3.5 h-3.5 text-text-tertiary" /></span>
          }
          <TerminalSettingsPanel settings={settings} onUpdate={setSettings} />
          <button
            onClick={() => setIsFullscreen(v => !v)}
            title={isFullscreen ? 'Exit fullscreen (Esc)' : 'Fullscreen'}
            className="p-1.5 rounded-lg text-text-secondary hover:text-text-primary hover:bg-bg-tertiary transition-colors"
          >
            {isFullscreen
              ? <Minimize2 className="w-4 h-4" />
              : <Maximize2 className="w-4 h-4" />
            }
          </button>
        </div>
      </div>

      {/* Terminal area — bg matches xterm theme */}
      <div className="flex-1 min-h-0 overflow-hidden relative" style={{ background: '#08080d' }}>
        {tabs.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full gap-3 text-text-tertiary">
            <span className="text-sm">No open sessions</span>
            <button
              onClick={createTab}
              className="px-4 py-2 text-sm font-medium text-white bg-accent-blue hover:bg-accent-blue/80 rounded-lg transition-colors"
            >
              Open Terminal
            </button>
          </div>
        ) : (
          tabs.map(tab => (
            <Terminal
              key={tab.sessionId}
              sessionId={tab.sessionId}
              isActive={tab.sessionId === activeId}
              socket={socket}
              settings={settings}
              onClose={() => closeTab(tab.sessionId)}
              onTitleChange={title => updateTitle(tab.sessionId, title)}
              onNewTab={createTab}
            />
          ))
        )}
      </div>
    </div>
  )

  if (isFullscreen) return terminalBlock

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-2xl font-semibold text-text-primary">Terminal</h1>
      </div>
      {terminalBlock}
    </div>
  )
}
