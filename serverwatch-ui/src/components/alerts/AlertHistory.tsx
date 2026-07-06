import { useState, useEffect, useCallback } from 'react'
import { Loader2, ChevronDown, ChevronRight, AlertTriangle, Zap } from 'lucide-react'
import type { AlertEvent, AlertRule } from '../../types'
import { getHistory, listRules } from '../../api/alerts'
import { useToastStore } from '../../stores/toastStore'
import { formatRelative } from '../../lib/formatters'

const TIME_RANGES = [
  { label: 'Last 1h',  hours: 1   },
  { label: 'Last 6h',  hours: 6   },
  { label: 'Last 24h', hours: 24  },
  { label: 'Last 7d',  hours: 168 },
  { label: 'Last 30d', hours: 720 },
]

function SeverityIcon({ severity }: { severity: AlertEvent['severity'] }) {
  if (severity === 'CRITICAL') {
    return <AlertTriangle className="w-3.5 h-3.5 text-accent-red flex-shrink-0" />
  }
  return <Zap className="w-3.5 h-3.5 text-accent-amber flex-shrink-0" />
}

function ExpandedRow({ alert }: { alert: AlertEvent }) {
  return (
    <div className="px-4 pb-3 pt-1 bg-bg-tertiary text-xs text-text-secondary space-y-1">
      <p><span className="text-text-tertiary">Rule ID:</span> {alert.ruleId}</p>
      <p><span className="text-text-tertiary">Metric:</span> {alert.metricType}</p>
      <p><span className="text-text-tertiary">Condition:</span> {alert.currentValue.toFixed(1)} {alert.operator} {alert.threshold}</p>
      <p><span className="text-text-tertiary">Message:</span> {alert.message}</p>
      {alert.notificationChannels.length > 0 && (
        <p><span className="text-text-tertiary">Channels:</span> {alert.notificationChannels.join(', ')}</p>
      )}
    </div>
  )
}

export default function AlertHistory() {
  const [events, setEvents]   = useState<AlertEvent[]>([])
  const [rules, setRules]     = useState<AlertRule[]>([])
  const [loading, setLoading] = useState(false)
  const [hours, setHours]     = useState(24)
  const [ruleFilter, setRuleFilter]         = useState<string>('all')
  const [severityFilter, setSeverityFilter] = useState<string>('all')
  const [expanded, setExpanded]             = useState<Set<number>>(new Set())

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const data = await getHistory(hours, 200)
      setEvents(data)
    } catch {
      useToastStore.getState().addToast('error', 'Failed to load alert history')
    } finally {
      setLoading(false)
    }
  }, [hours])

  useEffect(() => {
    void listRules().then(setRules).catch(() => {})
  }, [])

  useEffect(() => { void load() }, [load])

  function toggleExpand(id: number) {
    setExpanded(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const filtered = events.filter(e => {
    if (ruleFilter !== 'all' && String(e.ruleId) !== ruleFilter) return false
    if (severityFilter !== 'all' && e.severity !== severityFilter) return false
    return true
  })

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <h2 className="text-sm font-semibold text-text-primary">Alert History</h2>

        <div className="flex items-center gap-2 flex-wrap">
          {/* Time range */}
          <select
            value={hours}
            onChange={e => setHours(Number(e.target.value))}
            className="px-2.5 py-1.5 bg-bg-tertiary border border-border rounded-lg text-xs text-text-primary focus:outline-none focus:border-accent-blue"
          >
            {TIME_RANGES.map(r => (
              <option key={r.hours} value={r.hours}>{r.label}</option>
            ))}
          </select>

          {/* Rule filter */}
          <select
            value={ruleFilter}
            onChange={e => setRuleFilter(e.target.value)}
            className="px-2.5 py-1.5 bg-bg-tertiary border border-border rounded-lg text-xs text-text-primary focus:outline-none focus:border-accent-blue"
          >
            <option value="all">All Rules</option>
            {rules.map(r => (
              <option key={r.id} value={String(r.id)}>{r.name}</option>
            ))}
          </select>

          {/* Severity filter */}
          <select
            value={severityFilter}
            onChange={e => setSeverityFilter(e.target.value)}
            className="px-2.5 py-1.5 bg-bg-tertiary border border-border rounded-lg text-xs text-text-primary focus:outline-none focus:border-accent-blue"
          >
            <option value="all">All Severities</option>
            <option value="WARNING">Warning</option>
            <option value="CRITICAL">Critical</option>
          </select>
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-40 text-text-tertiary">
          <Loader2 className="w-5 h-5 animate-spin" />
        </div>
      ) : filtered.length === 0 ? (
        <div className="rounded-xl border border-border bg-bg-secondary p-8 text-center">
          <AlertTriangle className="w-8 h-8 text-text-tertiary mx-auto mb-3" />
          <p className="text-sm text-text-secondary">No alerts in selected range</p>
        </div>
      ) : (
        <div className="rounded-xl border border-border bg-bg-secondary overflow-hidden">
          {/* Table header */}
          <div className="grid grid-cols-[auto_80px_1fr_80px_80px_40px] gap-3 px-4 py-2 border-b border-border bg-bg-tertiary text-[10px] text-text-tertiary uppercase tracking-wider font-semibold">
            <span>Time</span>
            <span>Severity</span>
            <span>Rule</span>
            <span>Value</span>
            <span>Threshold</span>
            <span>Notified</span>
          </div>

          {filtered.map(event => (
            <div key={`${event.id}-${event.triggeredAt}`}>
              <button
                onClick={() => toggleExpand(event.id)}
                className="w-full grid grid-cols-[auto_80px_1fr_80px_80px_40px] gap-3 items-center px-4 py-2.5 border-b border-border/50 hover:bg-bg-tertiary transition-colors text-left"
              >
                <div className="flex items-center gap-1.5">
                  {expanded.has(event.id)
                    ? <ChevronDown className="w-3 h-3 text-text-tertiary" />
                    : <ChevronRight className="w-3 h-3 text-text-tertiary" />
                  }
                  <span className="text-xs text-text-secondary whitespace-nowrap">{formatRelative(event.triggeredAt)}</span>
                </div>
                <div className="flex items-center gap-1">
                  <SeverityIcon severity={event.severity} />
                  <span className={`text-xs font-semibold ${
                    event.severity === 'CRITICAL' ? 'text-accent-red' : 'text-accent-amber'
                  }`}>
                    {event.severity}
                  </span>
                </div>
                <span className="text-xs text-text-primary truncate">{event.ruleName}</span>
                <span className="text-xs text-text-secondary font-mono">{event.currentValue.toFixed(1)}%</span>
                <span className="text-xs text-text-tertiary font-mono">{event.operator} {event.threshold}%</span>
                <span className={`text-xs text-center ${event.notified ? 'text-accent-green' : 'text-text-tertiary'}`}>
                  {event.notified ? '✓' : '—'}
                </span>
              </button>
              {expanded.has(event.id) && <ExpandedRow alert={event} />}
            </div>
          ))}
        </div>
      )}

      <p className="text-xs text-text-tertiary text-right">
        {filtered.length} event{filtered.length !== 1 ? 's' : ''} shown
      </p>
    </div>
  )
}
