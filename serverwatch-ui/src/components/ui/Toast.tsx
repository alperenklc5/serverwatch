import { CheckCircle, XCircle, Info, X } from 'lucide-react'
import { useToastStore } from '../../stores/toastStore'
import { cn } from '../../lib/utils'
import type { ToastType } from '../../stores/toastStore'

const ICONS: Record<ToastType, typeof CheckCircle> = {
  success: CheckCircle,
  error:   XCircle,
  info:    Info,
}

const COLORS: Record<ToastType, string> = {
  success: 'border-accent-green/40 text-accent-green',
  error:   'border-accent-red/40   text-accent-red',
  info:    'border-accent-blue/40  text-accent-blue',
}

export default function Toaster() {
  const { toasts, removeToast } = useToastStore()

  if (toasts.length === 0) return null

  return (
    <div className="fixed top-4 right-4 z-[9999] flex flex-col gap-2 pointer-events-none">
      {toasts.map(toast => {
        const Icon = ICONS[toast.type]
        return (
          <div
            key={toast.id}
            className={cn(
              'pointer-events-auto flex items-center gap-3 px-4 py-3 rounded-lg',
              'bg-bg-secondary border shadow-xl text-sm max-w-sm',
              'animate-[fadeIn_0.15s_ease-out]',
              COLORS[toast.type],
            )}
          >
            <Icon className="w-4 h-4 flex-shrink-0" />
            <span className="flex-1 text-text-primary">{toast.message}</span>
            <button
              onClick={() => removeToast(toast.id)}
              className="text-text-tertiary hover:text-text-secondary transition-colors"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        )
      })}
    </div>
  )
}
