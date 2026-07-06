import type { GitStatus } from '../../types'
import { CheckCircle2, AlertCircle } from 'lucide-react'

interface GitStatusBarProps {
  status: GitStatus | null
}

export default function GitStatusBar({ status }: GitStatusBarProps) {
  if (!status) return null

  const { isClean, modified, added, removed, untracked, conflicting } = status

  if (isClean) {
    return (
      <div className="flex items-center gap-2 px-3 py-1.5 text-xs text-accent-green">
        <CheckCircle2 className="w-3.5 h-3.5" />
        <span>Working tree clean</span>
      </div>
    )
  }

  const items = [
    { count: modified.length, label: 'modified', color: 'text-accent-amber' },
    { count: added.length,    label: 'added',    color: 'text-accent-green' },
    { count: removed.length,  label: 'deleted',  color: 'text-accent-red'   },
    { count: untracked.length,label: 'untracked',color: 'text-text-secondary'},
    { count: conflicting.length, label: 'conflicting', color: 'text-accent-red' },
  ].filter(i => i.count > 0)

  return (
    <div className="flex items-center gap-3 px-3 py-1.5 text-xs">
      <AlertCircle className="w-3.5 h-3.5 text-accent-amber flex-shrink-0" />
      {items.map(item => (
        <span key={item.label} className={item.color}>
          {item.count} {item.label}
        </span>
      ))}
    </div>
  )
}
