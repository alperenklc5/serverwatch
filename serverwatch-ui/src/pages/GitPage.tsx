import { useState, useEffect, useCallback } from 'react'
import type { GitRepo, GitBranch, GitCommit, GitStatus } from '../types'
import { listRepos, getBranches, getStatus, pull, push, fetchRemote } from '../api/git'
import RepoSelector   from '../components/git/RepoSelector'
import BranchPanel    from '../components/git/BranchPanel'
import CommitHistory  from '../components/git/CommitHistory'
import DiffViewer     from '../components/git/DiffViewer'
import GitStatusBar   from '../components/git/GitStatusBar'
import { useToastStore } from '../stores/toastStore'
import { GitFork } from 'lucide-react'

export default function GitPage() {
  const [repos, setRepos]               = useState<GitRepo[]>([])
  const [selectedRepo, setSelectedRepo] = useState<GitRepo | null>(null)
  const [branches, setBranches]         = useState<GitBranch[]>([])
  const [status, setStatus]             = useState<GitStatus | null>(null)
  const [selectedCommit, setSelectedCommit] = useState<GitCommit | null>(null)
  const [actionLoading, setActionLoading]   = useState(false)

  const loadRepos = useCallback(async () => {
    try {
      const data = await listRepos()
      setRepos(data)
      setSelectedRepo(prev => {
        if (prev) {
          return data.find(r => r.repoId === prev.repoId) ?? data[0] ?? null
        }
        return data[0] ?? null
      })
    } catch {
      useToastStore.getState().addToast('error', 'Failed to load repositories')
    }
  }, [])

  const loadBranches = useCallback(async (repoId: string) => {
    try {
      const data = await getBranches(repoId)
      setBranches(data)
    } catch {
      useToastStore.getState().addToast('error', 'Failed to load branches')
    }
  }, [])

  const loadStatus = useCallback(async (repoId: string) => {
    try {
      const data = await getStatus(repoId)
      setStatus(data)
    } catch {
      setStatus(null)
    }
  }, [])

  useEffect(() => { void loadRepos() }, [loadRepos])

  useEffect(() => {
    if (!selectedRepo) { setBranches([]); setStatus(null); return }
    void loadBranches(selectedRepo.repoId)
    void loadStatus(selectedRepo.repoId)
    setSelectedCommit(null)
  }, [selectedRepo, loadBranches, loadStatus])

  async function handleFetch() {
    if (!selectedRepo) return
    setActionLoading(true)
    try {
      await fetchRemote(selectedRepo.repoId)
      useToastStore.getState().addToast('success', 'Fetch complete')
      await loadBranches(selectedRepo.repoId)
    } catch {
      useToastStore.getState().addToast('error', 'Fetch failed')
    } finally {
      setActionLoading(false)
    }
  }

  async function handlePull() {
    if (!selectedRepo) return
    setActionLoading(true)
    try {
      const updated = await pull(selectedRepo.repoId)
      setSelectedRepo(updated)
      setRepos(rs => rs.map(r => r.repoId === updated.repoId ? updated : r))
      useToastStore.getState().addToast('success', 'Pull complete')
    } catch {
      useToastStore.getState().addToast('error', 'Pull failed')
    } finally {
      setActionLoading(false)
    }
  }

  async function handlePush() {
    if (!selectedRepo) return
    setActionLoading(true)
    try {
      await push(selectedRepo.repoId)
      useToastStore.getState().addToast('success', 'Push complete')
    } catch {
      useToastStore.getState().addToast('error', 'Push failed')
    } finally {
      setActionLoading(false)
    }
  }

  async function handleBranchChange() {
    await loadRepos()
  }

  if (repos.length === 0 && !actionLoading) {
    return (
      <div className="flex flex-col items-center justify-center h-96 text-text-tertiary gap-4">
        <GitFork className="w-12 h-12" />
        <p className="text-sm">No repositories yet</p>
        <p className="text-xs">Clone a repository to get started</p>
        <RepoSelector
          repos={[]}
          selected={null}
          onSelect={() => undefined}
          onFetch={() => undefined}
          onPull={() => undefined}
          onPush={() => undefined}
          onCloned={() => void loadRepos()}
          loading={false}
        />
      </div>
    )
  }

  return (
    <div className="flex flex-col -m-6 h-[calc(100vh-56px)] overflow-hidden">
      {/* Toolbar */}
      <RepoSelector
        repos={repos}
        selected={selectedRepo}
        onSelect={repo => { setSelectedRepo(repo); setSelectedCommit(null) }}
        onFetch={() => void handleFetch()}
        onPull={() => void handlePull()}
        onPush={() => void handlePush()}
        onCloned={() => void loadRepos()}
        loading={actionLoading}
      />

      {/* Middle row: branches + history */}
      <div className="flex flex-1 min-h-0 overflow-hidden">
        {/* Left: branch panel */}
        <div className="w-52 flex-shrink-0 border-r border-border bg-bg-secondary overflow-hidden flex flex-col">
          {selectedRepo ? (
            <BranchPanel
              repoId={selectedRepo.repoId}
              branches={branches}
              onBranchChange={() => void handleBranchChange()}
            />
          ) : (
            <div className="flex items-center justify-center h-full text-xs text-text-tertiary p-4 text-center">
              Select a repository
            </div>
          )}
        </div>

        {/* Right: commit history */}
        <div className="flex-1 bg-bg-secondary overflow-hidden flex flex-col">
          {selectedRepo ? (
            <CommitHistory
              repoId={selectedRepo.repoId}
              branch={selectedRepo.currentBranch}
              selectedHash={selectedCommit?.hash ?? null}
              onSelectCommit={setSelectedCommit}
            />
          ) : (
            <div className="flex items-center justify-center h-full text-xs text-text-tertiary">
              Select a repository to view commits
            </div>
          )}
        </div>
      </div>

      {/* Status bar */}
      <div className="border-t border-border bg-bg-secondary flex-shrink-0">
        <GitStatusBar status={status} />
      </div>

      {/* Bottom: diff viewer */}
      <div className="h-72 border-t border-border bg-bg-secondary flex-shrink-0 overflow-hidden">
        <DiffViewer
          repoId={selectedRepo?.repoId ?? null}
          commit={selectedCommit}
        />
      </div>
    </div>
  )
}
