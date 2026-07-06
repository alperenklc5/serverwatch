import { useEffect, useRef } from 'react'
import {
  FolderOpen, Download, Copy, Trash2,
  Info, FilePlus, FolderPlus, Upload, RefreshCw, FileText, CornerUpRight,
} from 'lucide-react'
import type { FileEntry } from '../../types'
import { cn } from '../../lib/utils'

interface FileContextMenuProps {
  x: number
  y: number
  entry: FileEntry | null   // null = background right-click
  isReadOnly: boolean
  onClose: () => void
  onOpen: () => void
  onEdit: () => void
  onDownload: () => void
  onRename: () => void
  onDelete: () => void
  onCopyPath: () => void
  onProperties: () => void
  onNewFile: () => void
  onNewFolder: () => void
  onUpload: () => void
  onRefresh: () => void
}

interface ItemProps {
  icon: typeof FilePlus
  label: string
  onClick: () => void
  danger?: boolean
  disabled?: boolean
}

function Item({ icon: Icon, label, onClick, danger, disabled }: ItemProps) {
  return (
    <button
      onClick={disabled ? undefined : onClick}
      className={cn(
        'flex items-center gap-2.5 w-full px-3 py-1.5 text-sm transition-colors text-left',
        disabled
          ? 'text-text-tertiary cursor-not-allowed'
          : danger
            ? 'text-accent-red hover:bg-accent-red/10'
            : 'text-text-secondary hover:bg-bg-tertiary hover:text-text-primary',
      )}
    >
      <Icon className="w-3.5 h-3.5 flex-shrink-0" />
      {label}
    </button>
  )
}

function Sep() {
  return <div className="my-1 h-px bg-border mx-2" />
}

export default function FileContextMenu({
  x, y, entry, isReadOnly, onClose,
  onOpen, onEdit, onDownload, onRename, onDelete,
  onCopyPath, onProperties, onNewFile, onNewFolder, onUpload, onRefresh,
}: FileContextMenuProps) {
  const ref = useRef<HTMLDivElement>(null)

  // Clamp to viewport after first paint
  const safeX = Math.min(x, window.innerWidth  - 200)
  const safeY = Math.min(y, window.innerHeight - 300)

  useEffect(() => {
    function handler(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose()
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [onClose])

  const isFile = entry?.type === 'FILE' || entry?.type === 'SYMLINK'
  const ro     = isReadOnly

  const wrap = (fn: () => void) => () => { fn(); onClose() }

  return (
    <div
      ref={ref}
      className="fixed z-[9990] min-w-48 bg-bg-secondary border border-border rounded-lg shadow-2xl py-1 focus:outline-none"
      style={{ left: safeX, top: safeY }}
      onContextMenu={e => e.preventDefault()}
    >
      {entry ? (
        <>
          {/* File or directory selected */}
          <Item icon={FolderOpen} label="Open" onClick={wrap(onOpen)} />
          {isFile && entry.isEditable && (
            <Item icon={FileText} label="Edit" onClick={wrap(onEdit)} />
          )}
          <Item icon={Download} label="Download" onClick={wrap(onDownload)} />
          <Sep />
          <Item icon={Copy}  label="Copy path" onClick={wrap(onCopyPath)} />
          {!ro && (
            <>
              <Item icon={CornerUpRight} label="Rename" onClick={wrap(onRename)} disabled={ro} />
              <Sep />
              <Item icon={Trash2} label="Delete" onClick={wrap(onDelete)} danger disabled={ro} />
            </>
          )}
          <Sep />
          <Item icon={Info} label="Properties" onClick={wrap(onProperties)} />
        </>
      ) : (
        <>
          {/* Background right-click */}
          {!ro && (
            <>
              <Item icon={FilePlus}   label="New File"   onClick={wrap(onNewFile)} />
              <Item icon={FolderPlus} label="New Folder" onClick={wrap(onNewFolder)} />
              <Sep />
              <Item icon={Upload} label="Upload Files" onClick={wrap(onUpload)} />
              <Sep />
            </>
          )}
          <Item icon={RefreshCw} label="Refresh" onClick={wrap(onRefresh)} />
        </>
      )}
    </div>
  )
}

