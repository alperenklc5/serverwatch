import { useState } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import { X, UserPlus } from 'lucide-react'
import { createUser } from '../../api/settings'
import { useToastStore } from '../../stores/toastStore'
import type { Permission } from '../../types'

interface Props {
  onCreated: () => void
}

const EMPTY = { username: '', email: '', password: '', displayName: '' }

type PermGroup = { label: string; perms: { key: Permission; label: string }[] }

const PERMISSION_GROUPS: PermGroup[] = [
  {
    label: 'Terminal',
    perms: [{ key: 'TERMINAL_ACCESS', label: 'Access web terminal' }],
  },
  {
    label: 'Files',
    perms: [
      { key: 'FILES_VIEW',   label: 'View files' },
      { key: 'FILES_WRITE',  label: 'Write / upload' },
      { key: 'FILES_DELETE', label: 'Delete files' },
    ],
  },
  {
    label: 'Docker',
    perms: [
      { key: 'DOCKER_VIEW',    label: 'View containers' },
      { key: 'DOCKER_CONTROL', label: 'Start / stop' },
      { key: 'DOCKER_DELETE',  label: 'Remove' },
    ],
  },
  {
    label: 'Git',
    perms: [
      { key: 'GIT_VIEW',  label: 'View repos' },
      { key: 'GIT_WRITE', label: 'Clone / pull / push' },
    ],
  },
  {
    label: 'Alerts',
    perms: [
      { key: 'ALERTS_VIEW',   label: 'View alerts' },
      { key: 'ALERTS_MANAGE', label: 'Manage rules' },
    ],
  },
  {
    label: 'Administration',
    perms: [{ key: 'USER_MANAGEMENT', label: 'Manage users & permissions' }],
  },
]

const DEFAULT_PERMISSIONS: Permission[] = ['FILES_VIEW', 'DOCKER_VIEW', 'GIT_VIEW', 'ALERTS_VIEW']

