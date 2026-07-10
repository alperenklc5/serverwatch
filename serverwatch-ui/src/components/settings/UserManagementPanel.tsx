import { useEffect, useState, useCallback } from 'react'
import { Users, Trash2, ToggleLeft, ToggleRight, Shield, User, KeyRound } from 'lucide-react'
import { getUsers, enableUser, disableUser, deleteUser } from '../../api/settings'
import { useAuthStore, type AuthState } from '../../stores/authStore'
import { useToastStore } from '../../stores/toastStore'
import { formatRelative } from '../../lib/formatters'
import type { User as UserType } from '../../types'
import CreateUserDialog from './CreateUserDialog'
import PermissionEditor from './PermissionEditor'

export default function UserManagementPanel() {
  const currentUser = useAuthStore((s: AuthState) => s.user)
  const addToast    = useToastStore(s => s.addToast)
  const [users, setUsers]           = useState<UserType[]>([])
  const [loading, setLoading]       = useState(true)
  const [busy, setBusy]             = useState<Record<number, boolean>>({})
  const [editingPerms, setEditingPerms] = useState<UserType | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setUsers(await getUsers())
    } catch {
      addToast('error', 'Failed to load users.')
    } finally {
      setLoading(false)
    }
  }, [addToast])

  useEffect(() => { load() }, [load])

  async function withBusy(userId: number, fn: () => Promise<void>) {
    setBusy(b => ({ ...b, [userId]: true }))
    try { await fn() } finally { setBusy(b => ({ ...b, [userId]: false })) }
  }

  async function handleToggle(u: UserType) {
    await withBusy(u.id, async () => {
      try {
        if (u.enabled) { await disableUser(u.id); addToast('info', `${u.username} disabled.`) }
        else           { await enableUser(u.id);  addToast('success', `${u.username} enabled.`) }
        await load()
      } catch {
        addToast('error', 'Failed to update user status.')
      }
    })
  }

  async function handleDelete(u: UserType) {
    if (!confirm(`Delete user "${u.username}"? This cannot be undone.`)) return
    await withBusy(u.id, async () => {
      try {
        await deleteUser(u.id)
        addToast('success', `${u.username} deleted.`)
        await load()
      } catch {
        addToast('error', 'Failed to delete user.')
      }
    })
  }

  return (
    <div className="bg-bg-secondary border border-border rounded-xl p-6">
      <div className="flex items-center justify-between mb-5">
        <div className="flex items-center gap-2">
          <Users className="w-4 h-4 text-text-tertiary" />
          <h2 className="text-sm font-semibold text-text-primary">User Management</h2>
          <span className="text-xs text-text-tertiary bg-bg-tertiary px-2 py-0.5 rounded-full">Admin</span>
        </div>
        <CreateUserDialog onCreated={load} />
      </div>

      {loading ? (
        <div className="text-sm text-text-tertiary py-4 text-center">Loading users…</div>
      ) : (
        <div className="space-y-2">
          {users.map(u => {
            const isSelf    = u.id === currentUser?.id
            const isBusy    = busy[u.id] ?? false
            return (
              <div
                key={u.id}
                className="flex items-center gap-3 p-3 rounded-lg bg-bg-primary border border-border"
              >
                <div className="w-8 h-8 rounded-full bg-bg-tertiary flex items-center justify-center flex-shrink-0">
                  {u.role === 'ADMIN'
                    ? <Shield className="w-4 h-4 text-accent-amber" />
                    : <User className="w-4 h-4 text-text-tertiary" />
                  }
                </div>

                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-sm font-medium text-text-primary">{u.username}</span>
                    {isSelf && <span className="text-[10px] text-accent-blue bg-accent-blue/10 px-1.5 py-0.5 rounded-full">You</span>}
                    <span className={`text-[10px] px-1.5 py-0.5 rounded-full ${
                      u.enabled
                        ? 'text-accent-green bg-accent-green/10'
                        : 'text-text-tertiary bg-bg-tertiary'
                    }`}>
                      {u.enabled ? 'Active' : 'Disabled'}
                    </span>
                  </div>
                  <div className="text-xs text-text-tertiary mt-0.5 truncate">
                    {u.email || 'No email'} · {u.role} · Last login: {u.lastLoginAt ? formatRelative(u.lastLoginAt) : 'Never'}
                  </div>
                </div>

                <div className="flex items-center gap-1 flex-shrink-0">
                  <button
                    onClick={() => setEditingPerms(u)}
                    title="Edit permissions"
                    className="p-1.5 rounded text-text-tertiary hover:text-accent-blue hover:bg-accent-blue/10 transition-colors"
                  >
                    <KeyRound className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => handleToggle(u)}
                    disabled={isBusy || isSelf}
                    title={u.enabled ? 'Disable user' : 'Enable user'}
                    className="p-1.5 rounded text-text-tertiary hover:text-text-primary hover:bg-bg-tertiary transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                  >
                    {u.enabled
                      ? <ToggleRight className="w-4 h-4 text-accent-green" />
                      : <ToggleLeft className="w-4 h-4" />
                    }
                  </button>
                  <button
                    onClick={() => handleDelete(u)}
                    disabled={isBusy || isSelf}
                    title="Delete user"
                    className="p-1.5 rounded text-text-tertiary hover:text-accent-red hover:bg-accent-red/10 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {editingPerms && (
        <PermissionEditor
          user={editingPerms}
          open={!!editingPerms}
          onOpenChange={open => { if (!open) setEditingPerms(null) }}
          onSaved={load}
        />
      )}
    </div>
  )
}
