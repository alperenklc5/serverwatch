import { useEffect, useState } from 'react'
import { Monitor, Clock, Wifi, WifiOff } from 'lucide-react'
import { useMetricsStore } from '../../stores/metricsStore'
import { formatUptime } from '../../lib/formatters'

export default function ServerInfoBar() {
  const uptime     = useMetricsStore(s => s.uptime)
  const isConnected = useMetricsStore(s => s.isConnected)
  const [liveSeconds, setLiveSeconds] = useState(uptime?.uptimeSeconds ?? 0)

  // Tick the uptime counter locally every second
  useEffect(() => {
    if (uptime) setLiveSeconds(uptime.uptimeSeconds)
  }, [uptime])

  useEffect(() => {
    const id = setInterval(() => setLiveSeconds(s => s + 1), 1000)
    return () => clearInterval(id)
  }, [])

  return (
    <div className="bg-bg-secondary border border-border rounded-xl px-5 py-3 flex flex-wrap items-center gap-x-6 gap-y-2 text-sm">
      {/* Hostname */}
      <div className="flex items-center gap-2 text-text-primary font-medium">
        <Monitor className="w-4 h-4 text-text-secondary flex-shrink-0" />
        <span className="font-mono">{uptime?.hostname ?? '—'}</span>
      </div>

      <Divider />

      {/* OS */}
      <div className="text-text-secondary">
        {uptime ? `${uptime.osName} ${uptime.osVersion}` : '—'}
      </div>

      <Divider />

      {/* Uptime */}
      <div className="flex items-center gap-2 text-text-secondary">
        <Clock className="w-3.5 h-3.5 flex-shrink-0" />
        <span className="font-mono">{liveSeconds > 0 ? formatUptime(liveSeconds) : '—'}</span>
      </div>

      <Divider />

      {/* Connection */}
      <div className="flex items-center gap-2 ml-auto">
        {isConnected ? (
          <>
            <Wifi className="w-4 h-4 text-accent-green" />
            <span className="text-accent-green font-medium">Live</span>
          </>
        ) : (
          <>
            <WifiOff className="w-4 h-4 text-accent-red" />
            <span className="text-accent-red font-medium">Disconnected</span>
          </>
        )}
      </div>
    </div>
  )
}

function Divider() {
  return <span className="hidden sm:block w-px h-4 bg-border flex-shrink-0" />
}
