import type { AlertRule } from '../../types'
import { Pencil, Trash2, Zap, Plus } from 'lucide-react'
import { useAuthStore } from '../../stores/authStore'
import { formatRelative } from '../../lib/formatters'

interface AlertRuleListProps {
  rules:    AlertRule[]
  loading:  boolean
  onEdit:   (rule: AlertRule) => void
  onDelete: (id: number) => void
  onToggle: (id: number, enabled: boolean) => void
  onTest:   (id: number) => void
  onCreate: () => void
}

const METRIC_LABELS: Record<string, string> = {
  CPU_USAGE:        'CPU Usage',
  MEMORY_USAGE:     'Memory Usage',
  DISK_USAGE:       'Disk Usage',
  SWAP_USAGE:       'Swap Usage',
  CONTAINER_CPU:    'Container CPU',
  CONTAINER_MEMORY: 'Container Memory',
}

const OPERATOR_LABELS: Record<string, string> = {
  GT:  '>',
  GTE: '≥',
  LT:  '<',
  LTE: '≤',
  EQ:  '=',
}

function Toggle({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      onClick={() => onChange(!checked)}
      className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors focus:outline-none ${
        checked ? 'bg-accent-green' : 'bg-border'
      }`}
    >
      <span
        className={`inline-block h-3.5 w-3.5 rounded-full bg-white shadow transition-transform ${
          checked ? 'translate-x-4.5' : 'translate-x-0.5'
        }`}
      />
    </button>
  )
}

function ChannelBadge({ channel }: { channel: string }) {
  const icons: Record<string, string> = {
    EMAIL:   '📧',
    WEBHOOK: '🔗',
    DISCORD: '💬',
    SLACK:   '💬',
  }
  return (
    <span className="text-xs text-text-tertiary" title={channel}>
      {icons[channel] ?? channel}
    </span>
  )
}

export default function AlertRuleList({
  rules, loading, onEdit, onDelete, onToggle, onTest, onCreate,
}: AlertRuleListProps) {
  const canManage = useAuthStore(s => s.hasPermission('ALERTS_MANAGE'))

  if (loading && rules.length === 0) {
    return (
      <div className="flex items-center justify-center h-40 text-text-tertiary text-sm">
        Loading rules…
      </div>
    )
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-text-primary">Alert Rules</h2>
        {canManage && (
          <button
            onClick={onCreate}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-white bg-accent-blue hover:bg-accent-blue/80 rounded-lg transition-colors"
          >
            <Plus className="w-3.5 h-3.5" />
            Create Rule
          </button>
        )}
      </div>

      {rules.length === 0 ? (
        <div className="rounded-xl border border-border bg-bg-secondary p-8 text-center">
          <Zap className="w-8 h-8 text-text-tertiary mx-auto mb-3" />
          <p className="text-sm text-text-secondary mb-1">No alert rules yet</p>
          <p className="text-xs text-text-tertiary">Create a rule to start monitoring</p>
        </div>
      ) : (
        rules.map(rule => (
          <div
            key={rule.id}
            className={`rounded-xl border bg-bg-secondary p-4 transition-colors ${
              rule.enabled ? 'border-border' : 'border-border/50 opacity-60'
            }`}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-center gap-2 min-w-0">
                {canManage ? (
                  <Toggle
                    checked={rule.enabled}
                    onChange={v => onToggle(rule.id, v)}
                  />
                ) : (
                  <span className={`w-5 h-3 rounded-full inline-block ${rule.enabled ? 'bg-accent-green' : 'bg-border'}`} />
                )}
                <span className="text-sm font-medium text-text-primary truncate">{rule.name}</span>
                {!rule.enabled && (
                  <span className="text-xs text-text-tertiary bg-bg-tertiary px-1.5 py-0.5 rounded">
                    disabled
                  </span>
                )}
              </div>

              <div className="flex items-center gap-1 flex-shrink-0">
                <button
                  onClick={() => onTest(rule.id)}
                  title="Test notification"
                  className="p-1.5 rounded text-text-tertiary hover:text-accent-amber hover:bg-bg-tertiary transition-colors"
                >
                  <Zap className="w-3.5 h-3.5" />
                </button>
                {canManage && (
                  <>
                    <button
                      onClick={() => onEdit(rule)}
                      title="Edit rule"
                      className="p-1.5 rounded text-text-tertiary hover:text-text-primary hover:bg-bg-tertiary transition-colors"
                    >
                      <Pencil className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => onDelete(rule.id)}
                      title="Delete rule"
                      className="p-1.5 rounded text-text-tertiary hover:text-accent-red hover:bg-bg-tertiary transition-colors"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </>
                )}
              </div>
            </div>

            <div className="mt-2 flex flex-wrap items-center gap-3 text-xs text-text-secondary">
              <span className="font-mono bg-bg-tertiary px-2 py-0.5 rounded">
                {METRIC_LABELS[rule.metricType] ?? rule.metricType}
                {' '}{OPERATOR_LABELS[rule.operator] ?? rule.operator}{' '}
                {rule.threshold}%
              </span>
              {rule.containerName && (
                <span className="text-text-tertiary">container: {rule.containerName}</span>
              )}
              <span className="text-text-tertiary">cooldown: {rule.cooldownMinutes}m</span>

              <div className="flex items-center gap-1.5 ml-auto">
                {rule.notifyEmail   && <ChannelBadge channel="EMAIL" />}
                {rule.notifyWebhook && <ChannelBadge channel="WEBHOOK" />}
              </div>
            </div>

            {rule.updatedAt && (
              <p className="mt-1.5 text-[10px] text-text-tertiary">
                Updated {formatRelative(rule.updatedAt)}
              </p>
            )}
          </div>
        ))
      )}
    </div>
  )
}
