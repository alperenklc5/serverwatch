import { Info, Server, Cpu, Globe } from 'lucide-react'
import { useMetricsStore } from '../../stores/metricsStore'
import { formatBytes } from '../../lib/formatters'

const APP_VERSION = '1.0.0'

export default function AboutPanel() {
  const uptime       = useMetricsStore(s => s.uptime)
  const systemMetric = useMetricsStore(s => s.systemMetric)

  return (
    <div className="space-y-4">
      {/* App info */}
      <div className="bg-bg-secondary border border-border rounded-xl p-6">
        <div className="flex items-center gap-2 mb-4">
          <Info className="w-4 h-4 text-text-tertiary" />
          <h2 className="text-sm font-semibold text-text-primary">About ServerWatch</h2>
        </div>

        <div className="space-y-3 text-sm">
          <Row label="Version">
            <span className="font-mono text-accent-blue">{APP_VERSION}</span>
          </Row>
          <Row label="Stack">
            <span className="text-text-primary">Spring Boot 3.3 · Java 21 · React 18 · TypeScript</span>
          </Row>
          <Row label="License">
            <span className="text-text-secondary">MIT</span>
          </Row>
        </div>
      </div>

      {/* Server info */}
      <div className="bg-bg-secondary border border-border rounded-xl p-6">
        <div className="flex items-center gap-2 mb-4">
          <Server className="w-4 h-4 text-text-tertiary" />
          <h2 className="text-sm font-semibold text-text-primary">Host System</h2>
        </div>

        {(!uptime && !systemMetric) ? (
          <p className="text-sm text-text-tertiary">Waiting for metrics connection…</p>
        ) : (
          <div className="space-y-3 text-sm">
            {uptime && (
              <>
                <Row label="Hostname">
                  <span className="font-mono text-text-primary">{uptime.hostname}</span>
                </Row>
                <Row label="OS">
                  <span className="text-text-primary">{uptime.osName} {uptime.osVersion}</span>
                </Row>
                <Row label="Uptime">
                  <span className="text-text-primary">{uptime.formattedUptime}</span>
                </Row>
                <Row label="Boot Time">
                  <span className="text-text-secondary">{new Date(uptime.bootTime).toLocaleString()}</span>
                </Row>
              </>
            )}
            {systemMetric && (
              <>
                <Row label="CPU">
                  <span className="text-text-primary flex items-center gap-1.5">
                    <Cpu className="w-3.5 h-3.5 text-text-tertiary" />
                    {systemMetric.cpuModelName} ({systemMetric.cpuCoreCount} cores)
                  </span>
                </Row>
                <Row label="Memory">
                  <span className="text-text-primary">{formatBytes(systemMetric.memoryTotalBytes)} total</span>
                </Row>
                {systemMetric.diskInfos.map(d => (
                  <Row key={d.mountPoint} label={`Disk (${d.mountPoint})`}>
                    <span className="text-text-primary flex items-center gap-1.5">
                      <Globe className="w-3.5 h-3.5 text-text-tertiary" />
                      {formatBytes(d.totalBytes)} · {d.type}
                    </span>
                  </Row>
                ))}
              </>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-start gap-4">
      <span className="text-text-secondary w-28 flex-shrink-0">{label}</span>
      <span className="flex-1 min-w-0">{children}</span>
    </div>
  )
}
