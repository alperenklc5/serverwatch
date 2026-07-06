import { useState, useMemo } from 'react'
import { ChevronUp, ChevronDown } from 'lucide-react'
import { cn } from '../../lib/utils'
import { formatBytes } from '../../lib/formatters'
import type { ProcessInfo } from '../../types'

interface ProcessTableProps {
  processes: ProcessInfo[]
}

type SortKey = keyof Pick<ProcessInfo, 'pid' | 'name' | 'cpuPercent' | 'memoryBytes' | 'user' | 'state'>
type SortDir = 'asc' | 'desc'

const STATE_BADGE: Record<string, string> = {
  RUNNING:  'bg-accent-green/15 text-accent-green',
  Sleeping: 'bg-border text-text-tertiary',
  SLEEPING: 'bg-border text-text-tertiary',
  Stopped:  'bg-accent-red/15 text-accent-red',
  STOPPED:  'bg-accent-red/15 text-accent-red',
  Zombie:   'bg-accent-amber/15 text-accent-amber',
  ZOMBIE:   'bg-accent-amber/15 text-accent-amber',
}

function stateBadge(state: string): string {
  return STATE_BADGE[state] ?? 'bg-border text-text-tertiary'
}

interface ColDef {
  key: SortKey
  label: string
  className: string
}

const COLUMNS: ColDef[] = [
  { key: 'pid',         label: 'PID',     className: 'w-20 text-right' },
  { key: 'name',        label: 'Name',    className: 'flex-1' },
  { key: 'cpuPercent',  label: 'CPU %',   className: 'w-20 text-right' },
  { key: 'memoryBytes', label: 'Memory',  className: 'w-24 text-right' },
  { key: 'user',        label: 'User',    className: 'w-24' },
  { key: 'state',       label: 'State',   className: 'w-24' },
]

export default function ProcessTable({ processes }: ProcessTableProps) {
  const [sort, setSort] = useState<{ key: SortKey; dir: SortDir }>({ key: 'cpuPercent', dir: 'desc' })

  const sorted = useMemo(() => {
    const arr = [...processes].sort((a, b) => {
      const av = a[sort.key]
      const bv = b[sort.key]
      if (typeof av === 'number' && typeof bv === 'number') {
        return sort.dir === 'asc' ? av - bv : bv - av
      }
      return sort.dir === 'asc'
        ? String(av).localeCompare(String(bv))
        : String(bv).localeCompare(String(av))
    })
    return arr.slice(0, 20)
  }, [processes, sort])

  const toggleSort = (key: SortKey) => {
    setSort(prev =>
      prev.key === key
        ? { key, dir: prev.dir === 'asc' ? 'desc' : 'asc' }
        : { key, dir: 'desc' },
    )
  }

  const SortIcon = ({ col }: { col: SortKey }) => {
    if (sort.key !== col) return <ChevronDown className="w-3 h-3 opacity-30" />
    return sort.dir === 'asc'
      ? <ChevronUp   className="w-3 h-3 text-accent-blue" />
      : <ChevronDown className="w-3 h-3 text-accent-blue" />
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm min-w-[600px]">
        <thead>
          <tr className="border-b border-border">
            {COLUMNS.map(col => (
              <th
                key={col.key}
                onClick={() => toggleSort(col.key)}
                className={cn(
                  'py-2 px-3 text-left text-text-secondary font-medium cursor-pointer',
                  'hover:text-text-primary select-none transition-colors',
                  col.className,
                )}
              >
                <span className="inline-flex items-center gap-1">
                  {col.label}
                  <SortIcon col={col.key} />
                </span>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {sorted.map((proc, idx) => (
            <tr
              key={proc.pid}
              className={cn(
                'border-b border-border/50 hover:bg-bg-tertiary/50 transition-colors',
                idx % 2 === 0 ? 'bg-bg-secondary' : 'bg-bg-primary',
              )}
            >
              <td className="py-1.5 px-3 text-right font-mono text-text-tertiary">{proc.pid}</td>
              <td className="py-1.5 px-3 text-text-primary font-medium truncate max-w-[200px]">{proc.name}</td>
              <td className={cn(
                'py-1.5 px-3 text-right font-mono',
                proc.cpuPercent > 50 ? 'text-accent-red' :
                proc.cpuPercent > 20 ? 'text-accent-amber' : 'text-text-primary',
              )}>
                {proc.cpuPercent.toFixed(1)}
              </td>
              <td className="py-1.5 px-3 text-right font-mono text-text-secondary">
                {formatBytes(proc.memoryBytes)}
              </td>
              <td className="py-1.5 px-3 text-text-secondary truncate">{proc.user}</td>
              <td className="py-1.5 px-3">
                <span className={cn('text-xs px-2 py-0.5 rounded-full font-medium', stateBadge(proc.state))}>
                  {proc.state}
                </span>
              </td>
            </tr>
          ))}
          {sorted.length === 0 && (
            <tr>
              <td colSpan={6} className="py-8 text-center text-text-tertiary">No process data yet</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}
