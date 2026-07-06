import * as Dialog from '@radix-ui/react-dialog'
import { AlertTriangle } from 'lucide-react'
import { cn } from '../../lib/utils'

interface ConfirmDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: string
  confirmLabel: string
  confirmVariant?: 'danger' | 'warning'
  onConfirm: () => void
  isLoading?: boolean
}

export default function ConfirmDialog({
  open, onOpenChange, title, description,
  confirmLabel, confirmVariant = 'danger',
  onConfirm, isLoading = false,
}: ConfirmDialogProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-black/60 z-50 backdrop-blur-sm" />
        <Dialog.Content
          className={cn(
            'fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-50',
            'bg-bg-secondary border border-border rounded-xl shadow-2xl',
            'w-full max-w-md p-6 focus:outline-none',
          )}
        >
          <div className="flex items-start gap-4 mb-5">
            <div className={cn(
              'w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0',
              confirmVariant === 'danger' ? 'bg-accent-red/15' : 'bg-accent-amber/15',
            )}>
              <AlertTriangle className={cn(
                'w-5 h-5',
                confirmVariant === 'danger' ? 'text-accent-red' : 'text-accent-amber',
              )} />
            </div>
            <div>
              <Dialog.Title className="text-base font-semibold text-text-primary">
                {title}
              </Dialog.Title>
              <Dialog.Description className="text-sm text-text-secondary mt-1">
                {description}
              </Dialog.Description>
            </div>
          </div>

          <div className="flex justify-end gap-3">
            <Dialog.Close asChild>
              <button className="px-4 py-2 text-sm text-text-secondary hover:text-text-primary border border-border rounded-lg hover:bg-bg-tertiary transition-colors">
                Cancel
              </button>
            </Dialog.Close>
            <button
              onClick={() => { onConfirm(); onOpenChange(false) }}
              disabled={isLoading}
              className={cn(
                'px-4 py-2 text-sm font-medium text-white rounded-lg transition-colors disabled:opacity-60',
                confirmVariant === 'danger'
                  ? 'bg-accent-red hover:bg-accent-red/80'
                  : 'bg-accent-amber hover:bg-accent-amber/80',
              )}
            >
              {isLoading ? 'Please wait…' : confirmLabel}
            </button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
