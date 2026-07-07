import { useState } from 'react'
import { Eye, EyeOff, Lock, ShieldAlert } from 'lucide-react'
import { changePassword } from '../../api/auth'
import { useToastStore } from '../../stores/toastStore'
import { cn } from '../../lib/utils'

function strength(pw: string): { label: string; color: string; width: string } {
  if (pw.length === 0) return { label: '', color: '', width: 'w-0' }
  let score = 0
  if (pw.length >= 8)  score++
  if (pw.length >= 12) score++
  if (/[A-Z]/.test(pw)) score++
  if (/[0-9]/.test(pw)) score++
  if (/[^A-Za-z0-9]/.test(pw)) score++
  if (score <= 2) return { label: 'Weak',   color: 'bg-accent-red',   width: 'w-1/3' }
  if (score <= 3) return { label: 'Medium', color: 'bg-accent-amber', width: 'w-2/3' }
  return               { label: 'Strong',  color: 'bg-accent-green', width: 'w-full' }
}

function PasswordField({
  label, value, show, onChange, onToggle, placeholder,
}: {
  label: string
  value: string
  show: boolean
  onChange: (v: string) => void
  onToggle: () => void
  placeholder?: string
}) {
  return (
    <div>
      <label className="block text-sm text-text-secondary mb-1.5">{label}</label>
      <div className="relative">
        <input
          type={show ? 'text' : 'password'}
          value={value}
          onChange={e => onChange(e.target.value)}
          placeholder={placeholder}
          className="w-full px-3 py-2 pr-10 bg-bg-primary border border-border rounded-lg text-sm text-text-primary placeholder:text-text-tertiary focus:outline-none focus:border-accent-blue"
        />
        <button
          type="button"
          onClick={onToggle}
          className="absolute right-2.5 top-1/2 -translate-y-1/2 text-text-tertiary hover:text-text-secondary transition-colors"
        >
          {show ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
        </button>
      </div>
    </div>
  )
}

export default function ChangePasswordForm() {
  const addToast = useToastStore(s => s.addToast)

  const [current,    setCurrent]    = useState('')
  const [next,       setNext]       = useState('')
  const [confirm,    setConfirm]    = useState('')
  const [showCurr,   setShowCurr]   = useState(false)
  const [showNext,   setShowNext]   = useState(false)
  const [showConf,   setShowConf]   = useState(false)
  const [error,      setError]      = useState('')
  const [submitting, setSubmitting] = useState(false)

  const pw = strength(next)
  const mismatch = confirm.length > 0 && next !== confirm

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    if (next.length < 8) { setError('New password must be at least 8 characters.'); return }
    if (next !== confirm) { setError('Passwords do not match.'); return }
    setSubmitting(true)
    try {
      await changePassword(current, next)
      addToast('success', 'Password changed. Other device sessions have been invalidated.')
      setCurrent(''); setNext(''); setConfirm('')
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : ''
      setError(msg.includes('400') || msg.includes('incorrect')
        ? 'Current password is incorrect.'
        : 'Failed to change password. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="bg-bg-secondary border border-border rounded-xl p-6">
      <div className="flex items-center gap-2 mb-5">
        <Lock className="w-4 h-4 text-text-tertiary" />
        <h2 className="text-sm font-semibold text-text-primary">Change Password</h2>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4 max-w-sm">
        <PasswordField
          label="Current Password"
          value={current}
          show={showCurr}
          onChange={setCurrent}
          onToggle={() => setShowCurr(v => !v)}
        />
        <div>
          <PasswordField
            label="New Password"
            value={next}
            show={showNext}
            onChange={setNext}
            onToggle={() => setShowNext(v => !v)}
            placeholder="Minimum 8 characters"
          />
          {next.length > 0 && (
            <div className="mt-1.5">
              <div className="h-1 bg-bg-tertiary rounded-full overflow-hidden">
                <div className={cn('h-full rounded-full transition-all', pw.color, pw.width)} />
              </div>
              <p className={cn('text-xs mt-1', pw.color.replace('bg-', 'text-'))}>{pw.label}</p>
            </div>
          )}
        </div>
        <div>
          <PasswordField
            label="Confirm New Password"
            value={confirm}
            show={showConf}
            onChange={setConfirm}
            onToggle={() => setShowConf(v => !v)}
          />
          {mismatch && <p className="text-xs text-accent-red mt-1">Passwords do not match.</p>}
        </div>

        {error && (
          <div className="flex items-center gap-2 text-sm text-accent-red bg-accent-red/10 border border-accent-red/20 rounded-lg px-3 py-2">
            <ShieldAlert className="w-4 h-4 flex-shrink-0" />
            {error}
          </div>
        )}

        <div className="flex items-center justify-between pt-1">
          <p className="text-xs text-text-tertiary">Changing password logs you out on other devices.</p>
          <button
            type="submit"
            disabled={submitting || !current || !next || !confirm}
            className="px-4 py-2 text-sm font-medium text-white bg-accent-blue hover:bg-accent-blue/80 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {submitting ? 'Saving…' : 'Change Password'}
          </button>
        </div>
      </form>
    </div>
  )
}
