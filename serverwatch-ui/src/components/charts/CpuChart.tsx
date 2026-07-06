import { useMemo } from 'react'
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer, ReferenceLine,
} from 'recharts'
import { format } from 'date-fns'
import type { SystemMetric } from '../../types'

interface CpuChartProps {
  history: SystemMetric[]
}

const TOOLTIP_STYLE = {
  backgroundColor: '#12121a',
  border: '1px solid #2a2a3e',
  borderRadius: '8px',
  color: '#e4e4e7',
  fontSize: '12px',
}

export default function CpuChart({ history }: CpuChartProps) {
  const data = useMemo(
    () => history.map(m => ({
      time: format(new Date(m.timestamp), 'HH:mm:ss'),
      cpu: parseFloat(m.cpuUsagePercent.toFixed(1)),
    })),
    [history],
  )

  const tickInterval = Math.max(Math.floor(data.length / 6) - 1, 0)

  return (
    <ResponsiveContainer width="100%" height="100%">
      <AreaChart data={data} margin={{ top: 4, right: 4, left: -20, bottom: 0 }}>
        <defs>
          <linearGradient id="cpuGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%"  stopColor="#3b82f6" stopOpacity={0.35} />
            <stop offset="95%" stopColor="#3b82f6" stopOpacity={0.02} />
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
          formatter={(v) => [`${(Number(v) || 0).toFixed(1)}%`, 'CPU']}
          labelStyle={{ color: '#a1a1aa', marginBottom: 4 }}
        />
        <ReferenceLine y={80} stroke="#f59e0b" strokeDasharray="4 4" strokeWidth={1} />
        <Area
          type="monotone"
          dataKey="cpu"
          stroke="#3b82f6"
          strokeWidth={2}
          fill="url(#cpuGrad)"
          isAnimationActive={false}
          dot={false}
        />
      </AreaChart>
    </ResponsiveContainer>
  )
}
