import { ChevronRight, Home } from 'lucide-react'
import type { PathBreadcrumb } from '../../types'

interface BreadcrumbProps {
  breadcrumbs: PathBreadcrumb[]
  onNavigate: (path: string) => void
  homePath?: string
}

export default function Breadcrumb({ breadcrumbs, onNavigate, homePath = '/hostfs/opt' }: BreadcrumbProps) {
  return (
    <nav className="flex items-center gap-0.5 text-sm overflow-x-auto min-w-0">
      <button
        onClick={() => onNavigate(homePath)}
        className="flex items-center gap-1 p-1 rounded text-text-secondary hover:text-text-primary hover:bg-bg-tertiary transition-colors flex-shrink-0"
        title={homePath}
      >
        <Home className="w-3.5 h-3.5" />
      </button>
      {breadcrumbs.map((crumb, i) => {
        const isLast = i === breadcrumbs.length - 1
        return (
          <div key={crumb.path} className="flex items-center gap-0.5 min-w-0">
            <ChevronRight className="w-3.5 h-3.5 text-text-tertiary flex-shrink-0" />
            {isLast ? (
              <span className="text-text-primary font-medium truncate max-w-48 px-1">{crumb.name}</span>
            ) : (
              <button
                onClick={() => onNavigate(crumb.path)}
                className="text-text-secondary hover:text-text-primary hover:underline truncate max-w-32 px-1 transition-colors"
              >
                {crumb.name}
              </button>
            )}
          </div>
        )
      })}
    </nav>
  )
}
