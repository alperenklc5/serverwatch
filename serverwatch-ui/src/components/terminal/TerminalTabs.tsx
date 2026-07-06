import { Plus, X, Loader2 } from 'lucide-react'
import { cn } from '../../lib/utils'

export interface TabInfo {
  sessionId: string
  shell:     string
  title:     string
}

interface TerminalTabsProps {
  tabs:          TabInfo[]
  activeId:      string | null
  shells:        string[]
  selectedShell: string
  isCreating:    boolean
  onSelect:      (id: string) => void
  onClose:       (id: string) => void
  onNew:         () => void
  onShellChange: (shell: string) => void
}

export default function TerminalTabs({
  tabs, activeId, shells, selectedShell, isCreating,
  onSelect, onClose, onNew, onShellChange,
}: TerminalTabsProps) {
  return (
    <div className="flex items-center h-10 bg-bg-secondary border-b border-border overflow-hidden flex-shrink-0">
      {/* Tab list */}
      <div className="flex items-stretch overflow-x-auto flex-1 min-w-0">
        {tabs.map(tab => {
          const active = tab.sessionId === activeId
          const label  = tab.title || tab.shell || 'bash'
          return (
            <div
              key={tab.sessionId}
              className={cn(
                'flex items-center gap-1.5 px-3 min-w-0 max-w-40 flex-shrink-0 border-r border-border cursor-pointer group transition-colors',
                active
                  ? 'bg-bg-primary text-text-primary'
                  : 'bg-bg-tertiary text-text-secondary hover:bg-bg-primary/60 hover:text-text-primary',
              )}
              onClick={() => onSelect(tab.sessionId)}
            >
              <span className="text-xs truncate flex-1">{label}</span>
              <button
                onClick={e => { e.stopPropagation(); onClose(tab.sessionId) }}
                className="p-0.5 rounded text-text-tertiary hover:text-accent-red opacity-0 group-hover:opacity-100 transition-all flex-shrink-0"
              >
                <X className="w-3 h-3" />
              </button>
            </div>
          )
        })}
      </div>

      {/* New tab button */}
      <div className="flex items-center gap-1 px-2 flex-shrink-0 border-l border-border h-full">
        <button
          onClick={onNew}
          disabled={isCreating}
          title="New terminal (Ctrl+Shift+T)"
          className="flex items-center gap-1 px-2 py-1 rounded text-xs text-text-secondary hover:text-text-primary hover:bg-bg-tertiary transition-colors disabled:opacity-50"
        >
          {isCreating
            ? <Loader2 className="w-3.5 h-3.5 animate-spin" />
            : <Plus    className="w-3.5 h-3.5" />
          }
          <span className="hidden sm:inline">New</span>
        </button>

        {/* Shell selector */}
        {shells.length > 1 && (
          <select
            value={selectedShell}
            onChange={e => onShellChange(e.target.value)}
            className="text-xs bg-bg-primary border border-border rounded px-1.5 py-1 text-text-secondary focus:outline-none focus:border-accent-blue max-w-20"
          >
            {shells.map(s => (
              <option key={s} value={s}>{s.split('/').pop()}</option>
            ))}
          </select>
        )}
      </div>
    </div>
  )
}
