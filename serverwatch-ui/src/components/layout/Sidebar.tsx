import { useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard, Container, FolderOpen, TerminalSquare,
  GitBranch, Bell, Settings, LogOut, Activity,
  ChevronLeft, ChevronRight,
} from 'lucide-react'
import { useAuthStore } from '../../stores/authStore'
import { cn, getInitials } from '../../lib/utils'

const NAV_ITEMS = [
  { path: '/dashboard', label: 'Dashboard', Icon: LayoutDashboard },
  { path: '/containers', label: 'Containers', Icon: Container },
  { path: '/files', label: 'Files', Icon: FolderOpen },
  { path: '/terminal', label: 'Terminal', Icon: TerminalSquare },
  { path: '/git', label: 'Git', Icon: GitBranch },
  { path: '/alerts', label: 'Alerts', Icon: Bell },
] as const

export default function Sidebar() {
  const [collapsed, setCollapsed] = useState(false)
  const { user, logout } = useAuthStore()
  const navigate = useNavigate()

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <aside
      className={cn(
        'hidden md:flex flex-col h-screen bg-bg-secondary border-r border-border flex-shrink-0',
        'transition-all duration-200 ease-in-out',
        collapsed ? 'w-16' : 'w-60',
      )}
    >
      {/* Brand */}
      <div className={cn(
        'flex items-center gap-3 px-4 py-5 border-b border-border',
        collapsed && 'justify-center px-2',
      )}>
        <div
          className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0"
          style={{ background: 'rgba(59,130,246,0.15)', border: '1px solid rgba(59,130,246,0.35)' }}
        >
          <Activity className="w-4 h-4 text-accent-blue" />
        </div>
        {!collapsed && (
          <span className="text-sm font-semibold text-text-primary whitespace-nowrap overflow-hidden">
            ServerWatch
          </span>
        )}
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-2 py-4 space-y-0.5 overflow-y-auto">
        {NAV_ITEMS.map(({ path, label, Icon }) => (
          <NavLink
            key={path}
            to={path}
            title={collapsed ? label : undefined}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors relative',
                collapsed && 'justify-center px-0',
                isActive
                  ? 'bg-bg-tertiary text-text-primary'
                  : 'text-text-secondary hover:bg-bg-tertiary/50 hover:text-text-primary',
              )
            }
          >
            {({ isActive }) => (
              <>
                {isActive && (
                  <span className="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-5 bg-accent-blue rounded-r-full" />
                )}
                <Icon className="w-4 h-4 flex-shrink-0" />
                {!collapsed && <span>{label}</span>}
              </>
            )}
          </NavLink>
        ))}
      </nav>

      {/* Bottom section */}
      <div className="border-t border-border p-2 space-y-0.5">
        <NavLink
          to="/settings"
          title={collapsed ? 'Settings' : undefined}
          className={({ isActive }) =>
            cn(
              'flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors',
              collapsed && 'justify-center px-0',
              isActive
                ? 'bg-bg-tertiary text-text-primary'
                : 'text-text-secondary hover:bg-bg-tertiary/50 hover:text-text-primary',
            )
          }
        >
          <Settings className="w-4 h-4 flex-shrink-0" />
          {!collapsed && <span>Settings</span>}
        </NavLink>

        <button
          onClick={handleLogout}
          title={collapsed ? 'Logout' : undefined}
          className={cn(
            'w-full flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors',
            collapsed && 'justify-center px-0',
            'text-text-secondary hover:bg-accent-red/10 hover:text-accent-red',
          )}
        >
          <LogOut className="w-4 h-4 flex-shrink-0" />
          {!collapsed && <span>Logout</span>}
        </button>

        {/* User info (expanded only) */}
        {user && !collapsed && (
          <div className="flex items-center gap-2 px-3 py-2 mt-1">
            <div
              className="w-6 h-6 rounded-full flex items-center justify-center flex-shrink-0"
              style={{ background: 'rgba(59,130,246,0.15)', border: '1px solid rgba(59,130,246,0.35)' }}
            >
              <span className="text-accent-blue text-xs font-semibold">
                {getInitials(user.displayName || user.username)}
              </span>
            </div>
            <span className="text-xs text-text-secondary truncate">{user.username}</span>
          </div>
        )}

        {/* Collapse toggle */}
        <button
          onClick={() => setCollapsed(v => !v)}
          title={collapsed ? 'Expand' : undefined}
          className={cn(
            'w-full flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors',
            collapsed && 'justify-center px-0',
            'text-text-tertiary hover:text-text-secondary hover:bg-bg-tertiary/50',
          )}
        >
          {collapsed
            ? <ChevronRight className="w-4 h-4" />
            : <><ChevronLeft className="w-4 h-4" /><span>Collapse</span></>
          }
        </button>
      </div>
    </aside>
  )
}
