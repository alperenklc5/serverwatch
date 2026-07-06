import { useState, useEffect, useRef } from 'react'
import { AlertTriangle, Zap, Volume2, VolumeX } from 'lucide-react'
import { useAlertStore } from '../../stores/alertStore'
import type { AlertEvent } from '../../types'
import { formatRelative } from '../../lib/formatters'

function SeverityBadge({ severity }: { severity: AlertEvent['severity'] }) {
  if (severity === 'CRITICAL') {
    return (
      <span className="flex items-center gap-1 text-xs font-semibold text-accent-red bg-accent-red/10 px-2 py-0.5 rounded-full">
        <AlertTriangle className="w-3 h-3" />
        CRITICAL
      </span>
    )
  }
  return (
    <span className="flex items-center gap-1 text-xs font-semibold text-accent-amber bg-accent-amber/10 px-2 py-0.5 rounded-full">
      <Zap className="w-3 h-3" />
      WARNING
    </span>
  )
}

function AlertCard({ alert }: { alert: AlertEvent }) {
  return (
    <div className={`flex items-start gap-3 p-3 rounded-lg border-l-2 bg-bg-tertiary ${
      alert.severity === 'CRITICAL' ? 'border-l-accent-red' : 'border-l-accent-amber'
    }`}>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <SeverityBadge severity={alert.severity} />
          <span className="text-xs text-text-secondary truncate">{alert.ruleName}</span>
          <span className="text-xs text-text-tertiary ml-auto flex-shrink-0">{formatRelative(alert.triggeredAt)}</span>
        </div>
        <p className="text-sm text-text-primary mt-1 leading-snug">{alert.message}</p>
        {alert.notificationChannels.length > 0 && (
          <p className="text-[10px] text-text-tertiary mt-0.5">
            Sent via: {alert.notificationChannels.join(', ')}
          </p>
        )}
      </div>
    </div>
  )
}

export default function AlertLiveFeed() {
  const recentAlerts  = useAlertStore(s => s.recentAlerts)
  const clearUnread   = useAlertStore(s => s.clearUnread)
  const [sound, setSound] = useState(false)
  const audioRef      = useRef<AudioContext | null>(null)

  // Clear unread badge when this feed is visible
  useEffect(() => { clearUnread() }, [clearUnread])

  // Play sound on new alerts (track count in ref to avoid stale closure)
  const prevLenRef = useRef(recentAlerts.length)
  const soundRef   = useRef(sound)
  useEffect(() => { soundRef.current = sound }, [sound])

  useEffect(() => {
    if (recentAlerts.length > prevLenRef.current) {
      prevLenRef.current = recentAlerts.length
      if (soundRef.current) {
        try {
          if (!audioRef.current) audioRef.current = new AudioContext()
          const ctx  = audioRef.current
          const osc  = ctx.createOscillator()
          const gain = ctx.createGain()
          osc.connect(gain)
          gain.connect(ctx.destination)
          osc.frequency.value = 440
          gain.gain.setValueAtTime(0.15, ctx.currentTime)
          gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.3)
          osc.start(ctx.currentTime)
          osc.stop(ctx.currentTime + 0.3)
        } catch { /* ignore AudioContext errors */ }
      }
    }
  }, [recentAlerts.length]) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <h2 className="text-sm font-semibold text-text-primary">Live Feed</h2>
          <span className="w-2 h-2 rounded-full bg-accent-green animate-pulse" />
        </div>
        <button
          onClick={() => setSound(s => !s)}
          title={sound ? 'Disable sound' : 'Enable sound'}
          className="p-1.5 rounded text-text-tertiary hover:text-text-primary hover:bg-bg-tertiary transition-colors"
        >
          {sound
            ? <Volume2 className="w-4 h-4" />
            : <VolumeX className="w-4 h-4" />
          }
        </button>
      </div>

      {recentAlerts.length === 0 ? (
        <div className="rounded-xl border border-border bg-bg-secondary p-10 text-center">
          <Zap className="w-8 h-8 text-text-tertiary mx-auto mb-3" />
          <p className="text-sm text-text-secondary">Waiting for alerts…</p>
          <p className="text-xs text-text-tertiary mt-1">Live alerts will appear here in real-time</p>
        </div>
      ) : (
        <div className="space-y-2">
          {recentAlerts.map(alert => (
            <AlertCard key={`${alert.id}-${alert.triggeredAt}`} alert={alert} />
          ))}
        </div>
      )}
    </div>
  )
}
