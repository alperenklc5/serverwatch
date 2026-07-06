import { cn } from '../../lib/utils'
import { formatBytes } from '../../lib/formatters'
import type { DiskInfo } from '../../types'

interface DiskChartProps {
  disks: DiskInfo[]
}

function barColor(pct: number): string {
  if (pct > 80) return 'bg-accent-red'
  if (pct > 60) return 'bg-accent-amber'
  return 'bg-accent-green'
}

export default function DiskChart({ disks }: DiskChartProps) {
  if (disks.length === 0) {
    return <div className="flex items-center justify-center h-full text-text-tertiary text-sm">No disk data</div>
  }

  return (
    <div className="space-y-4 py-2">
      {disks.map(disk => {
        const used = disk.totalBytes - disk.usableBytes
        const pct  = disk.usagePercent

        return (
          <div key={disk.mountPoint} className="space-y-1.5">
            <div className="flex items-center justify-between text-sm">
              <span className="font-mono text-text-primary truncate max-w-[160px]" title={disk.mountPoint}>
                {disk.mountPoint}
              </span>
              <span className={cn(
                'font-mono text-xs font-semibold',
                pct > 80 ? 'text-accent-red' : pct > 60 ? 'text-accent-amber' : 'text-accent-green',
              )}>
                {pct.toFixed(1)}%
              </span>
            </div>

            {/* Track */}
            <div className="h-1.5 bg-bg-primary rounded-full overflow-hidden">
              <div
                className={cn('h-full rounded-full transition-all duration-700', barColor(pct))}
                style={{ width: `${Math.min(pct, 100)}%` }}
              />
            </div>

            <div className="text-xs text-text-tertiary font-mono">
              {formatBytes(used)} / {formatBytes(disk.totalBytes)}
              {disk.type && <span className="ml-2 opacity-60">{disk.type}</span>}
            </div>
          </div>
        )
      })}
    </div>
  )
}
