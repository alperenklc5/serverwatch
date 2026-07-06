import { useState } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import { X } from 'lucide-react'
import { cn } from '../../lib/utils'

interface CreateDialogProps {
  open: boolean
  defaultType?: 'FILE' | 'DIRECTORY'
  onOpenChange: (open: boolean) => void
  onConfirm: (name: string, type: 'FILE' | 'DIRECTORY', content: string) => void
}

function validate(name: string): string {
  if (!name.trim()) return 'Name is required'
  if (name.includes('/') || name.includes('\\')) return 'Name cannot contain slashes'
  if (name === '.' || name === '..') return 'Invalid name'
  return ''
}

export default function CreateDialog({
  open, defaultType = 'FILE', onOpenChange, onConfirm,
}: CreateDialogProps) {
  const [type, setType]       = useState<'FILE' | 'DIRECTORY'>(defaultType)
  const [name, setName]       = useState('')
  const [content, setContent] = useState('')
  const [error, setError]     = useState('')

  function reset() {
    setName('')
    setContent('')
    setError('')
  }

  function handleSubmit() {
    const err = validate(name)
    if (err) { setError(err); return }
    onConfirm(name.trim(), type, content)
    onOpenChange(false)
    reset()
  }

  return (
    <Dialog.Root open={open} onOpenChange={v => { if (!v) reset(); onOpenChange(v) }}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-black/60 z-50 backdrop-blur-sm" />
        <Dialog.Content className="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-50 bg-bg-secondary border border-border rounded-xl shadow-2xl w-full max-w-md p-6 focus:outline-none">
          <div className="flex items-center justify-between mb-5">
            <Dialog.Title className="text-base font-semibold text-text-primary">
              New {type === 'FILE' ? 'File' : 'Folder'}
            </Dialog.Title>
            <Dialog.Close asChild>
              <button className="p-1.5 rounded text-text-tertiary hover:text-text-primary hover:bg-bg-tertiary transition-colors">
                <X className="w-4 h-4" />
              </button>
            </Dialog.Close>
          </div>
          <Dialog.Description className="sr-only">Create a new file or folder</Dialog.Description>

          {/* Type toggle */}
          <div className="flex rounded-lg border border-border overflow-hidden mb-4">
            {(['FILE', 'DIRECTORY'] as const).map(t => (
              <button
                key={t}
                onClick={() => setType(t)}
                className={cn(
                  'flex-1 py-2 text-sm transition-colors',
                  type === t
                    ? 'bg-accent-blue/20 text-accent-blue font-medium'
                    : 'text-text-secondary hover:bg-bg-tertiary',
                )}
              >
                {t === 'FILE' ? 'File' : 'Folder'}
              </button>
            ))}
          </div>

          <div className="space-y-4">
            <div>
              <label className="text-xs text-text-secondary mb-1.5 block">Name</label>
              <input
                autoFocus
                value={name}
                onChange={e => { setName(e.target.value); setError('') }}
                onKeyDown={e => e.key === 'Enter' && handleSubmit()}
                placeholder={type === 'FILE' ? 'filename.txt' : 'folder-name'}
                className="w-full px-3 py-2 bg-bg-primary border border-border rounded-lg text-sm text-text-primary placeholder:text-text-tertiary focus:outline-none focus:border-accent-blue transition-colors"
              />
              {error && <p className="text-xs text-accent-red mt-1">{error}</p>}
            </div>

            {type === 'FILE' && (
              <div>
                <label className="text-xs text-text-secondary mb-1.5 block">Initial content (optional)</label>
                <textarea
                  value={content}
                  onChange={e => setContent(e.target.value)}
                  rows={4}
                  className="w-full px-3 py-2 bg-bg-primary border border-border rounded-lg text-sm text-text-primary font-mono placeholder:text-text-tertiary focus:outline-none focus:border-accent-blue transition-colors resize-none"
                />
              </div>
            )}
          </div>

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
              Create
            </button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
