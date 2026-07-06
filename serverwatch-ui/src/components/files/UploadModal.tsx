import { useCallback, useRef, useState } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import { Upload, X, CheckCircle, XCircle, Loader2, FolderOpen } from 'lucide-react'
import { uploadFile } from '../../api/files'
import { formatBytes } from '../../lib/formatters'
import { cn } from '../../lib/utils'

interface UploadModalProps {
  open: boolean
  targetDir: string
  onOpenChange: (open: boolean) => void
  onDone: () => void
}

interface UploadItem {
  id: string
  file: File
  progress: number
  status: 'pending' | 'uploading' | 'done' | 'error'
  error: string
  abort: AbortController
}

let _id = 0
function nextId() { return String(++_id) }

export default function UploadModal({ open, targetDir, onOpenChange, onDone }: UploadModalProps) {
  const [items, setItems] = useState<UploadItem[]>([])
  const [isDragOver, setIsDragOver] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  function updateItem(id: string, patch: Partial<UploadItem>) {
    setItems(prev => prev.map(it => it.id === id ? { ...it, ...patch } : it))
  }

  async function startUpload(item: UploadItem) {
    updateItem(item.id, { status: 'uploading' })
    try {
      await uploadFile(
        targetDir,
        item.file,
        pct => updateItem(item.id, { progress: pct }),
        item.abort.signal,
      )
      updateItem(item.id, { status: 'done', progress: 100 })
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Upload failed'
      if (msg !== 'Cancelled') {
        updateItem(item.id, { status: 'error', error: msg })
      }
    }
  }

  function addFiles(files: FileList | File[]) {
    const newItems: UploadItem[] = Array.from(files).map(file => ({
      id: nextId(),
      file,
      progress: 0,
      status: 'pending',
      error: '',
      abort: new AbortController(),
    }))
    setItems(prev => [...prev, ...newItems])
    newItems.forEach(item => void startUpload(item))
  }

  const handleDrop = useCallback((e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setIsDragOver(false)
    if (e.dataTransfer.files.length > 0) addFiles(e.dataTransfer.files)
  }, [])  // eslint-disable-line react-hooks/exhaustive-deps

  function handleClose() {
    // Cancel any in-flight uploads
    items.forEach(it => { if (it.status === 'uploading') it.abort.abort() })
    const hasDone = items.some(it => it.status === 'done')
    setItems([])
    onOpenChange(false)
    if (hasDone) onDone()
  }

  const allDone   = items.length > 0 && items.every(it => it.status === 'done' || it.status === 'error')
  const inFlight  = items.filter(it => it.status === 'uploading').length

  return (
    <Dialog.Root open={open} onOpenChange={v => { if (!v) handleClose() }}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-black/60 z-50 backdrop-blur-sm" />
        <Dialog.Content className="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-50 bg-bg-secondary border border-border rounded-xl shadow-2xl w-full max-w-md p-6 focus:outline-none">
          <div className="flex items-center justify-between mb-4">
            <Dialog.Title className="text-base font-semibold text-text-primary">Upload Files</Dialog.Title>
            <button
              onClick={handleClose}
              className="p-1.5 rounded text-text-tertiary hover:text-text-primary hover:bg-bg-tertiary transition-colors"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
          <Dialog.Description className="text-xs text-text-secondary mb-4">
            Target: <span className="font-mono text-text-primary">{targetDir}</span>
          </Dialog.Description>

          {/* Drop zone */}
          <div
            onDragOver={e => { e.preventDefault(); setIsDragOver(true) }}
            onDragLeave={() => setIsDragOver(false)}
            onDrop={handleDrop}
            onClick={() => inputRef.current?.click()}
            className={cn(
              'border-2 border-dashed rounded-lg py-8 flex flex-col items-center justify-center gap-3 cursor-pointer transition-colors',
              isDragOver
                ? 'border-accent-blue bg-accent-blue/5'
                : 'border-border hover:border-border-active hover:bg-bg-tertiary',
            )}
          >
            <Upload className={cn('w-8 h-8', isDragOver ? 'text-accent-blue' : 'text-text-tertiary')} />
            <div className="text-center">
              <p className="text-sm text-text-secondary">Drag files here or click to browse</p>
              <p className="text-xs text-text-tertiary mt-1">Multiple files supported</p>
            </div>
          </div>

          <input
            ref={inputRef}
            type="file"
            multiple
            className="hidden"
            onChange={e => { if (e.target.files) { addFiles(e.target.files); e.target.value = '' } }}
          />

          {/* File list */}
          {items.length > 0 && (
            <div className="mt-4 space-y-2 max-h-52 overflow-y-auto">
              {items.map(item => (
                <div key={item.id} className="bg-bg-primary rounded-lg p-3">
                  <div className="flex items-center gap-2 mb-1.5">
                    <FolderOpen className="w-3.5 h-3.5 text-text-tertiary flex-shrink-0" />
                    <span className="text-xs text-text-primary truncate flex-1">{item.file.name}</span>
                    <span className="text-xs text-text-tertiary flex-shrink-0">{formatBytes(item.file.size)}</span>
                    {item.status === 'done'     && <CheckCircle className="w-3.5 h-3.5 text-accent-green flex-shrink-0" />}
                    {item.status === 'error'    && <XCircle     className="w-3.5 h-3.5 text-accent-red flex-shrink-0" />}
                    {item.status === 'uploading'&& <Loader2     className="w-3.5 h-3.5 text-accent-blue animate-spin flex-shrink-0" />}
                    {item.status === 'uploading'&& (
                      <button
                        onClick={() => item.abort.abort()}
                        className="p-0.5 text-text-tertiary hover:text-accent-red transition-colors"
                      >
                        <X className="w-3 h-3" />
                      </button>
                    )}
                  </div>
                  {item.status === 'uploading' && (
                    <div className="h-1 bg-bg-secondary rounded-full overflow-hidden">
                      <div
                        className="h-full bg-accent-blue rounded-full transition-all duration-300"
                        style={{ width: `${item.progress}%` }}
                      />
                    </div>
                  )}
                  {item.status === 'error' && (
                    <p className="text-xs text-accent-red mt-1">{item.error}</p>
                  )}
                </div>
              ))}
            </div>
          )}

          <div className="flex items-center justify-between mt-5">
            <span className="text-xs text-text-tertiary">
              {inFlight > 0 ? `Uploading ${inFlight} file${inFlight > 1 ? 's' : ''}…` : ''}
            </span>
            <button
              onClick={handleClose}
              className={cn(
                'px-4 py-2 text-sm font-medium rounded-lg transition-colors',
                allDone
                  ? 'bg-accent-green text-white hover:bg-accent-green/80'
                  : 'text-text-secondary border border-border hover:bg-bg-tertiary',
              )}
            >
              {allDone ? 'Done' : 'Close'}
            </button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
