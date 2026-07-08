import { useEffect, useState } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import { ShieldCheck, X, AlertTriangle } from 'lucide-react'
import { getUserPermissions, setUserPermissions } from '../../api/settings'
import { useToastStore } from '../../stores/toastStore'
import type { Permission, PermissionInfo, User as UserType } from '../../types'

interface Props {
  user: UserType
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved: () => void
}

const CATEGORY_ORDER = ['Terminal', 'Files', 'Docker', 'Git', 'Alerts', 'Administration']

export default function PermissionEditor({ user, open, onOpenChange, onSaved }: Props) {
  const addToast = useToastStore(s => s.addToast)
  const [permissions, setPermissions] = useState<PermissionInfo[]>([])
  const [loading, setLoading]         = useState(false)
  const [saving, setSaving]           = useState(false)
  const isBuiltinAdmin = user.username === 'admin'

  useEffect(() => {
    if (!open) return
    setLoading(true)
    getUserPermissions(user.id)
      .then(setPermissions)
      .catch(() => addToast('error', 'Failed to load permissions.'))
      .finally(() => setLoading(false))
  }, [open, user.id, addToast])

  function toggle(permission: Permission) {
    if (isBuiltinAdmin) return
    setPermissions(prev =>
      prev.map(p => p.permission === permission ? { ...p, granted: !p.granted } : p),
    )
  }

  async function handleSave() {
    setSaving(true)
    try {
      const granted = permissions.filter(p => p.granted).map(p => p.permission)
      await setUserPermissions(user.id, granted)
      addToast('success', `Permissions updated for ${user.username}.`)
      onSaved()
      onOpenChange(false)
    } catch {
      addToast('error', 'Failed to save permissions.')
    } finally {
      setSaving(false)
    }
  }

  const byCategory = CATEGORY_ORDER.reduce<Record<string, PermissionInfo[]>>((acc, cat) => {
    acc[cat] = permissions.filter(p => p.category === cat)
    return acc
  }, {})

  const wantsUserManagement = permissions.find(p => p.permission === 'USER_MANAGEMENT')?.granted

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-black/60 z-50 backdrop-blur-sm" />
        <Dialog.Content className="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-50 bg-bg-secondary border border-border rounded-xl shadow-2xl w-full max-w-md p-6 focus:outline-none">
          <div className="flex items-center justify-between mb-5">
            <div className="flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-accent-blue" />
              <Dialog.Title className="text-base font-semibold text-text-primary">
                Edit Permissions: {user.username}
              </Dialog.Title>
            </div>
            <Dialog.Close asChild>
              <button className="p-1.5 rounded text-text-tertiary hover:text-text-primary hover:bg-bg-tertiary transition-colors">
                <X className="w-4 h-4" />
              </button>
            </Dialog.Close>
          </div>
          <Dialog.Description className="sr-only">
            Edit permissions for {user.username}
          </Dialog.Description>

          {isBuiltinAdmin && (
            <div className="mb-4 flex items-start gap-2 text-xs text-accent-amber bg-accent-amber/10 border border-accent-amber/20 rounded-lg px-3 py-2">
              <AlertTriangle className="w-3.5 h-3.5 mt-0.5 flex-shrink-0" />
              <span>The built-in <strong>admin</strong> account always has all permissions. These cannot be changed.</span>
            </div>
          )}

          {loading ? (
            <div className="text-sm text-text-tertiary text-center py-6">Loading…</div>
          ) : (
            <div className="space-y-4 max-h-[60vh] overflow-y-auto pr-1">
              {CATEGORY_ORDER.map(cat => {
                const items = byCategory[cat]
                if (!items || items.length === 0) return null
                return (
                  <div key={cat}>
                    <p className="text-[10px] font-semibold uppercase tracking-wider text-text-tertiary mb-2">{cat}</p>
                    <div className="space-y-2">
                      {items.map(perm => (
                        <label
                          key={perm.permission}
                          className={`flex items-center gap-3 cursor-pointer group ${isBuiltinAdmin ? 'cursor-not-allowed' : ''}`}
                          title={isBuiltinAdmin ? 'Cannot be changed for built-in admin' : undefined}
                        >
                          <input
                            type="checkbox"
                            checked={perm.granted}
                            onChange={() => toggle(perm.permission)}
                            disabled={isBuiltinAdmin}
                            className="w-4 h-4 rounded border-border bg-bg-primary accent-accent-blue disabled:cursor-not-allowed"
                          />
                          <span className={`text-sm ${perm.granted ? 'text-text-primary' : 'text-text-secondary'} group-hover:text-text-primary transition-colors`}>
                            {perm.label}
                          </span>
                        </label>
                      ))}
                    </div>
                  </div>
                )
              })}
            </div>
          )}

          {wantsUserManagement && !isBuiltinAdmin && (
            <div className="mt-4 flex items-start gap-2 text-xs text-accent-amber bg-accent-amber/10 border border-accent-amber/20 rounded-lg px-3 py-2">
              <AlertTriangle className="w-3.5 h-3.5 mt-0.5 flex-shrink-0" />
              <span>This will let the user manage other accounts and permissions.</span>
            </div>
          )}

          <div className="flex justify-end gap-3 pt-4 mt-2 border-t border-border">
            <Dialog.Close asChild>
              <button className="px-4 py-2 text-sm text-text-secondary border border-border rounded-lg hover:bg-bg-tertiary transition-colors">
                Cancel
              </button>
            </Dialog.Close>
            {!isBuiltinAdmin && (
              <button
                onClick={handleSave}
                disabled={saving || loading}
                className="px-4 py-2 text-sm font-medium text-white bg-accent-blue hover:bg-accent-blue/80 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {saving ? 'Saving…' : 'Save Changes'}
              </button>
            )}
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
