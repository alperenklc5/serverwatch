import { NavLink } from 'react-router-dom'
import { LayoutDashboard, Container, FolderOpen, TerminalSquare, GitBranch } from 'lucide-react'
import { cn } from '../../lib/utils'

const MOBILE_NAV = [
  { path: '/dashboard', label: 'Dashboard', Icon: LayoutDashboard },
  { path: '/containers', label: 'Containers', Icon: Container },
  { path: '/files', label: 'Files', Icon: FolderOpen },
  { path: '/terminal', label: 'Terminal', Icon: TerminalSquare },
  { path: '/git', label: 'Git', Icon: GitBranch },
] as const

export default function MobileNav() {
  return (
    <nav className="md:hidden fixed bottom-0 left-0 right-0 bg-bg-secondary border-t border-border flex z-50">
      {MOBILE_NAV.map(({ path, label, Icon }) => (
        <NavLink
          key={path}
          to={path}
          className={({ isActive }) =>
            cn(
              'flex-1 flex flex-col items-center gap-1 py-3 text-xs transition-colors',
              isActive ? 'text-accent-blue' : 'text-text-tertiary hover:text-text-secondary',
            )
          }
        >
          <Icon className="w-5 h-5" />
          <span>{label}</span>
        </NavLink>
      ))}
    </nav>
  )
}
