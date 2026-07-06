import { Container, Image, Cpu, HardDrive } from 'lucide-react'
import type { DockerInfoDTO } from '../../types'
import { formatBytes } from '../../lib/formatters'
import { cn } from '../../lib/utils'

interface DockerOverviewProps {
  info: DockerInfoDTO | null
  totalCpu: number
  totalMemory: number
}

interface BadgeProps {
  label: string
  value: number | string
  color: string
  icon: typeof Container
}

function Badge({ label, value, color, icon: Icon }: BadgeProps) {
  return (
    <div className="flex items-center gap-3">
      <div className={cn('w-8 h-8 rounded-lg flex items-center justify-center', color)}>
        <Icon className="w-4 h-4" />
      </div>
      <div>
        <div className="text-lg font-semibold font-mono text-text-primary leading-none">{value}</div>
        <div className="text-xs text-text-secondary mt-0.5">{label}</div>
      </div>
    </div>
  )
}

export default function DockerOverview({ info, totalCpu, totalMemory }: DockerOverviewProps) {
  return (
    <div className="bg-bg-secondary border border-border rounded-xl px-5 py-4">
      <div className="flex flex-wrap items-center gap-x-8 gap-y-4">
        <Badge
          label="Running"
          value={info?.runningContainers ?? 0}
          color="bg-accent-green/10 text-accent-green"
          icon={Container}
        />
        <Divider />
        <Badge
          label="Stopped"
          value={info?.stoppedContainers ?? 0}
          color="bg-accent-red/10 text-accent-red"
          icon={Container}
        />
        <Divider />
        <Badge
          label="Paused"
          value={info?.pausedContainers ?? 0}
          color="bg-accent-amber/10 text-accent-amber"
          icon={Container}
        />
        <Divider />
        <Badge
          label="Images"
          value={info?.totalImages ?? 0}
          color="bg-accent-blue/10 text-accent-blue"
          icon={Image}
        />
        <Divider />
        <Badge
          label="CPU (total)"
          value={`${totalCpu.toFixed(1)}%`}
          color="bg-accent-blue/10 text-accent-blue"
          icon={Cpu}
        />
        <Divider />
        <Badge
          label="Memory (total)"
          value={formatBytes(totalMemory)}
          color="bg-accent-purple/10 text-accent-purple"
          icon={HardDrive}
        />
        {info && (
          <>
            <Divider />
            <div className="text-xs text-text-tertiary font-mono ml-auto">
              Docker {info.dockerVersion} · {info.operatingSystem}
            </div>
          </>
        )}
      </div>
    </div>
  )
}

function Divider() {
  return <span className="hidden sm:block w-px h-8 bg-border flex-shrink-0" />
}
