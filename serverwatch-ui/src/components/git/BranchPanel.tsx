import { useState } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import { GitBranch, Plus, ChevronRight, ChevronDown, X } from 'lucide-react'
import { useAuthStore } from '../../stores/authStore'
import type { GitBranch as GitBranchType } from '../../types'
import { checkout, createBranch, deleteBranch } from '../../api/git'
import { useToastStore } from '../../stores/toastStore'

interface BranchPanelProps {
  repoId:         string
  branches:       GitBranchType[]
  onBranchChange: () => void
}

interface ContextMenu {
  x:      number
  y:      number
  branch: GitBranchType
}

export default function BranchPanel({ repoId, branches, onBranchChange }: BranchPanelProps) {
  const canWrite = useAuthStore(s => s.hasPermission('GIT_WRITE'))
  const [localOpen, setLocalOpen]     = useState(true)
  const [remoteOpen, setRemoteOpen]   = useState(true)
  const [ctxMenu, setCtxMenu]         = useState<ContextMenu | null>(null)
  const [createOpen, setCreateOpen]   = useState(false)
  const [newName, setNewName]         = useState('')
  const [startPoint, setStartPoint]   = useState('')
  const [loading, setLoading]         = useState(false)

  const localBranches  = branches.filter(b => !b.isRemote)
  const remoteBranches = branches.filter(b => b.isRemote)

  async function handleCheckout(branch: GitBranchType) {
    if (branch.isCurrent || branch.isRemote) return
    setLoading(true)
    try {
      await checkout(repoId, branch.name)
      useToastStore.getState().addToast('success', `Checked out ${branch.name}`)
      onBranchChange()
    } catch {
      useToastStore.getState().addToast('error', `Failed to checkout ${branch.name}`)
    } finally {
      setLoading(false)
    }
  }

  async function handleDelete(branch: GitBranchType) {
    setCtxMenu(null)
    if (branch.isCurrent) {
      useToastStore.getState().addToast('error', 'Cannot delete current branch')
      return
    }
    setLoading(true)
    try {
      await deleteBranch(repoId, branch.name)
      useToastStore.getState().addToast('success', `Deleted ${branch.name}`)
      onBranchChange()
    } catch {
      useToastStore.getState().addToast('error', `Failed to delete ${branch.name}`)
    } finally {
      setLoading(false)
    }
  }

  async function handleCreate() {
    if (!newName.trim()) return
    setLoading(true)
    try {
      await createBranch(repoId, newName.trim(), startPoint.trim() || undefined)
      useToastStore.getState().addToast('success', `Created branch ${newName}`)
      setCreateOpen(false)
      setNewName('')
      setStartPoint('')
      onBranchChange()
    } catch {
      useToastStore.getState().addToast('error', 'Failed to create branch')
    } finally {
      setLoading(false)
    }
  }

  function BranchRow({ branch }: { branch: GitBranchType }) {
    return (
      <div
        onDoubleClick={() => canWrite && void handleCheckout(branch)}
        onContextMenu={e => { e.preventDefault(); setCtxMenu({ x: e.clientX, y: e.clientY, branch }) }}
        className={`flex items-center gap-2 px-3 py-1.5 text-xs cursor-pointer rounded-sm select-none transition-colors ${
          branch.isCurrent
            ? 'bg-accent-blue/10 text-accent-blue'
            : 'text-text-secondary hover:bg-bg-tertiary hover:text-text-primary'
        }`}
      >
        <span className={`w-1.5 h-1.5 rounded-full flex-shrink-0 ${branch.isCurrent ? 'bg-accent-blue' : 'opacity-0'}`} />
        <span className="font-mono truncate">{branch.name}</span>
        {branch.ahead > 0 && (
          <span className="ml-auto text-accent-green text-[10px]">↑{branch.ahead}</span>
        )}
        {branch.behind > 0 && (
          <span className={`text-accent-amber text-[10px] ${branch.ahead === 0 ? 'ml-auto' : ''}`}>↓{branch.behind}</span>
        )}
      </div>
    )
  }

  return (
    <>
      <div className="flex flex-col h-full overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-3 py-2 border-b border-border flex-shrink-0">
          <span className="text-xs font-semibold text-text-secondary uppercase tracking-wider">Branches</span>
          {canWrite && (
            <button
              onClick={() => setCreateOpen(true)}
              disabled={loading}
              className="p-1 rounded hover:bg-bg-tertiary text-text-tertiary hover:text-text-primary transition-colors"
            >
              <Plus className="w-3.5 h-3.5" />
            </button>
          )}
        </div>

        <div className="flex-1 overflow-y-auto py-1">
          {/* Local */}
          <button
            onClick={() => setLocalOpen(o => !o)}
            className="w-full flex items-center gap-1.5 px-3 py-1 text-[10px] text-text-tertiary hover:text-text-secondary font-semibold uppercase tracking-wider"
          >
            {localOpen
              ? <ChevronDown className="w-3 h-3" />
              : <ChevronRight className="w-3 h-3" />
            }
            Local ({localBranches.length})
          </button>
          {localOpen && localBranches.map(b => <BranchRow key={b.name} branch={b} />)}

          {/* Remote */}
          <button
            onClick={() => setRemoteOpen(o => !o)}
            className="w-full flex items-center gap-1.5 px-3 py-1 mt-1 text-[10px] text-text-tertiary hover:text-text-secondary font-semibold uppercase tracking-wider"
          >
            {remoteOpen
              ? <ChevronDown className="w-3 h-3" />
              : <ChevronRight className="w-3 h-3" />
            }
            Remote ({remoteBranches.length})
          </button>
          {remoteOpen && remoteBranches.map(b => <BranchRow key={b.name} branch={b} />)}
        </div>
      </div>

      {/* Context menu */}
      {ctxMenu && (
        <div className="fixed inset-0 z-40" onClick={() => setCtxMenu(null)}>
          <div
            className="absolute bg-bg-secondary border border-border rounded-lg shadow-xl py-1 z-50 w-44"
            style={{ left: ctxMenu.x, top: ctxMenu.y }}
            onClick={e => e.stopPropagation()}
          >
            {canWrite && (
              <>
                <button
                  onClick={() => { void handleCheckout(ctxMenu.branch); setCtxMenu(null) }}
                  disabled={ctxMenu.branch.isCurrent || ctxMenu.branch.isRemote}
                  className="w-full flex items-center gap-2 px-3 py-2 text-sm text-text-primary hover:bg-bg-tertiary text-left disabled:opacity-40"
                >
                  <GitBranch className="w-3.5 h-3.5" />
                  Checkout
                </button>
                <button
                  onClick={() => { setStartPoint(ctxMenu.branch.name); setCreateOpen(true); setCtxMenu(null) }}
                  className="w-full flex items-center gap-2 px-3 py-2 text-sm text-text-primary hover:bg-bg-tertiary text-left"
                >
                  <Plus className="w-3.5 h-3.5" />
                  Branch from here
                </button>
                {!ctxMenu.branch.isRemote && (
                  <button
                    onClick={() => void handleDelete(ctxMenu.branch)}
                    disabled={ctxMenu.branch.isCurrent}
                    className="w-full flex items-center gap-2 px-3 py-2 text-sm text-accent-red hover:bg-bg-tertiary text-left disabled:opacity-40"
                  >
                    <X className="w-3.5 h-3.5" />
                    Delete
                  </button>
                )}
              </>
            )}
            {!canWrite && (
              <p className="px-3 py-2 text-xs text-text-tertiary">No write access</p>
            )}
          </div>
        </div>
      )}

      {/* Create branch dialog */}
      <Dialog.Root
        open={createOpen}
        onOpenChange={v => { if (!v) { setNewName(''); setStartPoint('') } setCreateOpen(v) }}
      >
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 bg-black/60 z-50 backdrop-blur-sm" />
          <Dialog.Content className="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-50 bg-bg-secondary border border-border rounded-xl shadow-2xl w-full max-w-sm p-6 focus:outline-none">
            <div className="flex items-center justify-between mb-4">
              <Dialog.Title className="text-base font-semibold text-text-primary">Create Branch</Dialog.Title>
              <Dialog.Close asChild>
                <button className="p-1.5 rounded text-text-tertiary hover:text-text-primary hover:bg-bg-tertiary transition-colors">
                  <X className="w-4 h-4" />
                </button>
              </Dialog.Close>
            </div>
            <Dialog.Description className="sr-only">Create a new git branch</Dialog.Description>
            <div className="space-y-3">
              <div>
                <label className="text-xs text-text-secondary block mb-1.5">Branch Name *</label>
                <input
                  autoFocus
                  value={newName}
                  onChange={e => setNewName(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && void handleCreate()}
                  placeholder="feature/my-feature"
                  className="w-full px-3 py-2 bg-bg-primary border border-border rounded-lg text-sm text-text-primary placeholder:text-text-tertiary focus:outline-none focus:border-accent-blue transition-colors font-mono"
                />
              </div>
              <div>
                <label className="text-xs text-text-secondary block mb-1.5">Start Point (optional)</label>
                <input
                  value={startPoint}
                  onChange={e => setStartPoint(e.target.value)}
                  placeholder="main or commit hash"
                  className="w-full px-3 py-2 bg-bg-primary border border-border rounded-lg text-sm text-text-primary placeholder:text-text-tertiary focus:outline-none focus:border-accent-blue transition-colors font-mono"
                />
              </div>
            </div>
            <div className="flex justify-end gap-3 mt-4">
              <Dialog.Close asChild>
                <button className="px-4 py-2 text-sm text-text-secondary border border-border rounded-lg hover:bg-bg-tertiary transition-colors">
                  Cancel
                </button>
              </Dialog.Close>
              <button
                onClick={() => void handleCreate()}
                disabled={!newName.trim() || loading}
                className="px-4 py-2 text-sm font-medium text-white bg-accent-blue hover:bg-accent-blue/80 rounded-lg transition-colors disabled:opacity-50"
              >
                Create
              </button>
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </>
  )
}
