import { useMemo } from 'react'
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer,
} from 'recharts'
import { format } from 'date-fns'
import type { SystemMetric } from '../../types'
import { formatBytes } from '../../lib/formatters'

interface MemoryChartProps {
  history: SystemMetric[]
}

const TOOLTIP_STYLE = {
  backgroundColor: '#12121a',
  border: '1px solid #2a2a3e',
  borderRadius: '8px',
  color: '#e4e4e7',
  fontSize: '12px',
}

export default function MemoryChart({ history }: MemoryChartProps) {
  const data = useMemo(
    () => history.map(m => ({
      time: format(new Date(m.timestamp), 'HH:mm:ss'),
      mem: parseFloat(m.memoryUsagePercent.toFixed(1)),
      used: m.memoryUsedBytes,
      total: m.memoryTotalBytes,
    })),
    [history],
  )

  const tickInterval = Math.max(Math.floor(data.length / 6) - 1, 0)

  return (
    <ResponsiveContainer width="100%" height="100%">
      <AreaChart data={data} margin={{ top: 4, right: 4, left: -20, bottom: 0 }}>
        <defs>
          <linearGradient id="memGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%"  stopColor="#22c55e" stopOpacity={0.35} />
            <stop offset="95%" stopColor="#22c55e" stopOpacity={0.02} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke="#2a2a3e" vertical={false} />
        <XAxis
          dataKey="time"
          tick={{ fontSize: 10, fill: '#71717a' }}
          tickLine={false}
          axisLine={false}
          interval={tickInterval}
        />
        <YAxis
          domain={[0, 100]}
          tick={{ fontSize: 10, fill: '#71717a' }}
          tickLine={false}
          axisLine={false}
          tickFormatter={v => `${v}%`}
        />
        <Tooltip
          contentStyle={TOOLTIP_STYLE}
          formatter={(_v, _name, props) => {
            const payload = (props as { payload?: { used?: number; total?: number } }).payload
            const used  = payload?.used  ?? 0
            const total = payload?.total ?? 0
            return [`${formatBytes(used)} / ${formatBytes(total)}`, 'Memory']
          }}
          labelStyle={{ color: '#a1a1aa', marginBottom: 4 }}
        />
        <Area
          type="monotone"
          dataKey="mem"
          stroke="#22c55e"
          strokeWidth={2}
          fill="url(#memGrad)"
          isAnimationActive={false}
          dot={false}
        />
      </AreaChart>
    </ResponsiveContainer>
  )
}
