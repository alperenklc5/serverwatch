import { useNavigate, useLocation } from 'react-router-dom'
import { Bell } from 'lucide-react'
import { useAuthStore } from '../../stores/authStore'
import { useMetricsStore } from '../../stores/metricsStore'
import { useAlertStore } from '../../stores/alertStore'
import { getInitials } from '../../lib/utils'

const PAGE_TITLES: Record<string, string> = {
  '/dashboard':  'Dashboard',
  '/containers': 'Containers',
  '/files':      'Files',
  '/terminal':   'Terminal',
  '/git':        'Git',
  '/alerts':     'Alerts',
  '/settings':   'Settings',
}

export default function Header() {
  const { pathname }   = useLocation()
  const navigate       = useNavigate()
  const { user }       = useAuthStore()
  const isConnected    = useMetricsStore(s => s.isConnected)
  const unreadCount    = useAlertStore(s => s.unreadCount)
  const title          = PAGE_TITLES[pathname] ?? 'ServerWatch'

  function handleBellClick() {
    navigate('/alerts')
  }

  return (
    <header className="h-14 border-b border-border bg-bg-secondary flex items-center justify-between px-6 flex-shrink-0">
      <h1 className="text-base font-semibold text-text-primary">{title}</h1>

      <div className="flex items-center gap-3">
        {/* WebSocket status */}
        <div className="flex items-center gap-1.5 text-xs">
          <span className={`w-1.5 h-1.5 rounded-full ${isConnected ? 'bg-accent-green animate-pulse' : 'bg-accent-red'}`} />
          <span className={`hidden sm:inline ${isConnected ? 'text-accent-green' : 'text-accent-red'}`}>
            {isConnected ? 'Live' : 'Offline'}
          </span>
        </div>

        {/* Alert bell with unread badge */}
        <button
          onClick={handleBellClick}
          className="relative w-8 h-8 flex items-center justify-center rounded-lg hover:bg-bg-tertiary text-text-secondary hover:text-text-primary transition-colors"
        >
          <Bell className="w-4 h-4" />
          {unreadCount > 0 && (
            <span className="absolute -top-0.5 -right-0.5 flex items-center justify-center min-w-4 h-4 px-0.5 text-[9px] font-bold text-white bg-accent-red rounded-full">
              {unreadCount > 99 ? '99+' : unreadCount}
            </span>
          )}
        </button>

        {/* User avatar */}
        {user && (
          <div
            className="w-8 h-8 rounded-full flex items-center justify-center"
            style={{ background: 'rgba(59,130,246,0.15)', border: '1px solid rgba(59,130,246,0.35)' }}
          >
            <span className="text-accent-blue text-xs font-semibold">
              {getInitials(user.displayName || user.username)}
            </span>
          </div>
        )}
      </div>
    </header>
  )
}
