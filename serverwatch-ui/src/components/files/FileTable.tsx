import type { ReactNode } from 'react'
import {
  Folder, File, FileText, FileCode2, Image, Archive,
  Link2, ChevronUp, ChevronDown, ChevronsUpDown, Lock,
} from 'lucide-react'
import type { FileEntry } from '../../types'
import { formatBytes, formatRelative } from '../../lib/formatters'
import { cn } from '../../lib/utils'

export type SortBy = 'name' | 'size' | 'modified' | 'type'

interface FileTableProps {
  entries: FileEntry[]
  parentPath: string | null
  selectedPaths: Set<string>
  sortBy: SortBy
  sortDir: 'asc' | 'desc'
  highlightName?: string
  isReadOnly: boolean
  onNavigateUp: () => void
  onSortChange: (col: SortBy) => void
  onClick: (entry: FileEntry, e: React.MouseEvent) => void
  onDoubleClick: (entry: FileEntry) => void
  onContextMenu: (entry: FileEntry | null, e: React.MouseEvent) => void
}

// ── file icon helper ──────────────────────────────────────────────────────────
const CODE_EXTS  = new Set(['js','jsx','ts','tsx','py','java','go','rs','c','cpp','h','cs','rb','php','swift','kt','r'])
const IMAGE_EXTS = new Set(['png','jpg','jpeg','gif','svg','webp','ico','bmp','tiff'])
const ARCH_EXTS  = new Set(['zip','tar','gz','bz2','xz','7z','rar','tgz'])
const TEXT_EXTS  = new Set(['md','txt','csv','log','ini','conf','env','toml'])

function getIcon(entry: FileEntry): ReactNode {
  if (entry.type === 'SYMLINK')   return <Link2        className="w-4 h-4 text-accent-cyan"   />
  if (entry.type === 'DIRECTORY') return <Folder       className="w-4 h-4 text-accent-amber"  />
  const ext = entry.name.split('.').pop()?.toLowerCase() ?? ''
  if (CODE_EXTS.has(ext))  return <FileCode2 className="w-4 h-4 text-accent-blue"   />
  if (IMAGE_EXTS.has(ext)) return <Image     className="w-4 h-4 text-accent-purple" />
  if (ARCH_EXTS.has(ext))  return <Archive   className="w-4 h-4 text-accent-amber"  />
  if (TEXT_EXTS.has(ext))  return <FileText  className="w-4 h-4 text-text-secondary"/>
  return <File className="w-4 h-4 text-text-tertiary" />
}

// ── sort header ───────────────────────────────────────────────────────────────
function SortHeader({
  col, label, current, dir, onChange,
}: {
  col: SortBy; label: string; current: SortBy; dir: 'asc' | 'desc'
  onChange: (c: SortBy) => void
}) {
  const active = col === current
  return (
    <button
      onClick={() => onChange(col)}
      className="flex items-center gap-1 text-xs font-medium text-text-secondary hover:text-text-primary transition-colors"
    >
      {label}
      {active
        ? (dir === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)
        : <ChevronsUpDown className="w-3 h-3 opacity-40" />
      }
    </button>
  )
}

// ── main component ────────────────────────────────────────────────────────────
export default function FileTable({
  entries, parentPath, selectedPaths, sortBy, sortDir,
  highlightName, isReadOnly,
  onNavigateUp, onSortChange,
  onClick, onDoubleClick, onContextMenu,
}: FileTableProps) {

  return (
    <div
      className="flex-1 overflow-auto"
      onContextMenu={e => {
        // right-click on blank area (not on a row)
        if ((e.target as HTMLElement).closest('[data-entry-row]')) return
        e.preventDefault()
        onContextMenu(null, e)
      }}
    >
      <table className="w-full text-sm border-collapse">
        <thead className="sticky top-0 z-10 bg-bg-secondary border-b border-border">
          <tr>
            <th className="w-8 px-3 py-2" />
            <th className="px-3 py-2 text-left">
              <SortHeader col="name" label="Name" current={sortBy} dir={sortDir} onChange={onSortChange} />
            </th>
            <th className="px-3 py-2 text-right hidden sm:table-cell">
              <SortHeader col="size" label="Size" current={sortBy} dir={sortDir} onChange={onSortChange} />
            </th>
            <th className="px-3 py-2 text-left hidden md:table-cell">
              <SortHeader col="modified" label="Modified" current={sortBy} dir={sortDir} onChange={onSortChange} />
            </th>
            <th className="px-3 py-2 text-left hidden lg:table-cell">
              <span className="text-xs font-medium text-text-secondary">Permissions</span>
            </th>
            {isReadOnly && (
              <th className="w-8 px-2 py-2" />
            )}
          </tr>
        </thead>

        <tbody>
          {/* Parent directory (..) */}
          {parentPath !== null && (
            <tr
              className="hover:bg-bg-tertiary cursor-pointer border-b border-border/30"
              onDoubleClick={onNavigateUp}
            >
              <td className="px-3 py-2">
                <Folder className="w-4 h-4 text-text-tertiary" />
              </td>
              <td className="px-3 py-2 text-text-secondary font-mono">..</td>
              <td className="px-3 py-2 hidden sm:table-cell" />
              <td className="px-3 py-2 hidden md:table-cell" />
              <td className="px-3 py-2 hidden lg:table-cell" />
              {isReadOnly && <td />}
            </tr>
          )}

          {entries.map(entry => {
            const selected   = selectedPaths.has(entry.path)
            const highlighted = highlightName === entry.name
            return (
              <tr
                key={entry.path}
                data-entry-row
                className={cn(
                  'border-b border-border/30 cursor-pointer transition-colors select-none',
                  selected
                    ? 'bg-accent-blue/10 border-l-2 border-l-accent-blue'
                    : highlighted
                      ? 'bg-accent-amber/10'
                      : 'hover:bg-bg-tertiary',
                  entry.isHidden && 'opacity-60',
                )}
                onClick={e => onClick(entry, e)}
                onDoubleClick={() => onDoubleClick(entry)}
                onContextMenu={e => { e.preventDefault(); e.stopPropagation(); onContextMenu(entry, e) }}
              >
                <td className="px-3 py-2">{getIcon(entry)}</td>
                <td className="px-3 py-2 max-w-0">
                  <span className="truncate block text-text-primary">{entry.name}</span>
                  {entry.symlinkTarget && (
                    <span className="text-xs text-text-tertiary font-mono truncate block">
                      → {entry.symlinkTarget}
                    </span>
                  )}
                </td>
                <td className="px-3 py-2 text-right text-text-secondary font-mono hidden sm:table-cell">
                  {entry.type === 'FILE' ? formatBytes(entry.size) : '—'}
                </td>
                <td className="px-3 py-2 text-text-secondary hidden md:table-cell">
                  {formatRelative(entry.modifiedAt)}
                </td>
                <td className="px-3 py-2 font-mono text-xs text-text-tertiary hidden lg:table-cell">
                  {entry.permissions}
                </td>
                {isReadOnly && (
                  <td className="px-2 py-2">
                    {!entry.isWritable && <Lock className="w-3 h-3 text-text-tertiary" />}
                  </td>
                )}
              </tr>
            )
          })}

          {entries.length === 0 && parentPath === null && (
            <tr>
              <td colSpan={5} className="px-4 py-12 text-center text-text-tertiary text-sm">
                Empty directory
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}
