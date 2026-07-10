import { useState } from 'react'
import { ChevronDown, RefreshCw, ArrowDown, ArrowUp, Plus, CheckCircle2, Circle } from 'lucide-react'
import { useAuthStore, type AuthState } from '../../stores/authStore'
import type { GitRepo } from '../../types'
import CloneDialog from './CloneDialog'

interface RepoSelectorProps {
  repos:    GitRepo[]
  selected: GitRepo | null
  onSelect: (repo: GitRepo) => void
  onFetch:  () => void
  onPull:   () => void
  onPush:   () => void
  onCloned: () => void
  loading?: boolean
}

export default function RepoSelector({
  repos, selected, onSelect, onFetch, onPull, onPush, onCloned, loading,
}: RepoSelectorProps) {
  const hasPermission = useAuthStore((s: AuthState) => s.hasPermission)
  const canWrite      = hasPermission('GIT_WRITE')
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [cloneOpen, setCloneOpen]       = useState(false)

  return (
    <>
      <div className="flex items-center gap-2 px-4 py-2.5 border-b border-border bg-bg-secondary flex-shrink-0">
        {/* Repo dropdown */}
        <div className="relative">
          <button
            onClick={() => setDropdownOpen(o => !o)}
            className="flex items-center gap-2 px-3 py-1.5 bg-bg-tertiary border border-border rounded-lg text-sm text-text-primary hover:border-accent-blue transition-colors min-w-44"
          >
            <span className="font-medium truncate max-w-36">
              {selected?.name ?? 'Select repo'}
            </span>
            <ChevronDown className="w-3.5 h-3.5 text-text-tertiary ml-auto flex-shrink-0" />
          </button>

          {dropdownOpen && (
            <>
              {/* Click-away overlay */}
              <div className="fixed inset-0 z-40" onClick={() => setDropdownOpen(false)} />
              <div className="absolute top-full left-0 mt-1 w-60 bg-bg-secondary border border-border rounded-lg shadow-xl z-50 py-1">
                {repos.length === 0 ? (
                  <p className="px-3 py-2 text-xs text-text-tertiary">No repositories</p>
                ) : (
                  repos.map(repo => (
                    <button
                      key={repo.repoId}
                      onClick={() => { onSelect(repo); setDropdownOpen(false) }}
                      className="w-full flex items-center gap-2 px-3 py-2 text-sm text-text-primary hover:bg-bg-tertiary text-left transition-colors"
                    >
                      {repo.isClean
                        ? <CheckCircle2 className="w-3.5 h-3.5 text-accent-green flex-shrink-0" />
                        : <Circle className="w-3.5 h-3.5 text-accent-amber flex-shrink-0" />
                      }
                      <span className="truncate">{repo.name}</span>
                      <span className="text-xs text-text-tertiary ml-auto flex-shrink-0">{repo.currentBranch}</span>
                    </button>
                  ))
                )}
              </div>
            </>
          )}
        </div>

        {/* Branch + status pill */}
        {selected && (
          <div className="flex items-center gap-1.5 px-2.5 py-1 bg-bg-tertiary rounded-full text-xs border border-border">
            <span className="text-text-secondary">{selected.currentBranch}</span>
            {selected.isClean
              ? <span className="text-accent-green">✓</span>
              : <span className="text-accent-amber">●</span>
            }
          </div>
        )}

        {/* Action buttons */}
        <div className="ml-auto flex items-center gap-1.5">
          {canWrite && (
            <>
              <button
                onClick={onFetch}
                disabled={!selected || loading}
                title="Fetch"
                className="flex items-center gap-1.5 px-2.5 py-1.5 text-xs text-text-secondary hover:text-text-primary hover:bg-bg-tertiary border border-border rounded-lg transition-colors disabled:opacity-40"
              >
                <RefreshCw className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">Fetch</span>
              </button>
              <button
                onClick={onPull}
                disabled={!selected || loading}
                title="Pull"
                className="flex items-center gap-1.5 px-2.5 py-1.5 text-xs text-text-secondary hover:text-text-primary hover:bg-bg-tertiary border border-border rounded-lg transition-colors disabled:opacity-40"
              >
                <ArrowDown className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">Pull</span>
              </button>
              <button
                onClick={onPush}
                disabled={!selected || loading}
                title="Push"
                className="flex items-center gap-1.5 px-2.5 py-1.5 text-xs text-text-secondary hover:text-text-primary hover:bg-bg-tertiary border border-border rounded-lg transition-colors disabled:opacity-40"
              >
                <ArrowUp className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">Push</span>
              </button>
              <button
                onClick={() => setCloneOpen(true)}
                className="flex items-center gap-1.5 px-2.5 py-1.5 text-xs text-accent-blue hover:bg-accent-blue/10 border border-accent-blue/30 rounded-lg transition-colors"
              >
                <Plus className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">Clone</span>
              </button>
            </>
          )}
        </div>
      </div>

      <CloneDialog open={cloneOpen} onOpenChange={setCloneOpen} onCloned={onCloned} />
    </>
  )
}
