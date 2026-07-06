import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { User, Lock, Eye, EyeOff, Activity, Loader2 } from 'lucide-react'
import { useAuthStore } from '../stores/authStore'
import { cn } from '../lib/utils'

export default function LoginPage() {
  const navigate = useNavigate()
  const { login, isAuthenticated, isLoading } = useAuthStore()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      navigate('/dashboard', { replace: true })
    }
  }, [isAuthenticated, isLoading, navigate])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setIsSubmitting(true)
    try {
      await login(username, password)
      navigate('/dashboard', { replace: true })
    } catch {
      setError('Invalid username or password')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen bg-bg-primary flex items-center justify-center p-4 relative overflow-hidden">
      {/* Subtle grid background */}
      <div
        className="absolute inset-0 opacity-[0.03]"
        style={{
          backgroundImage: `
            linear-gradient(var(--color-text-primary) 1px, transparent 1px),
            linear-gradient(90deg, var(--color-text-primary) 1px, transparent 1px)
          `,
          backgroundSize: '40px 40px',
        }}
      />
      {/* Glow */}
      <div className="absolute top-1/3 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 rounded-full blur-3xl pointer-events-none"
        style={{ background: 'rgba(59,130,246,0.05)' }}
      />

      <div className="relative w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center gap-3 mb-3">
            <div className="w-10 h-10 rounded-lg flex items-center justify-center"
              style={{ background: 'rgba(59,130,246,0.15)', border: '1px solid rgba(59,130,246,0.35)' }}
            >
              <Activity className="w-5 h-5 text-accent-blue" />
            </div>
            <span className="text-2xl font-semibold text-text-primary tracking-tight">ServerWatch</span>
          </div>
          <p className="text-text-secondary text-sm">Server Monitoring Dashboard</p>
        </div>

        {/* Card */}
        <div className="bg-bg-secondary border border-border rounded-xl shadow-2xl p-8">
          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Username */}
            <div className="space-y-1.5">
              <label className="text-sm font-medium text-text-secondary">Username</label>
              <div className="relative">
                <User className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-tertiary" />
                <input
                  type="text"
                  value={username}
                  onChange={e => setUsername(e.target.value)}
                  placeholder="Enter username"
                  autoComplete="username"
                  required
                  className={cn(
                    'w-full bg-bg-primary border border-border rounded-lg pl-10 pr-4 py-2.5',
                    'text-text-primary placeholder:text-text-tertiary text-sm',
                    'focus:outline-none focus:border-accent-blue transition-colors',
                  )}
                />
              </div>
            </div>

            {/* Password */}
            <div className="space-y-1.5">
              <label className="text-sm font-medium text-text-secondary">Password</label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-tertiary" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  placeholder="Enter password"
                  autoComplete="current-password"
                  required
                  className={cn(
                    'w-full bg-bg-primary border border-border rounded-lg pl-10 pr-10 py-2.5',
                    'text-text-primary placeholder:text-text-tertiary text-sm',
                    'focus:outline-none focus:border-accent-blue transition-colors',
                  )}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(v => !v)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-text-tertiary hover:text-text-secondary transition-colors"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            {/* Error */}
            {error && (
              <p className="text-accent-red text-sm flex items-center gap-2">
                <span className="w-1.5 h-1.5 rounded-full bg-accent-red inline-block flex-shrink-0" />
                {error}
              </p>
            )}

            {/* Submit */}
            <button
              type="submit"
              disabled={isSubmitting}
              className={cn(
                'w-full bg-accent-blue text-white font-medium',
                'rounded-lg py-2.5 text-sm transition-colors mt-2',
                'flex items-center justify-center gap-2',
                'disabled:opacity-60 disabled:cursor-not-allowed',
                'hover:opacity-90',
              )}
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Signing in…
                </>
              ) : (
                'Sign In'
              )}
            </button>
          </form>
        </div>
      </div>

      {/* Version badge */}
      <div className="fixed bottom-4 right-4 text-text-tertiary text-xs font-mono">v1.0.0</div>
    </div>
  )
}
