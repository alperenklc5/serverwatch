import { useState } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import { X, Loader2, GitBranch } from 'lucide-react'
import { cloneRepo } from '../../api/git'
import { useToastStore } from '../../stores/toastStore'

interface CloneDialogProps {
  open:         boolean
  onOpenChange: (open: boolean) => void
  onCloned:     () => void
}

function urlToName(url: string): string {
  return url.split('/').pop()?.replace(/\.git$/, '') ?? ''
}

export default function CloneDialog({ open, onOpenChange, onCloned }: CloneDialogProps) {
  const [url, setUrl]       = useState('')
  const [name, setName]     = useState('')
  const [branch, setBranch] = useState('')
  const [loading, setLoading] = useState(false)

  function reset() { setUrl(''); setName(''); setBranch(''); }

  async function handleClone() {
    if (!url.trim()) return
    setLoading(true)
    try {
      await cloneRepo(url.trim(), name.trim() || urlToName(url), branch.trim() || undefined)
      useToastStore.getState().addToast('success', 'Repository cloned')
      onCloned()
      onOpenChange(false)
      reset()
    } catch {
      useToastStore.getState().addToast('error', 'Clone failed — check URL and permissions')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog.Root open={open} onOpenChange={v => { if (!v) reset(); onOpenChange(v) }}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-black/60 z-50 backdrop-blur-sm" />
        <Dialog.Content className="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-50 bg-bg-secondary border border-border rounded-xl shadow-2xl w-full max-w-md p-6 focus:outline-none">
          <div className="flex items-center justify-between mb-5">
            <Dialog.Title className="text-base font-semibold text-text-primary">Clone Repository</Dialog.Title>
            <Dialog.Close asChild>
              <button className="p-1.5 rounded text-text-tertiary hover:text-text-primary hover:bg-bg-tertiary transition-colors">
                <X className="w-4 h-4" />
              </button>
            </Dialog.Close>
          </div>
          <Dialog.Description className="sr-only">Clone a remote git repository</Dialog.Description>

          <div className="space-y-4">
            <div>
              <label className="text-xs text-text-secondary block mb-1.5">Repository URL *</label>
              <input
                autoFocus
                value={url}
                onChange={e => { setUrl(e.target.value); if (!name) setName(urlToName(e.target.value)) }}
                placeholder="https://github.com/user/repo.git"
                className="w-full px-3 py-2 bg-bg-primary border border-border rounded-lg text-sm text-text-primary placeholder:text-text-tertiary focus:outline-none focus:border-accent-blue transition-colors font-mono"
              />
            </div>
            <div>
              <label className="text-xs text-text-secondary block mb-1.5">Name (optional)</label>
              <input
                value={name}
                onChange={e => setName(e.target.value)}
                placeholder={urlToName(url) || 'my-repo'}
                className="w-full px-3 py-2 bg-bg-primary border border-border rounded-lg text-sm text-text-primary placeholder:text-text-tertiary focus:outline-none focus:border-accent-blue transition-colors"
              />
            </div>
            <div>
              <label className="text-xs text-text-secondary block mb-1.5">Branch (optional, default: main)</label>
              <div className="relative">
                <GitBranch className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-text-tertiary pointer-events-none" />
                <input
                  value={branch}
                  onChange={e => setBranch(e.target.value)}
                  placeholder="main"
                  className="w-full pl-9 pr-3 py-2 bg-bg-primary border border-border rounded-lg text-sm text-text-primary placeholder:text-text-tertiary focus:outline-none focus:border-accent-blue transition-colors"
                />
              </div>
            </div>
          </div>

          <div className="flex justify-end gap-3 mt-5">
            <Dialog.Close asChild>
              <button className="px-4 py-2 text-sm text-text-secondary border border-border rounded-lg hover:bg-bg-tertiary transition-colors">
                Cancel
              </button>
            </Dialog.Close>
            <button
              onClick={() => void handleClone()}
              disabled={!url.trim() || loading}
              className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-accent-blue hover:bg-accent-blue/80 rounded-lg transition-colors disabled:opacity-50"
            >
              {loading && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
              Clone
            </button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
