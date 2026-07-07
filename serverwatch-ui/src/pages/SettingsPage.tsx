import { useState } from 'react'
import { User, Users, Palette, Bell, Info, Shield } from 'lucide-react'
import { useAuthStore } from '../stores/authStore'
import { cn } from '../lib/utils'
import ActiveSessionCard    from '../components/settings/ActiveSessionCard'
import ChangePasswordForm   from '../components/settings/ChangePasswordForm'
import UserManagementPanel  from '../components/settings/UserManagementPanel'
import ThemeSelector        from '../components/settings/ThemeSelector'
import DashboardWidgetToggle from '../components/settings/DashboardWidgetToggle'
import TerminalPreferences  from '../components/settings/TerminalPreferences'
import NotificationSettings from '../components/settings/NotificationSettings'
import AboutPanel           from '../components/settings/AboutPanel'

type Tab = 'account' | 'users' | 'appearance' | 'notifications' | 'about'

interface TabDef {
  id: Tab
  label: string
  icon: React.ElementType
  adminOnly?: boolean
}

const TABS: TabDef[] = [
  { id: 'account',       label: 'Account',       icon: User  },
  { id: 'users',         label: 'Users',          icon: Users, adminOnly: true },
  { id: 'appearance',    label: 'Appearance',     icon: Palette },
  { id: 'notifications', label: 'Notifications',  icon: Bell  },
  { id: 'about',         label: 'About',          icon: Info  },
]

export default function SettingsPage() {
  const user         = useAuthStore(s => s.user)
  const isAdmin      = user?.role === 'ADMIN'
  const [tab, setTab] = useState<Tab>('account')

  const visibleTabs = TABS.filter(t => !t.adminOnly || isAdmin)

  return (
    <div>
      <h1 className="text-2xl font-semibold text-text-primary mb-6">Settings</h1>

      <div className="flex gap-6">
        {/* Sidebar */}
        <aside className="w-44 flex-shrink-0">
          <nav className="space-y-0.5">
            {visibleTabs.map(({ id, label, icon: Icon, adminOnly }) => (
              <button
                key={id}
                onClick={() => setTab(id)}
                className={cn(
                  'w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm transition-colors',
                  tab === id
                    ? 'bg-accent-blue/15 text-accent-blue font-medium'
                    : 'text-text-secondary hover:text-text-primary hover:bg-bg-tertiary',
                )}
              >
                <Icon className="w-4 h-4 flex-shrink-0" />
                <span>{label}</span>
                {adminOnly && (
                  <span title="Admin only" className="ml-auto flex-shrink-0"><Shield className="w-3 h-3 text-accent-amber" /></span>
                )}
              </button>
            ))}
          </nav>
        </aside>

        {/* Content */}
        <div className="flex-1 min-w-0 space-y-4">
          {tab === 'account' && (
            <>
              <ActiveSessionCard />
              <ChangePasswordForm />
            </>
          )}

          {tab === 'users' && isAdmin && (
            <UserManagementPanel />
          )}

          {tab === 'appearance' && (
            <>
              <ThemeSelector />
              <DashboardWidgetToggle />
              <TerminalPreferences />
            </>
          )}

          {tab === 'notifications' && (
            <NotificationSettings />
          )}

          {tab === 'about' && (
            <AboutPanel />
          )}
        </div>
      </div>
    </div>
  )
}
