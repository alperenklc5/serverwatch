import { useState, useEffect, useRef } from 'react'
import { Loader2, GitCommit as GitCommitIcon } from 'lucide-react'
import type { GitCommit } from '../../types'
import { getLog } from '../../api/git'
import { formatRelative } from '../../lib/formatters'
import { useToastStore } from '../../stores/toastStore'

interface CommitHistoryProps {
  repoId:         string
  branch?:        string
  selectedHash:   string | null
  onSelectCommit: (commit: GitCommit) => void
}

const PAGE_SIZE = 30

export default function CommitHistory({ repoId, branch, selectedHash, onSelectCommit }: CommitHistoryProps) {
  const [commits, setCommits]   = useState<GitCommit[]>([])
  const [loading, setLoading]   = useState(false)
  const [hasMore, setHasMore]   = useState(true)
  const skipRef                 = useRef(0)

  async function loadPage(append: boolean) {
    setLoading(true)
    try {
      const result = await getLog(repoId, branch, PAGE_SIZE, skipRef.current)
      setCommits(prev => append ? [...prev, ...result] : result)
      skipRef.current += result.length
      setHasMore(result.length === PAGE_SIZE)
    } catch {
      useToastStore.getState().addToast('error', 'Failed to load commits')
    } finally {
      setLoading(false)
    }
  }

  // Reset and reload when repo/branch changes
  useEffect(() => {
    skipRef.current = 0
    setCommits([])
    setHasMore(true)
    void loadPage(false)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [repoId, branch])

  if (loading && commits.length === 0) {
    return (
      <div className="flex items-center justify-center h-full text-text-tertiary">
        <Loader2 className="w-5 h-5 animate-spin" />
      </div>
    )
  }

  if (commits.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-full text-text-tertiary gap-2">
        <GitCommitIcon className="w-8 h-8" />
        <p className="text-sm">No commits found</p>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full overflow-hidden">
      <div className="flex items-center justify-between px-3 py-2 border-b border-border flex-shrink-0">
        <span className="text-xs font-semibold text-text-secondary uppercase tracking-wider">Commit History</span>
        <span className="text-xs text-text-tertiary">{commits.length}{hasMore ? '+' : ''} commits</span>
      </div>

      <div className="flex-1 overflow-y-auto">
        {commits.map((commit, idx) => (
          <button
            key={commit.hash}
            onClick={() => onSelectCommit(commit)}
            className={`w-full flex gap-3 px-3 py-2.5 text-left hover:bg-bg-tertiary transition-colors border-b border-border/50 ${
              selectedHash === commit.hash ? 'bg-accent-blue/10 border-l-2 border-l-accent-blue' : ''
            }`}
          >
            {/* Graph column */}
            <div className="flex flex-col items-center w-4 flex-shrink-0 pt-0.5">
              <span className={`w-2.5 h-2.5 rounded-full border-2 flex-shrink-0 ${
                idx === 0
                  ? 'bg-accent-blue border-accent-blue'
                  : 'bg-bg-primary border-border'
              }`} />
              {idx < commits.length - 1 && (
                <span className="w-px flex-1 bg-border mt-0.5 min-h-3" />
              )}
            </div>

            <div className="flex-1 min-w-0">
              <p className="text-xs text-text-primary truncate leading-snug">{commit.message}</p>
              <div className="flex items-center gap-2 mt-0.5">
                <span className="font-mono text-[10px] text-accent-blue">{commit.shortHash}</span>
                <span className="text-[10px] text-text-tertiary truncate">{commit.author}</span>
                <span className="text-[10px] text-text-tertiary ml-auto flex-shrink-0">{formatRelative(commit.date)}</span>
              </div>
              {commit.filesChanged > 0 && (
                <div className="flex items-center gap-2 mt-0.5">
                  <span className="text-[10px] text-text-tertiary">{commit.filesChanged} files</span>
                  {commit.insertions > 0 && (
                    <span className="text-[10px] text-accent-green">+{commit.insertions}</span>
                  )}
                  {commit.deletions > 0 && (
                    <span className="text-[10px] text-accent-red">-{commit.deletions}</span>
                  )}
                </div>
              )}
            </div>
          </button>
        ))}

        {hasMore && (
          <button
            onClick={() => void loadPage(true)}
            disabled={loading}
            className="w-full py-3 text-xs text-text-secondary hover:text-text-primary hover:bg-bg-tertiary transition-colors"
          >
            {loading
              ? <Loader2 className="w-3.5 h-3.5 animate-spin mx-auto" />
              : 'Load more'
            }
          </button>
        )}
      </div>
    </div>
  )
}
