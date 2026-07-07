import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  RefreshCw, Upload, FilePlus, FolderPlus, Search,
  Eye, EyeOff, Loader2, Lock,
} from 'lucide-react'
import {
  listDirectory, deleteEntry, moveEntry, downloadFile,
  createEntry, getRoots,
} from '../../api/files'
import { useToastStore } from '../../stores/toastStore'
import type { DirectoryListing, FileEntry } from '../../types'
import { formatBytes } from '../../lib/formatters'
import { cn } from '../../lib/utils'

import Breadcrumb from './Breadcrumb'
import FileTable, { type SortBy } from './FileTable'
import FileContextMenu from './FileContextMenu'
import FileEditor from './FileEditor'
import UploadModal from './UploadModal'
import CreateDialog from './CreateDialog'
import RenameDialog from './RenameDialog'
import PropertiesPanel from './PropertiesPanel'
import SearchModal from './SearchModal'
import ConfirmDialog from '../ui/ConfirmDialog'

interface CtxMenu {
  x: number
  y: number
  entry: FileEntry | null
}

const DEFAULT_PATH = '/hostfs/opt'

export default function FileBrowser() {
  const [currentPath, setCurrentPath] = useState(DEFAULT_PATH)
  const [roots, setRoots]             = useState<string[]>([])
  const [listing, setListing]         = useState<DirectoryListing | null>(null)
  const [loading, setLoading]         = useState(true)
  const [showHidden, setShowHidden]   = useState(false)
  const [sortBy, setSortBy]           = useState<SortBy>('name')
  const [sortDir, setSortDir]         = useState<'asc' | 'desc'>('asc')

  // Selection
  const [selectedPaths, setSelectedPaths]       = useState<Set<string>>(new Set())
  const [lastClickedIndex, setLastClickedIndex] = useState(-1)
  const [highlightName, setHighlightName]       = useState<string | undefined>()

  // Context menu
  const [ctxMenu, setCtxMenu] = useState<CtxMenu | null>(null)

  // Dialogs
  const [editorPath, setEditorPath]         = useState<string | null>(null)
  const [showUpload, setShowUpload]         = useState(false)
  const [createType, setCreateType]         = useState<'FILE' | 'DIRECTORY' | null>(null)
  const [renameEntry, setRenameEntry]       = useState<FileEntry | null>(null)
  const [deleteTargets, setDeleteTargets]   = useState<FileEntry[]>([])
  const [propertiesEntry, setPropertiesEntry] = useState<FileEntry | null>(null)
  const [showSearch, setShowSearch]         = useState(false)

  const containerRef = useRef<HTMLDivElement>(null)

  // ── load directory ──────────────────────────────────────────────────────────
  const loadDir = useCallback(async (path: string, silent = false) => {
    if (!silent) setLoading(true)
    try {
      const data = await listDirectory(path, showHidden)
      setListing(data)
      setCurrentPath(data.path)
      setSelectedPaths(new Set())
      setLastClickedIndex(-1)
    } catch {
      useToastStore.getState().addToast('error', `Cannot open: ${path}`)
    } finally {
      setLoading(false)
    }
  }, [showHidden])

  // Initial load: fetch available roots then open the default path
  useEffect(() => {
    getRoots()
      .then(fetched => {
        setRoots(fetched)
        void loadDir(fetched[0] ?? DEFAULT_PATH)
      })
      .catch(() => void loadDir(DEFAULT_PATH))
  }, [loadDir])

  // Reload when showHidden changes
  useEffect(() => {
    void loadDir(currentPath, true)
  }, [showHidden]) // eslint-disable-line react-hooks/exhaustive-deps

  // ── sorted entries ──────────────────────────────────────────────────────────
  const sortedEntries = useMemo(() => {
    if (!listing) return []
    return [...listing.entries].sort((a, b) => {
      // Dirs first
      if (a.type === 'DIRECTORY' && b.type !== 'DIRECTORY') return -1
      if (b.type === 'DIRECTORY' && a.type !== 'DIRECTORY') return 1
      let cmp = 0
      if (sortBy === 'name')     cmp = a.name.localeCompare(b.name)
      else if (sortBy === 'size')     cmp = a.size - b.size
      else if (sortBy === 'modified') cmp = new Date(a.modifiedAt).getTime() - new Date(b.modifiedAt).getTime()
      else if (sortBy === 'type')     cmp = a.mimeType.localeCompare(b.mimeType)
      return sortDir === 'asc' ? cmp : -cmp
    })
  }, [listing, sortBy, sortDir])

  // ── navigation ──────────────────────────────────────────────────────────────
  const navigate = useCallback((path: string) => {
    setHighlightName(undefined)
    void loadDir(path)
  }, [loadDir])

  const navigateUp = useCallback(() => {
    if (listing?.parentPath != null) navigate(listing.parentPath)
  }, [listing, navigate])

  // ── selection ────────────────────────────────────────────────────────────────
  const handleClick = useCallback((entry: FileEntry, e: React.MouseEvent) => {
    const idx = sortedEntries.indexOf(entry)
    if (e.ctrlKey || e.metaKey) {
      setSelectedPaths(prev => {
        const next = new Set(prev)
        if (next.has(entry.path)) next.delete(entry.path)
        else next.add(entry.path)
        return next
      })
    } else if (e.shiftKey && lastClickedIndex >= 0) {
      const lo = Math.min(lastClickedIndex, idx)
      const hi = Math.max(lastClickedIndex, idx)
      setSelectedPaths(new Set(sortedEntries.slice(lo, hi + 1).map(e => e.path)))
    } else {
      setSelectedPaths(new Set([entry.path]))
    }
    setLastClickedIndex(idx)
  }, [sortedEntries, lastClickedIndex])

  // ── open / double-click ──────────────────────────────────────────────────────
  const handleOpen = useCallback((entry: FileEntry) => {
    if (entry.type === 'DIRECTORY') {
      navigate(entry.path)
    } else if (entry.isEditable) {
      setEditorPath(entry.path)
    } else {
      // Binary or non-editable → download
      void downloadFile(entry.path).then(blob => {
        const url = URL.createObjectURL(blob)
        const a   = document.createElement('a')
        a.href    = url
        a.download = entry.name
        a.click()
        URL.revokeObjectURL(url)
      }).catch(() => useToastStore.getState().addToast('error', 'Download failed'))
    }
  }, [navigate])

  // ── download ──────────────────────────────────────────────────────────────────
  const handleDownload = useCallback((entry: FileEntry) => {
    void downloadFile(entry.path).then(blob => {
      const url = URL.createObjectURL(blob)
      const a   = document.createElement('a')
      a.href    = url
      a.download = entry.name
      a.click()
      URL.revokeObjectURL(url)
    }).catch(() => useToastStore.getState().addToast('error', 'Download failed'))
  }, [])

  // ── sort ─────────────────────────────────────────────────────────────────────
  const handleSortChange = useCallback((col: SortBy) => {
    if (col === sortBy) setSortDir(d => d === 'asc' ? 'desc' : 'asc')
    else { setSortBy(col); setSortDir('asc') }
  }, [sortBy])

  // ── context menu ─────────────────────────────────────────────────────────────
  const handleContextMenu = useCallback((entry: FileEntry | null, e: React.MouseEvent) => {
    e.preventDefault()
    setCtxMenu({ x: e.clientX, y: e.clientY, entry })
    if (entry) setSelectedPaths(new Set([entry.path]))
  }, [])

  // ── create ────────────────────────────────────────────────────────────────────
  const handleCreate = useCallback(async (name: string, type: 'FILE' | 'DIRECTORY', content: string) => {
    try {
      await createEntry(currentPath, name, type, content)
      useToastStore.getState().addToast('success', `Created ${name}`)
      await loadDir(currentPath, true)
      setHighlightName(name)
    } catch {
      useToastStore.getState().addToast('error', `Failed to create ${name}`)
    }
  }, [currentPath, loadDir])

  // ── rename ────────────────────────────────────────────────────────────────────
  const handleRename = useCallback(async (entry: FileEntry, newName: string) => {
    const dir     = entry.path.substring(0, entry.path.lastIndexOf('/')) || '/'
    const newPath = `${dir}/${newName}`
    try {
      await moveEntry(entry.path, newPath)
      useToastStore.getState().addToast('success', `Renamed to ${newName}`)
      await loadDir(currentPath, true)
      setHighlightName(newName)
    } catch {
      useToastStore.getState().addToast('error', 'Rename failed')
    }
  }, [currentPath, loadDir])

  // ── delete ────────────────────────────────────────────────────────────────────
  const handleDelete = useCallback(async () => {
    const targets = deleteTargets
    setDeleteTargets([])
    try {
      await Promise.all(targets.map(e => deleteEntry(e.path, e.type === 'DIRECTORY')))
      useToastStore.getState().addToast('success', `Deleted ${targets.length} item${targets.length > 1 ? 's' : ''}`)
      await loadDir(currentPath, true)
    } catch {
      useToastStore.getState().addToast('error', 'Delete failed')
    }
  }, [deleteTargets, currentPath, loadDir])

  // ── keyboard shortcuts ────────────────────────────────────────────────────────
  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (selectedPaths.size === 0) return
    const selected = sortedEntries.filter(ent => selectedPaths.has(ent.path))
    if (e.key === 'Delete') {
      e.preventDefault()
      setDeleteTargets(selected)
    } else if (e.key === 'F2' && selected.length === 1) {
      e.preventDefault()
      setRenameEntry(selected[0])
    } else if (e.key === 'Enter' && selected.length === 1) {
      e.preventDefault()
      handleOpen(selected[0])
    } else if ((e.ctrlKey || e.metaKey) && e.key === 'c' && selected.length === 1) {
      e.preventDefault()
      void navigator.clipboard.writeText(selected[0].path)
      useToastStore.getState().addToast('info', 'Path copied')
    }
  }, [selectedPaths, sortedEntries, handleOpen])

  // ── drag & drop → upload ──────────────────────────────────────────────────────
  const [isDragOver, setIsDragOver] = useState(false)
  const handleDragOver = useCallback((e: React.DragEvent) => {
    if (e.dataTransfer.types.includes('Files')) { e.preventDefault(); setIsDragOver(true) }
  }, [])
  const handleDragLeave = useCallback(() => setIsDragOver(false), [])
  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setIsDragOver(false)
    if (e.dataTransfer.files.length > 0) setShowUpload(true)
  }, [])

  // ── derived ───────────────────────────────────────────────────────────────────
  const selectedEntries = useMemo(
    () => sortedEntries.filter(e => selectedPaths.has(e.path)),
    [sortedEntries, selectedPaths],
  )
  const ctxEntry  = ctxMenu?.entry ?? (selectedEntries.length === 1 ? selectedEntries[0] : null)
  const isRO      = listing?.isReadOnly ?? false
  const homePath  = roots[0] ?? DEFAULT_PATH
  // Highlight the root that is a prefix of the current path
  const currentRoot = roots.find(r => currentPath.startsWith(r)) ?? homePath

  return (
    <div
      ref={containerRef}
      className={cn(
        'flex flex-col h-full bg-bg-secondary border border-border rounded-xl overflow-hidden outline-none',
        isDragOver && 'ring-2 ring-accent-blue',
      )}
      tabIndex={0}
      onKeyDown={handleKeyDown}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      {/* Toolbar */}
      <div className="flex items-center gap-2 px-4 py-3 border-b border-border flex-wrap">
        {/* Root selector — jumps to a different allowed root */}
        {roots.length > 0 && (
          <select
            value={currentRoot}
            onChange={e => navigate(e.target.value)}
            className="text-xs bg-bg-primary border border-border rounded px-2 py-1.5 text-text-secondary focus:outline-none focus:border-accent-blue flex-shrink-0 max-w-40"
          >
            {roots.map(r => (
              <option key={r} value={r}>{r}</option>
            ))}
          </select>
        )}
        <Breadcrumb
          breadcrumbs={listing?.breadcrumbs ?? []}
          homePath={homePath}
          onNavigate={navigate}
        />
        <div className="flex items-center gap-1.5 ml-auto flex-shrink-0">
          {isRO && <span title="Read-only"><Lock className="w-3.5 h-3.5 text-text-tertiary" /></span>}
          {!isRO && (
            <>
              <ToolBtn icon={Upload}    label="Upload"     onClick={() => setShowUpload(true)} />
              <ToolBtn icon={FilePlus}  label="New File"   onClick={() => setCreateType('FILE')} />
              <ToolBtn icon={FolderPlus} label="New Folder" onClick={() => setCreateType('DIRECTORY')} />
            </>
          )}
          <ToolBtn icon={Search}    label="Search"     onClick={() => setShowSearch(true)} />
          <ToolBtn icon={showHidden ? EyeOff : Eye} label={showHidden ? 'Hide hidden' : 'Show hidden'} onClick={() => setShowHidden(v => !v)} active={showHidden} />
          <ToolBtn icon={RefreshCw} label="Refresh"    onClick={() => void loadDir(currentPath, true)} />
        </div>
      </div>

      {/* Content area */}
      {loading ? (
        <div className="flex-1 flex items-center justify-center gap-2 text-text-tertiary">
          <Loader2 className="w-5 h-5 animate-spin" />
          <span className="text-sm">Loading…</span>
        </div>
      ) : (
        <FileTable
          entries={sortedEntries}
          parentPath={listing?.parentPath ?? null}
          selectedPaths={selectedPaths}
          sortBy={sortBy}
          sortDir={sortDir}
          highlightName={highlightName}
          isReadOnly={isRO}
          onNavigateUp={navigateUp}
          onSortChange={handleSortChange}
          onClick={handleClick}
          onDoubleClick={handleOpen}
          onContextMenu={handleContextMenu}
        />
      )}

      {/* Status bar */}
      <div className="flex items-center gap-4 px-4 py-2 border-t border-border text-xs text-text-tertiary flex-shrink-0">
        <span>{listing?.directoryCount ?? 0} folder{listing?.directoryCount !== 1 ? 's' : ''}</span>
        <span>{listing?.fileCount ?? 0} file{listing?.fileCount !== 1 ? 's' : ''}</span>
        {(listing?.totalSize ?? 0) > 0 && <span>{formatBytes(listing!.totalSize)} total</span>}
        {selectedPaths.size > 0 && (
          <span className="text-accent-blue ml-auto">{selectedPaths.size} selected</span>
        )}
        <span className={cn('font-mono truncate max-w-64', selectedPaths.size > 0 ? '' : 'ml-auto')}>
          {currentPath}
        </span>
      </div>

      {/* ── modals & dialogs ── */}

      {ctxMenu && (
        <FileContextMenu
          x={ctxMenu.x}
          y={ctxMenu.y}
          entry={ctxMenu.entry}
          isReadOnly={isRO}
          onClose={() => setCtxMenu(null)}
          onOpen={() => ctxEntry && handleOpen(ctxEntry)}
          onEdit={() => ctxEntry && ctxEntry.isEditable && setEditorPath(ctxEntry.path)}
          onDownload={() => ctxEntry && handleDownload(ctxEntry)}
          onRename={() => ctxEntry && setRenameEntry(ctxEntry)}
          onDelete={() => ctxEntry && setDeleteTargets([ctxEntry])}
          onCopyPath={() => ctxEntry && void navigator.clipboard.writeText(ctxEntry.path)}
          onProperties={() => ctxEntry && setPropertiesEntry(ctxEntry)}
          onNewFile={() => setCreateType('FILE')}
          onNewFolder={() => setCreateType('DIRECTORY')}
          onUpload={() => setShowUpload(true)}
          onRefresh={() => void loadDir(currentPath, true)}
        />
      )}

      <FileEditor
        path={editorPath}
        onClose={() => { setEditorPath(null); void loadDir(currentPath, true) }}
      />

      <UploadModal
        open={showUpload}
        targetDir={currentPath}
        onOpenChange={setShowUpload}
        onDone={() => void loadDir(currentPath, true)}
      />

      <CreateDialog
        open={createType !== null}
        defaultType={createType ?? 'FILE'}
        onOpenChange={v => { if (!v) setCreateType(null) }}
        onConfirm={(name, type, content) => void handleCreate(name, type, content)}
      />

      {renameEntry && (
        <RenameDialog
          open
          currentName={renameEntry.name}
          onOpenChange={v => { if (!v) setRenameEntry(null) }}
          onConfirm={newName => void handleRename(renameEntry, newName)}
        />
      )}

      <ConfirmDialog
        open={deleteTargets.length > 0}
        onOpenChange={v => { if (!v) setDeleteTargets([]) }}
        title={`Delete ${deleteTargets.length === 1 ? `"${deleteTargets[0].name}"` : `${deleteTargets.length} items`}?`}
        description="This action cannot be undone."
        confirmLabel="Delete"
        confirmVariant="danger"
        onConfirm={() => void handleDelete()}
      />

      <PropertiesPanel
        entry={propertiesEntry}
        onClose={() => setPropertiesEntry(null)}
      />

      <SearchModal
        open={showSearch}
        rootPath={currentPath}
        onOpenChange={setShowSearch}
        onNavigate={(path, name) => { navigate(path); setHighlightName(name) }}
      />
    </div>
  )
}

// ── small toolbar button ──────────────────────────────────────────────────────
function ToolBtn({
  icon: Icon, label, onClick, active,
}: {
  icon: typeof RefreshCw
  label: string
  onClick: () => void
  active?: boolean
}) {
  return (
    <button
      onClick={onClick}
      title={label}
      className={cn(
        'flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs transition-colors',
        active
          ? 'bg-accent-blue/20 text-accent-blue'
          : 'text-text-secondary hover:text-text-primary hover:bg-bg-tertiary',
      )}
    >
      <Icon className="w-3.5 h-3.5" />
      <span className="hidden sm:inline">{label}</span>
    </button>
  )
}
