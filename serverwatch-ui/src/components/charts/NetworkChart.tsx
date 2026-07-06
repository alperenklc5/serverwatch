import { useMemo } from 'react'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer, Legend,
} from 'recharts'
import { format } from 'date-fns'
import type { NetworkDataPoint } from '../../stores/metricsStore'
import { formatBytes } from '../../lib/formatters'

interface NetworkChartProps {
  history: NetworkDataPoint[]
}

const TOOLTIP_STYLE = {
  backgroundColor: '#12121a',
  border: '1px solid #2a2a3e',
  borderRadius: '8px',
  color: '#e4e4e7',
  fontSize: '12px',
}

function fmtSpeed(bytes: number): string {
  return `${formatBytes(bytes)}/s`
}

export default function NetworkChart({ history }: NetworkChartProps) {
  const data = useMemo(
    () => history.map(p => ({
      time: format(new Date(p.time), 'HH:mm:ss'),
      rx: p.rx,
      tx: p.tx,
    })),
    [history],
  )

  const tickInterval = Math.max(Math.floor(data.length / 6) - 1, 0)

  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={data} margin={{ top: 4, right: 4, left: -8, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#2a2a3e" vertical={false} />
        <XAxis
          dataKey="time"
          tick={{ fontSize: 10, fill: '#71717a' }}
          tickLine={false}
          axisLine={false}
          interval={tickInterval}
        />
        <YAxis
          tick={{ fontSize: 10, fill: '#71717a' }}
          tickLine={false}
          axisLine={false}
          tickFormatter={fmtSpeed}
          width={60}
        />
        <Tooltip
          contentStyle={TOOLTIP_STYLE}
          formatter={(v, name) => [fmtSpeed(Number(v) || 0), name === 'rx' ? '↓ Download' : '↑ Upload']}
          labelStyle={{ color: '#a1a1aa', marginBottom: 4 }}
        />
        <Legend
          formatter={(value: string) => (
            <span style={{ fontSize: 11, color: '#a1a1aa' }}>
              {value === 'rx' ? '↓ Download' : '↑ Upload'}
            </span>
          )}
        />
        <Line
          type="monotone"
          dataKey="rx"
          stroke="#06b6d4"
          strokeWidth={2}
          dot={false}
          isAnimationActive={false}
        />
        <Line
          type="monotone"
          dataKey="tx"
          stroke="#8b5cf6"
          strokeWidth={2}
          dot={false}
          isAnimationActive={false}
        />
      </LineChart>
    </ResponsiveContainer>
  )
}
