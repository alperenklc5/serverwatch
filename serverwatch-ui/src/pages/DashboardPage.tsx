import { useMemo } from 'react'
import { Cpu, MemoryStick, HardDrive, Network } from 'lucide-react'
import { useMetricsStore } from '../stores/metricsStore'
import { useSettingsStore } from '../stores/settingsStore'
import { formatPercent, formatBytes } from '../lib/formatters'
import StatCard from '../components/charts/StatCard'
import CpuChart from '../components/charts/CpuChart'
import MemoryChart from '../components/charts/MemoryChart'
import NetworkChart from '../components/charts/NetworkChart'
import DiskChart from '../components/charts/DiskChart'
import ProcessTable from '../components/charts/ProcessTable'
import ServerInfoBar from '../components/charts/ServerInfoBar'

export default function DashboardPage() {
  const systemMetric   = useMetricsStore(s => s.systemMetric)
  const systemHistory  = useMetricsStore(s => s.systemHistory)
  const networkPrimary = useMetricsStore(s => s.networkPrimary)
  const processes      = useMetricsStore(s => s.processes)
  const widgets        = useSettingsStore(s => s.dashboardWidgets)

  // Stat card derived values
  const cpuValue   = systemMetric?.cpuUsagePercent ?? 0
  const memValue   = systemMetric?.memoryUsagePercent ?? 0
  const diskPct    = useMemo(() => {
    const disks = systemMetric?.diskInfos ?? []
    if (disks.length === 0) return 0
    const total = disks.reduce((s, d) => s + d.totalBytes, 0)
    const used  = disks.reduce((s, d) => s + (d.totalBytes - d.usableBytes), 0)
    return total > 0 ? (used / total) * 100 : 0
  }, [systemMetric])

  const netTotal = useMemo(() => {
    const last = networkPrimary[networkPrimary.length - 1]
    return last ? last.rx + last.tx : 0
  }, [networkPrimary])

  const cpuTrend  = useMemo(() => systemHistory.slice(-30).map(m => m.cpuUsagePercent), [systemHistory])
  const memTrend  = useMemo(() => systemHistory.slice(-30).map(m => m.memoryUsagePercent), [systemHistory])
  const netTrend  = useMemo(() => networkPrimary.slice(-30).map(p => p.rx + p.tx), [networkPrimary])

  const diskSubtitle = useMemo(() => {
    const disks = systemMetric?.diskInfos ?? []
    const used  = disks.reduce((s, d) => s + (d.totalBytes - d.usableBytes), 0)
    const total = disks.reduce((s, d) => s + d.totalBytes, 0)
    return `${formatBytes(used)} / ${formatBytes(total)}`
  }, [systemMetric])

  const memSubtitle = useMemo(() => {
    if (!systemMetric) return '—'
    return `${formatBytes(systemMetric.memoryUsedBytes)} / ${formatBytes(systemMetric.memoryTotalBytes)}`
  }, [systemMetric])

  return (
    <div className="space-y-5">
      {widgets.serverInfo && <ServerInfoBar />}

      {/* Stat Cards — only shown widgets */}
      {(widgets.cpu || widgets.memory || widgets.disk || widgets.network) && (
        <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">
          {widgets.cpu && (
            <StatCard
              title="CPU"
              value={cpuValue}
              format={formatPercent}
              subtitle={systemMetric ? `${systemMetric.cpuCoreCount} cores` : '—'}
              icon={Cpu}
              color="blue"
              trend={cpuTrend}
              alertThreshold={85}
            />
          )}
          {widgets.memory && (
            <StatCard
              title="Memory"
              value={memValue}
              format={formatPercent}
              subtitle={memSubtitle}
              icon={MemoryStick}
              color="green"
              trend={memTrend}
              alertThreshold={90}
            />
          )}
          {widgets.disk && (
            <StatCard
              title="Disk"
              value={diskPct}
              format={formatPercent}
              subtitle={diskSubtitle}
              icon={HardDrive}
              color="amber"
              alertThreshold={85}
            />
          )}
          {widgets.network && (
            <StatCard
              title="Network"
              value={netTotal}
              format={v => `${formatBytes(v)}/s`}
              subtitle={useMemo(() => {
                const last = networkPrimary[networkPrimary.length - 1]
                if (!last) return '—'
                return `↑${formatBytes(last.tx)}/s  ↓${formatBytes(last.rx)}/s`
              }, [networkPrimary])}
              icon={Network}
              color="cyan"
              trend={netTrend}
            />
          )}
        </div>
      )}

      {/* Charts row 1 */}
      {(widgets.cpu || widgets.memory) && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          {widgets.cpu && (
            <ChartCard title="CPU Usage">
              <CpuChart history={systemHistory} />
            </ChartCard>
          )}
          {widgets.memory && (
            <ChartCard title="Memory Usage">
              <MemoryChart history={systemHistory} />
            </ChartCard>
          )}
        </div>
      )}

      {/* Charts row 2 */}
      {(widgets.network || widgets.disk) && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          {widgets.network && (
            <ChartCard title="Network Traffic">
              <NetworkChart history={networkPrimary} />
            </ChartCard>
          )}
          {widgets.disk && (
            <ChartCard title="Disk Usage" noPad>
              <DiskChart disks={systemMetric?.diskInfos ?? []} />
            </ChartCard>
          )}
        </div>
      )}

      {/* Process Table */}
      {widgets.processes && (
        <div className="bg-bg-secondary border border-border rounded-xl overflow-hidden">
          <div className="px-5 py-3 border-b border-border">
            <h2 className="text-sm font-semibold text-text-primary">Top Processes</h2>
          </div>
          <ProcessTable processes={processes} />
        </div>
      )}
    </div>
  )
}

interface ChartCardProps {
  title: string
  children: React.ReactNode
  noPad?: boolean
}

function ChartCard({ title, children, noPad = false }: ChartCardProps) {
  return (
    <div className="bg-bg-secondary border border-border rounded-xl overflow-hidden">
      <div className="px-5 py-3 border-b border-border">
        <h2 className="text-sm font-semibold text-text-primary">{title}</h2>
      </div>
      <div className={noPad ? 'px-5 py-3' : 'p-4 h-48'}>
        {children}
      </div>
    </div>
  )
}
