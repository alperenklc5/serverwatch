import { useState } from 'react'
import { LogOut, User, Shield } from 'lucide-react'
import { logoutAllSessions } from '../../api/settings'
import { useAuthStore } from '../../stores/authStore'
import { useToastStore } from '../../stores/toastStore'
import { formatRelative } from '../../lib/formatters'

export default function ActiveSessionCard() {
  const user      = useAuthStore(s => s.user)
  const addToast  = useToastStore(s => s.addToast)
  const [loading, setLoading] = useState(false)

  async function handleLogoutAll() {
    setLoading(true)
    try {
      await logoutAllSessions()
      addToast('success', 'All other sessions have been logged out.')
    } catch {
      addToast('error', 'Failed to log out other sessions.')
    } finally {
      setLoading(false)
    }
  }

  if (!user) return null

  return (
    <div className="bg-bg-secondary border border-border rounded-xl p-6">
      <div className="flex items-center gap-2 mb-5">
        <User className="w-4 h-4 text-text-tertiary" />
        <h2 className="text-sm font-semibold text-text-primary">Account Info</h2>
      </div>

      <div className="space-y-3 text-sm mb-5">
        <Row label="Username">
          <span className="font-mono text-text-primary">{user.username}</span>
        </Row>
        <Row label="Display Name">
          <span className="text-text-primary">{user.displayName || '—'}</span>
        </Row>
        <Row label="Email">
          <span className="text-text-primary">{user.email || '—'}</span>
        </Row>
        <Row label="Role">
          <span className="flex items-center gap-1.5">
            {user.role === 'ADMIN' && <Shield className="w-3.5 h-3.5 text-accent-amber" />}
            <span className={user.role === 'ADMIN' ? 'text-accent-amber' : 'text-text-primary'}>
              {user.role}
            </span>
          </span>
        </Row>
        <Row label="Last Login">
          <span className="text-text-secondary">{user.lastLoginAt ? formatRelative(user.lastLoginAt) : '—'}</span>
        </Row>
        <Row label="Member Since">
          <span className="text-text-secondary">{user.createdAt ? new Date(user.createdAt).toLocaleDateString() : '—'}</span>
        </Row>
      </div>

      <div className="pt-4 border-t border-border flex items-center justify-between">
        <p className="text-xs text-text-tertiary">Logs out all sessions except this one.</p>
        <button
          onClick={handleLogoutAll}
          disabled={loading}
          className="flex items-center gap-2 px-3 py-1.5 text-sm text-accent-red border border-accent-red/30 rounded-lg hover:bg-accent-red/10 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <LogOut className="w-3.5 h-3.5" />
          {loading ? 'Logging out…' : 'Log Out All Other Devices'}
        </button>
      </div>
    </div>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center gap-4">
      <span className="text-text-secondary w-28 flex-shrink-0">{label}</span>
      {children}
    </div>
  )
}
