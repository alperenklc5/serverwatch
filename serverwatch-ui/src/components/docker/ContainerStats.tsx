import { useMemo } from 'react'
import {
  LineChart, Line, XAxis, YAxis, Tooltip,
  ResponsiveContainer, CartesianGrid,
} from 'recharts'
import type { ContainerStats } from '../../types'
import { formatBytes } from '../../lib/formatters'

interface ContainerStatsProps {
  history: ContainerStats[]
}

// Tailwind color class → hex for recharts stroke
const COLORS = {
  blue:   '#3b82f6',
  green:  '#22c55e',
  amber:  '#f59e0b',
  purple: '#8b5cf6',
  cyan:   '#06b6d4',
} as const

type ColorKey = keyof typeof COLORS

function MiniChart({
  data, color, yFormatter,
}: {
  data: { i: number; v: number }[]
  color: ColorKey
  yFormatter: (v: number) => string
}) {
  const stroke = COLORS[color]
  return (
    <ResponsiveContainer width="100%" height={60}>
      <LineChart data={data} margin={{ top: 2, right: 2, left: 2, bottom: 2 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#2a2a3e" />
        <XAxis dataKey="i" hide />
        <YAxis hide domain={['auto', 'auto']} />
        <Tooltip
          contentStyle={{
            backgroundColor: '#12121a',
            border: '1px solid #2a2a3e',
            borderRadius: 6,
            fontSize: 11,
          }}
          labelFormatter={() => ''}
          formatter={(v) => [yFormatter(Number(v) || 0), '']}
        />
        <Line
          type="monotone"
          dataKey="v"
          stroke={stroke}
          strokeWidth={1.5}
          dot={false}
          isAnimationActive={false}
        />
      </LineChart>
    </ResponsiveContainer>
  )
}

interface PanelProps {
  title: string
  badge: string
  color: ColorKey
  data: { i: number; v: number }[]
  yFormatter: (v: number) => string
  span2?: boolean
}

function Panel({ title, badge, color, data, yFormatter, span2 }: PanelProps) {
  const textColor = {
    blue:   'text-accent-blue',
    green:  'text-accent-green',
    amber:  'text-accent-amber',
    purple: 'text-accent-purple',
    cyan:   'text-accent-cyan',
  }[color]

  return (
    <div className={`bg-bg-primary rounded-lg p-3${span2 ? ' sm:col-span-2' : ''}`}>
      <div className="flex items-center justify-between mb-2">
        <span className="text-xs text-text-secondary font-medium">{title}</span>
        <span className={`text-xs font-mono font-semibold ${textColor}`}>{badge}</span>
      </div>
      <MiniChart data={data} color={color} yFormatter={yFormatter} />
    </div>
  )
}

export default function ContainerStats({ history }: ContainerStatsProps) {
  const latest = history[history.length - 1]

  const cpuData   = useMemo(() => history.map((s, i) => ({ i, v: s.cpuPercent })), [history])
  const memData   = useMemo(() => history.map((s, i) => ({ i, v: s.memoryUsageBytes })), [history])
  const netRxData = useMemo(() => history.map((s, i) => ({ i, v: s.networkRxBytes })), [history])
  const netTxData = useMemo(() => history.map((s, i) => ({ i, v: s.networkTxBytes })), [history])
  const blockData = useMemo(
    () => history.map((s, i) => ({ i, v: s.blockReadBytes + s.blockWriteBytes })),
    [history],
  )

  if (!latest) {
    return (
      <div className="text-center text-text-tertiary text-sm py-8">
        No stats available yet…
      </div>
    )
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <Panel
        title="CPU"
        badge={`${latest.cpuPercent.toFixed(1)}%`}
        color="blue"
        data={cpuData}
        yFormatter={v => `${v.toFixed(1)}%`}
      />
      <Panel
        title="Memory"
        badge={`${formatBytes(latest.memoryUsageBytes)} / ${formatBytes(latest.memoryLimitBytes)}`}
        color="green"
        data={memData}
        yFormatter={v => formatBytes(v)}
      />
      <Panel
        title="Net RX"
        badge={formatBytes(latest.networkRxBytes)}
        color="cyan"
        data={netRxData}
        yFormatter={v => formatBytes(v)}
      />
      <Panel
        title="Net TX"
        badge={formatBytes(latest.networkTxBytes)}
        color="purple"
        data={netTxData}
        yFormatter={v => formatBytes(v)}
      />
      <Panel
        title="Block I/O (read + write)"
        badge={`R ${formatBytes(latest.blockReadBytes)} / W ${formatBytes(latest.blockWriteBytes)}`}
        color="amber"
        data={blockData}
        yFormatter={v => formatBytes(v)}
        span2
      />

      <div className="bg-bg-primary rounded-lg p-3 sm:col-span-2 flex items-center justify-between">
        <span className="text-xs text-text-secondary">PIDs</span>
        <span className="text-xs font-mono font-semibold text-text-primary">{latest.pidCount}</span>
      </div>
    </div>
  )
}
