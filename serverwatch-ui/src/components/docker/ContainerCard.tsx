import { Play, Square, RefreshCw, FileText, Trash2, Shield, Loader2 } from 'lucide-react'
import { cn } from '../../lib/utils'
import { formatBytes } from '../../lib/formatters'
import type { ContainerInfo, ContainerStats } from '../../types'

interface ContainerCardProps {
  container: ContainerInfo
  stats?: ContainerStats
  actionLoading?: string   // which action is in-flight: 'start'|'stop'|'restart'|'remove'
  onStart:   () => void
  onStop:    () => void
  onRestart: () => void
  onRemove:  () => void
  onViewLogs: () => void
  onInspect: () => void
}

const STATE_DOT: Record<string, string> = {
  running:    'bg-accent-green',
  exited:     'bg-accent-red',
  stopped:    'bg-accent-red',
  paused:     'bg-accent-amber',
  restarting: 'bg-accent-blue animate-pulse',
}

const STATE_TEXT: Record<string, string> = {
  running:    'text-accent-green',
  exited:     'text-accent-red',
  stopped:    'text-accent-red',
  paused:     'text-accent-amber',
  restarting: 'text-accent-blue',
}

function isProtected(c: ContainerInfo): boolean {
  return c.labels?.['serverwatch.managed'] === 'true' ||
         c.name.toLowerCase().includes('serverwatch')
}

function MiniBar({ value, max, color }: { value: number; max: number; color: string }) {
  const pct = max > 0 ? Math.min((value / max) * 100, 100) : 0
  return (
    <div className="h-1 bg-bg-primary rounded-full overflow-hidden flex-1">
      <div
        className={cn('h-full rounded-full transition-all duration-700', color)}
        style={{ width: `${pct}%` }}
      />
    </div>
  )
}

export default function ContainerCard({
  container, stats, actionLoading,
  onStart, onStop, onRestart, onRemove, onViewLogs, onInspect,
}: ContainerCardProps) {
  const running   = container.state === 'running'
  const protected_ = isProtected(container)
  const dotColor  = STATE_DOT[container.state] ?? 'bg-text-tertiary'
  const textColor = STATE_TEXT[container.state] ?? 'text-text-tertiary'

  const ports = container.ports.filter(p => p.publicPort > 0)

  return (
    <div
      className="bg-bg-secondary border border-border hover:border-border-active rounded-xl p-4 flex flex-col gap-3 transition-colors cursor-pointer group"
      onClick={onInspect}
    >
      {/* Header */}
      <div className="flex items-start gap-2">
        <span className={cn('mt-1.5 w-2 h-2 rounded-full flex-shrink-0', dotColor)} />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="font-semibold text-text-primary truncate">{container.name}</span>
            {protected_ && (
              <span title="Protected">
                <Shield className="w-3.5 h-3.5 text-accent-blue flex-shrink-0" aria-label="Protected" />
              </span>
            )}
          </div>
          <div className="text-xs text-text-secondary truncate mt-0.5">{container.image}</div>
          <div className={cn('text-xs mt-0.5', textColor)}>{container.status}</div>
        </div>
      </div>

      {/* Live stats bars (running only) */}
      {running && stats && (
        <div className="space-y-1.5">
          <div className="flex items-center gap-2 text-xs text-text-secondary">
            <span className="w-8 shrink-0">CPU</span>
            <MiniBar value={stats.cpuPercent} max={100} color="bg-accent-blue" />
            <span className="w-12 text-right font-mono text-text-primary">{stats.cpuPercent.toFixed(1)}%</span>
          </div>
          <div className="flex items-center gap-2 text-xs text-text-secondary">
            <span className="w-8 shrink-0">RAM</span>
            <MiniBar value={stats.memoryUsageBytes} max={stats.memoryLimitBytes} color="bg-accent-green" />
            <span className="w-12 text-right font-mono text-text-primary">{formatBytes(stats.memoryUsageBytes)}</span>
          </div>
        </div>
      )}

      {/* Port badges */}
      {ports.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {ports.slice(0, 4).map((p, i) => (
            <span
              key={i}
              className="text-xs font-mono bg-bg-primary border border-border rounded px-1.5 py-0.5 text-text-tertiary"
            >
              {p.publicPort}:{p.privatePort}
            </span>
          ))}
          {ports.length > 4 && (
            <span className="text-xs text-text-tertiary">+{ports.length - 4}</span>
          )}
        </div>
      )}

      {/* Actions */}
      <div
        className="flex items-center gap-1 pt-1 border-t border-border/50"
        onClick={e => e.stopPropagation()}
      >
        {running ? (
          <>
            {!protected_ && (
              <>
                <ActionBtn
                  icon={Square}
                  label="Stop"
                  loading={actionLoading === 'stop'}
                  onClick={onStop}
                  className="hover:text-accent-red hover:bg-accent-red/10"
                />
                <ActionBtn
                  icon={RefreshCw}
                  label="Restart"
                  loading={actionLoading === 'restart'}
                  onClick={onRestart}
                  className="hover:text-accent-amber hover:bg-accent-amber/10"
                />
              </>
            )}
            <ActionBtn
              icon={FileText}
              label="Logs"
              loading={false}
              onClick={onViewLogs}
              className="hover:text-accent-blue hover:bg-accent-blue/10"
            />
          </>
        ) : (
          <>
            <ActionBtn
              icon={Play}
              label="Start"
              loading={actionLoading === 'start'}
              onClick={onStart}
              className="hover:text-accent-green hover:bg-accent-green/10"
            />
            {!protected_ && (
              <ActionBtn
                icon={Trash2}
                label="Remove"
                loading={actionLoading === 'remove'}
                onClick={onRemove}
                className="hover:text-accent-red hover:bg-accent-red/10"
              />
            )}
            <ActionBtn
              icon={FileText}
              label="Logs"
              loading={false}
              onClick={onViewLogs}
              className="hover:text-accent-blue hover:bg-accent-blue/10"
            />
          </>
        )}
      </div>
    </div>
  )
}

interface ActionBtnProps {
  icon: typeof Play
  label: string
  loading: boolean
  onClick: () => void
  className?: string
}

function ActionBtn({ icon: Icon, label, loading, onClick, className }: ActionBtnProps) {
  return (
    <button
      onClick={onClick}
      disabled={loading}
      title={label}
      className={cn(
        'flex items-center gap-1.5 px-2 py-1.5 rounded-lg text-xs text-text-secondary transition-colors',
        'disabled:opacity-50 disabled:cursor-not-allowed',
        className,
      )}
    >
      {loading
        ? <Loader2 className="w-3.5 h-3.5 animate-spin" />
        : <Icon className="w-3.5 h-3.5" />
      }
      <span>{label}</span>
    </button>
  )
}
