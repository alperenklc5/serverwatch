import { useEffect, useRef, useState } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import { X } from 'lucide-react'

interface RenameDialogProps {
  open: boolean
  currentName: string
  onOpenChange: (open: boolean) => void
  onConfirm: (newName: string) => void
}

export default function RenameDialog({ open, currentName, onOpenChange, onConfirm }: RenameDialogProps) {
  const [name, setName] = useState(currentName)
  const [error, setError] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!open) return
    setName(currentName)
    setError('')
    // Auto-select the filename part (without extension)
    setTimeout(() => {
      const el = inputRef.current
      if (!el) return
      el.focus()
      const dotIdx = currentName.lastIndexOf('.')
      if (dotIdx > 0) el.setSelectionRange(0, dotIdx)
      else el.select()
    }, 30)
  }, [open, currentName])

  function handleSubmit() {
    const n = name.trim()
    if (!n) { setError('Name is required'); return }
    if (n.includes('/') || n.includes('\\')) { setError('Name cannot contain slashes'); return }
    if (n === '.' || n === '..') { setError('Invalid name'); return }
    if (n === currentName) { onOpenChange(false); return }
    onConfirm(n)
    onOpenChange(false)
  }

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-black/60 z-50 backdrop-blur-sm" />
        <Dialog.Content className="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-50 bg-bg-secondary border border-border rounded-xl shadow-2xl w-full max-w-sm p-6 focus:outline-none">
          <div className="flex items-center justify-between mb-4">
            <Dialog.Title className="text-base font-semibold text-text-primary">Rename</Dialog.Title>
            <Dialog.Close asChild>
              <button className="p-1.5 rounded text-text-tertiary hover:text-text-primary hover:bg-bg-tertiary transition-colors">
                <X className="w-4 h-4" />
              </button>
            </Dialog.Close>
          </div>
          <Dialog.Description className="sr-only">Enter a new name for the file or folder</Dialog.Description>

          <input
            ref={inputRef}
            value={name}
            onChange={e => { setName(e.target.value); setError('') }}
            onKeyDown={e => { if (e.key === 'Enter') handleSubmit() }}
            className="w-full px-3 py-2 bg-bg-primary border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-accent-blue transition-colors"
          />
          {error && <p className="text-xs text-accent-red mt-1.5">{error}</p>}

          <div className="flex justify-end gap-3 mt-5">
            <Dialog.Close asChild>
              <button className="px-4 py-2 text-sm text-text-secondary hover:text-text-primary border border-border rounded-lg hover:bg-bg-tertiary transition-colors">
                Cancel
              </button>
            </Dialog.Close>
            <button
              onClick={handleSubmit}
              className="px-4 py-2 text-sm font-medium text-white bg-accent-blue hover:bg-accent-blue/80 rounded-lg transition-colors"
            >
              Rename
            </button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
