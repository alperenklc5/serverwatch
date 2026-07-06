import { useAuthStore } from '../stores/authStore'

export default function SettingsPage() {
  const { user } = useAuthStore()

  return (
    <div>
      <h1 className="text-2xl font-semibold text-text-primary mb-6">Settings</h1>
      <div className="rounded-xl border border-border bg-bg-secondary p-6 space-y-4">
        <h2 className="text-sm font-medium text-text-secondary uppercase tracking-wider">Account</h2>
        <div className="space-y-3 text-sm">
          <div className="flex items-center gap-4">
            <span className="text-text-secondary w-28">Username</span>
            <span className="font-mono text-text-primary">{user?.username}</span>
          </div>
          <div className="flex items-center gap-4">
            <span className="text-text-secondary w-28">Role</span>
            <span className="font-mono text-accent-blue">{user?.role}</span>
          </div>
          <div className="flex items-center gap-4">
            <span className="text-text-secondary w-28">Email</span>
            <span className="font-mono text-text-primary">{user?.email || '—'}</span>
          </div>
          <div className="flex items-center gap-4">
            <span className="text-text-secondary w-28">Display name</span>
            <span className="font-mono text-text-primary">{user?.displayName || '—'}</span>
          </div>
        </div>
      </div>
    </div>
  )
}