export default function CreateUserDialog({ onCreated }: Props) {
  const addToast = useToastStore(s => s.addToast)
  const [open, setOpen]             = useState(false)
  const [form, setForm]             = useState(EMPTY)
  const [error, setError]           = useState('')
  const [loading, setLoading]       = useState(false)
  const [selectedPerms, setSelectedPerms] = useState<Set<Permission>>(new Set(DEFAULT_PERMISSIONS))

  function setField(field: keyof typeof EMPTY, value: string) {
    setForm(f => ({ ...f, [field]: value }))
  }

  function togglePerm(perm: Permission) {
    setSelectedPerms(prev => {
      const next = new Set(prev)
      if (next.has(perm)) next.delete(perm)
      else next.add(perm)
      return next
    })
  }

  function handleOpenChange(o: boolean) {
    setOpen(o)
    if (!o) {
      setForm(EMPTY)
      setError('')
      setSelectedPerms(new Set(DEFAULT_PERMISSIONS))
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    if (!form.username.trim()) { setError('Username is required.'); return }
    if (form.password.length < 8) { setError('Password must be at least 8 characters.'); return }
    setLoading(true)
    try {
      await createUser(
        form.username.trim(),
        form.email.trim(),
        form.password,
        form.displayName.trim(),
        [...selectedPerms],
      )
      addToast('success', `User "${form.username}" created.`)
      setOpen(false)
      onCreated()
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : ''
      setError(msg.includes('409') || msg.toLowerCase().includes('exist')
        ? 'Username or email already exists.'
        : 'Failed to create user. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog.Root open={open} onOpenChange={handleOpenChange}>
      <Dialog.Trigger asChild>
        <button className="flex items-center gap-2 px-3 py-1.5 text-sm font-medium text-white bg-accent-blue hover:bg-accent-blue/80 rounded-lg transition-colors">
          <UserPlus className="w-3.5 h-3.5" />
          New User
        </button>
      </Dialog.Trigger>

      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-black/60 z-50 backdrop-blur-sm" />
        <Dialog.Content className="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-50 bg-bg-secondary border border-border rounded-xl shadow-2xl w-full max-w-md p-6 focus:outline-none max-h-[90vh] overflow-y-auto">
          <div className="flex items-center justify-between mb-5">
            <Dialog.Title className="text-base font-semibold text-text-primary">Create User</Dialog.Title>
            <Dialog.Close asChild>
              <button className="p-1.5 rounded text-text-tertiary hover:text-text-primary hover:bg-bg-tertiary transition-colors">
                <X className="w-4 h-4" />
              </button>
            </Dialog.Close>
          </div>
          <Dialog.Description className="sr-only">Create a new user account</Dialog.Description>

          <form onSubmit={handleSubmit} className="space-y-4">
            <Field label="Username *">
              <input
                value={form.username}
                onChange={e => setField('username', e.target.value)}
                placeholder="johndoe"
                className="w-full px-3 py-2 bg-bg-primary border border-border rounded-lg text-sm text-text-primary placeholder:text-text-tertiary focus:outline-none focus:border-accent-blue"
              />
            </Field>
            <Field label="Display Name">
              <input
                value={form.displayName}
                onChange={e => setField('displayName', e.target.value)}
                placeholder="John Doe"
                className="w-full px-3 py-2 bg-bg-primary border border-border rounded-lg text-sm text-text-primary placeholder:text-text-tertiary focus:outline-none focus:border-accent-blue"
              />
            </Field>
            <Field label="Email">
              <input
                type="email"
                value={form.email}
                onChange={e => setField('email', e.target.value)}
                placeholder="john@example.com"
                className="w-full px-3 py-2 bg-bg-primary border border-border rounded-lg text-sm text-text-primary placeholder:text-text-tertiary focus:outline-none focus:border-accent-blue"
              />
            </Field>
            <Field label="Password *">
              <input
                type="password"
                value={form.password}
                onChange={e => setField('password', e.target.value)}
                placeholder="Minimum 8 characters"
                className="w-full px-3 py-2 bg-bg-primary border border-border rounded-lg text-sm text-text-primary placeholder:text-text-tertiary focus:outline-none focus:border-accent-blue"
              />
            </Field>

            {/* Permission picker */}
            <div>
              <label className="block text-sm text-text-secondary mb-2">Initial Permissions</label>
              <div className="space-y-3 bg-bg-primary border border-border rounded-lg p-3">
                {PERMISSION_GROUPS.map(group => (
                  <div key={group.label}>
                    <p className="text-[10px] font-semibold uppercase tracking-wider text-text-tertiary mb-1.5">{group.label}</p>
                    <div className="flex flex-wrap gap-x-4 gap-y-1.5">
                      {group.perms.map(p => (
                        <label key={p.key} className="flex items-center gap-2 cursor-pointer">
                          <input
                            type="checkbox"
                            checked={selectedPerms.has(p.key)}
                            onChange={() => togglePerm(p.key)}
                            className="w-3.5 h-3.5 rounded border-border bg-bg-secondary accent-accent-blue"
                          />
                          <span className="text-xs text-text-secondary">{p.label}</span>
                        </label>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {error && (
              <p className="text-xs text-accent-red bg-accent-red/10 border border-accent-red/20 rounded-lg px-3 py-2">
                {error}
              </p>
            )}

            <div className="flex justify-end gap-3 pt-1">
              <Dialog.Close asChild>
                <button type="button" className="px-4 py-2 text-sm text-text-secondary border border-border rounded-lg hover:bg-bg-tertiary transition-colors">
                  Cancel
                </button>
              </Dialog.Close>
              <button
                type="submit"
                disabled={loading}
                className="px-4 py-2 text-sm font-medium text-white bg-accent-blue hover:bg-accent-blue/80 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {loading ? 'Creating…' : 'Create User'}
              </button>
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-sm text-text-secondary mb-1.5">{label}</label>
      {children}
    </div>
  )
}
