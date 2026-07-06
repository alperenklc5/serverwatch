import { useState, useEffect } from 'react'
import { Loader2, FileText, Plus, Minus, ChevronDown, ChevronRight } from 'lucide-react'
import type { GitCommit, GitDiff, GitDiffEntry } from '../../types'
import { getDiff } from '../../api/git'
import { useToastStore } from '../../stores/toastStore'

const CHANGE_COLORS: Record<string, string> = {
  ADD:    'text-accent-green',
  MODIFY: 'text-accent-amber',
  DELETE: 'text-accent-red',
  RENAME: 'text-accent-blue',
  COPY:   'text-accent-blue',
}

const CHANGE_LABEL: Record<string, string> = {
  ADD:    'A',
  MODIFY: 'M',
  DELETE: 'D',
  RENAME: 'R',
  COPY:   'C',
}

type LineType = 'add' | 'remove' | 'hunk' | 'context'

function parsePatch(patch: string): Array<{ content: string; type: LineType }> {
  if (!patch) return []
  return patch.split('\n').map(line => ({
    content: line,
    type: (
      (line.startsWith('+') && !line.startsWith('+++')) ? 'add' :
      (line.startsWith('-') && !line.startsWith('---')) ? 'remove' :
      line.startsWith('@@') ? 'hunk' :
      'context'
    ) as LineType,
  }))
}

function FileEntry({ entry }: { entry: GitDiffEntry }) {
  const [open, setOpen] = useState(true)
  const lines = parsePatch(entry.patch)
  const displayPath = entry.changeType === 'RENAME'
    ? `${entry.oldPath} → ${entry.newPath}`
    : (entry.newPath || entry.oldPath)

  return (
    <div className="border border-border rounded-lg overflow-hidden">
      <button
        onClick={() => setOpen(o => !o)}
        className="w-full flex items-center gap-2 px-3 py-2 bg-bg-tertiary text-sm text-text-primary hover:bg-bg-tertiary/80 transition-colors text-left"
      >
        {open
          ? <ChevronDown className="w-3.5 h-3.5 flex-shrink-0 text-text-tertiary" />
          : <ChevronRight className="w-3.5 h-3.5 flex-shrink-0 text-text-tertiary" />
        }
        <span className={`font-mono text-xs font-bold px-1 rounded flex-shrink-0 ${CHANGE_COLORS[entry.changeType] ?? 'text-text-secondary'}`}>
          {CHANGE_LABEL[entry.changeType] ?? '?'}
        </span>
        <span className="font-mono text-xs truncate">{displayPath}</span>
      </button>

      {open && (
        <div className="overflow-x-auto bg-bg-primary text-xs font-mono">
          {lines.map((line, i) => (
            <div
              key={i}
              className={`px-4 py-px whitespace-pre leading-5 ${
                line.type === 'add'    ? 'bg-accent-green/10 text-accent-green' :
                line.type === 'remove' ? 'bg-accent-red/10 text-accent-red'    :
                line.type === 'hunk'   ? 'bg-accent-blue/5 text-accent-blue'   :
                'text-text-secondary'
              }`}
            >
              {line.content || ' '}
            </div>
          ))}
          {lines.length === 0 && (
            <p className="px-4 py-2 text-text-tertiary italic">No patch data</p>
          )}
        </div>
      )}
    </div>
  )
}

interface DiffViewerProps {
  repoId: string | null
  commit: GitCommit | null
}

export default function DiffViewer({ repoId, commit }: DiffViewerProps) {
  const [diff, setDiff]               = useState<GitDiff | null>(null)
  const [loading, setLoading]         = useState(false)
  const [selectedFile, setSelectedFile] = useState<string | null>(null)

  useEffect(() => {
    if (!repoId || !commit) { setDiff(null); return }
    setLoading(true)
    setDiff(null)
    setSelectedFile(null)
    getDiff(repoId, commit.hash)
      .then(d => setDiff(d))
      .catch(() => useToastStore.getState().addToast('error', 'Failed to load diff'))
      .finally(() => setLoading(false))
  }, [repoId, commit])

  if (!commit) {
    return (
      <div className="flex flex-col items-center justify-center h-full text-text-tertiary gap-2">
        <FileText className="w-8 h-8" />
        <p className="text-sm">Select a commit to view changes</p>
      </div>
    )
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full">
        <Loader2 className="w-5 h-5 animate-spin text-text-tertiary" />
      </div>
    )
  }

  if (!diff || diff.entries.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-full text-text-tertiary gap-2">
        <FileText className="w-8 h-8" />
        <p className="text-sm">No changes in this commit</p>
      </div>
    )
  }

  const visibleEntries = selectedFile
    ? diff.entries.filter(e => (e.newPath || e.oldPath) === selectedFile)
    : diff.entries

  return (
    <div className="flex h-full overflow-hidden">
      {/* File sidebar */}
      <div className="w-52 flex-shrink-0 border-r border-border overflow-y-auto">
        <div className="px-3 py-2 border-b border-border flex-shrink-0">
          <span className="text-xs font-semibold text-text-secondary uppercase tracking-wider">
            Files ({diff.entries.length})
          </span>
        </div>
        <div className="py-1">
          <button
            onClick={() => setSelectedFile(null)}
            className={`w-full flex items-center px-3 py-1.5 text-xs text-left transition-colors ${
              !selectedFile
                ? 'bg-accent-blue/10 text-accent-blue'
                : 'text-text-secondary hover:bg-bg-tertiary hover:text-text-primary'
            }`}
          >
            All files
          </button>
          {diff.entries.map(entry => {
            const path = entry.newPath || entry.oldPath
            return (
              <button
                key={path}
                onClick={() => setSelectedFile(path === selectedFile ? null : path)}
                className={`w-full flex items-center gap-1.5 px-3 py-1.5 text-xs text-left transition-colors ${
                  selectedFile === path
                    ? 'bg-accent-blue/10 text-accent-blue'
                    : 'text-text-secondary hover:bg-bg-tertiary hover:text-text-primary'
                }`}
              >
                <span className={`font-bold flex-shrink-0 ${CHANGE_COLORS[entry.changeType] ?? ''}`}>
                  {CHANGE_LABEL[entry.changeType] ?? '?'}
                </span>
                <span className="font-mono truncate">{path.split('/').pop()}</span>
              </button>
            )
          })}
        </div>
      </div>

      {/* Diff content */}
      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {/* Commit summary header */}
        <div className="flex items-center gap-3 pb-2 border-b border-border">
          <span className="font-mono text-xs text-accent-blue">{commit.shortHash}</span>
          <span className="text-sm text-text-primary truncate">{commit.message}</span>
          <div className="ml-auto flex items-center gap-3 text-xs text-text-tertiary flex-shrink-0">
            {commit.filesChanged > 0 && (
              <span>{commit.filesChanged} files</span>
            )}
            {commit.insertions > 0 && (
              <span className="flex items-center gap-0.5 text-accent-green">
                <Plus className="w-3 h-3" />{commit.insertions}
              </span>
            )}
            {commit.deletions > 0 && (
              <span className="flex items-center gap-0.5 text-accent-red">
                <Minus className="w-3 h-3" />{commit.deletions}
              </span>
            )}
          </div>
        </div>

        {visibleEntries.map(entry => (
          <FileEntry key={entry.newPath || entry.oldPath} entry={entry} />
        ))}
      </div>
    </div>
  )
}
