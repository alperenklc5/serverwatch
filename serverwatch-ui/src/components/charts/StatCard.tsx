import type { LucideIcon } from 'lucide-react'
import { LineChart, Line, ResponsiveContainer } from 'recharts'
import { cn } from '../../lib/utils'
import AnimatedNumber from '../ui/AnimatedNumber'

type Color = 'blue' | 'green' | 'amber' | 'cyan'

interface StatCardProps {
  title: string
  value: number
  format: (n: number) => string
  subtitle: string
  icon: LucideIcon
  color: Color
  trend?: number[]
  alertThreshold?: number
}

const COLOR = {
  blue:  { stroke: '#3b82f6', iconCls: 'text-accent-blue',  bgCls: 'bg-accent-blue/10'  },
  green: { stroke: '#22c55e', iconCls: 'text-accent-green', bgCls: 'bg-accent-green/10' },
  amber: { stroke: '#f59e0b', iconCls: 'text-accent-amber', bgCls: 'bg-accent-amber/10' },
  cyan:  { stroke: '#06b6d4', iconCls: 'text-accent-cyan',  bgCls: 'bg-accent-cyan/10'  },
} as const satisfies Record<Color, { stroke: string; iconCls: string; bgCls: string }>

export default function StatCard({
  title, value, format, subtitle, icon: Icon, color, trend = [], alertThreshold,
}: StatCardProps) {
  const { stroke, iconCls, bgCls } = COLOR[color]
  const isAlert = alertThreshold !== undefined && value > alertThreshold
  const sparkData = trend.map((v, i) => ({ i, v }))

  return (
    <div
      className={cn(
        'bg-bg-secondary border rounded-xl p-4 flex flex-col gap-3',
        'transition-colors duration-300',
        isAlert ? 'border-accent-red shadow-[0_0_12px_rgba(239,68,68,0.15)]' : 'border-border',
      )}
    >
      {/* Header row */}
      <div className="flex items-start justify-between">
        <div className={cn('w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0', bgCls)}>
          <Icon className={cn('w-4 h-4', iconCls)} />
        </div>
        <span className="text-xs text-text-tertiary font-medium uppercase tracking-wider">{title}</span>
      </div>

      {/* Value */}
      <div>
        <div className="text-3xl font-semibold font-mono text-text-primary leading-none">
          <AnimatedNumber value={value} format={format} />
        </div>
        <div className="text-sm text-text-secondary mt-1">{subtitle}</div>
      </div>

      {/* Sparkline */}
      {sparkData.length > 1 && (
        <div className="h-8 -mx-1">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={sparkData}>
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
        </div>
      )}
    </div>
  )
}
