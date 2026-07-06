import { useCallback, useEffect, useRef, useState } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import Editor, { type OnMount } from '@monaco-editor/react'
import { X, Save, Loader2, AlertCircle } from 'lucide-react'
import { readFile, writeFile } from '../../api/files'
import type { FileContent } from '../../types'
import { formatBytes } from '../../lib/formatters'
import { useToastStore } from '../../stores/toastStore'
import { cn } from '../../lib/utils'

interface FileEditorProps {
  path: string | null
  onClose: () => void
}

// Extension → Monaco language ID
const LANG_MAP: Record<string, string> = {
  js: 'javascript', jsx: 'javascript',
  ts: 'typescript', tsx: 'typescript',
  py: 'python',
  json: 'json',
  yml: 'yaml', yaml: 'yaml',
  sh: 'shell', bash: 'shell',
  sql: 'sql',
  md: 'markdown',
  html: 'html', htm: 'html',
  css: 'css', scss: 'scss', less: 'less',
  xml: 'xml',
  java: 'java',
  go: 'go',
  rs: 'rust',
  c: 'c', cpp: 'cpp', h: 'cpp',
  cs: 'csharp',
  rb: 'ruby',
  php: 'php',
  kt: 'kotlin',
  swift: 'swift',
  toml: 'ini',
  ini: 'ini', conf: 'ini', env: 'ini',
}

function detectLanguage(path: string): string {
  const ext = path.split('.').pop()?.toLowerCase() ?? ''
  return LANG_MAP[ext] ?? 'plaintext'
}

function fileName(path: string) {
  return path.split('/').pop() ?? path
}

export default function FileEditor({ path, onClose }: FileEditorProps) {
  const [fileData, setFileData]   = useState<FileContent | null>(null)
  const [content, setContent]     = useState('')
  const [loading, setLoading]     = useState(false)
  const [saving, setSaving]       = useState(false)
  const [dirty, setDirty]         = useState(false)
  const [closeConfirm, setCloseConfirm] = useState(false)

  // Use a ref so the Ctrl+S keybinding closure always calls the latest save
  const saveRef = useRef<() => Promise<void>>(async () => {})

  const save = useCallback(async () => {
    if (!path) return
    setSaving(true)
    try {
      await writeFile(path, content)
      setDirty(false)
      useToastStore.getState().addToast('success', `Saved ${fileName(path)}`)
    } catch {
      useToastStore.getState().addToast('error', 'Failed to save file')
    } finally {
      setSaving(false)
    }
  }, [path, content])

  // Keep saveRef up to date
  useEffect(() => { saveRef.current = save }, [save])

  // Load file when path changes
  useEffect(() => {
    if (!path) return
    setLoading(true)
    setDirty(false)
    setCloseConfirm(false)
    readFile(path)
      .then(fc => {
        setFileData(fc)
        setContent(fc.content)
      })
      .catch(() => {
        useToastStore.getState().addToast('error', 'Failed to read file')
        onClose()
      })
      .finally(() => setLoading(false))
  }, [path, onClose])

  const handleEditorMount: OnMount = useCallback((editor, monaco) => {
    editor.addAction({
      id: 'save-file',
      label: 'Save File',
      keybindings: [monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS],
      run: () => { void saveRef.current() },
    })
  }, [])

  function handleChange(val: string | undefined) {
    setContent(val ?? '')
    setDirty(true)
  }

  function tryClose() {
    if (dirty) { setCloseConfirm(true); return }
    onClose()
  }

  const language = path ? detectLanguage(path) : 'plaintext'

  return (
    <>
      <Dialog.Root open={path !== null} onOpenChange={v => { if (!v) tryClose() }}>
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 bg-black/70 z-50 backdrop-blur-sm" />
          <Dialog.Content
            className={cn(
              'fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-50',
              'bg-bg-secondary border border-border rounded-xl shadow-2xl',
              'w-[95vw] max-w-5xl flex flex-col focus:outline-none',
            )}
            style={{ height: '85vh' }}
          >
            {/* Title bar */}
            <div className="flex items-center gap-3 px-4 py-3 border-b border-border flex-shrink-0">
              <Dialog.Title className="flex items-center gap-2 text-sm font-medium text-text-primary flex-1 min-w-0">
                <span className="truncate">{path ? fileName(path) : ''}</span>
                {dirty && <span className="w-2 h-2 rounded-full bg-accent-amber flex-shrink-0" title="Unsaved changes" />}
              </Dialog.Title>
              <Dialog.Description className="sr-only">Edit file contents</Dialog.Description>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => void save()}
                  disabled={saving || !dirty || fileData?.isBinary}
                  title="Save (Ctrl+S)"
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-white bg-accent-blue hover:bg-accent-blue/80 rounded-lg transition-colors disabled:opacity-40"
                >
                  {saving
                    ? <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    : <Save    className="w-3.5 h-3.5" />
                  }
                  Save
                </button>
                <button
                  onClick={tryClose}
                  className="p-1.5 rounded text-text-tertiary hover:text-text-primary hover:bg-bg-tertiary transition-colors"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            </div>

            {/* Editor area */}
            <div className="flex-1 min-h-0 overflow-hidden">
              {loading ? (
                <div className="flex items-center justify-center h-full gap-2 text-text-tertiary">
                  <Loader2 className="w-5 h-5 animate-spin" />
                  <span className="text-sm">Loading…</span>
                </div>
              ) : fileData?.isBinary ? (
                <div className="flex flex-col items-center justify-center h-full gap-3 text-text-secondary">
                  <AlertCircle className="w-10 h-10 text-accent-amber" />
                  <p className="text-sm">Binary file — cannot be edited as text.</p>
                </div>
              ) : (
                <Editor
                  height="100%"
                  language={language}
                  theme="vs-dark"
                  value={content}
                  onChange={handleChange}
                  onMount={handleEditorMount}
                  loading={<div className="flex items-center justify-center h-full text-text-tertiary text-sm">Loading editor…</div>}
                  options={{
                    fontSize: 13,
                    minimap: { enabled: false },
                    lineNumbers: 'on',
                    scrollBeyondLastLine: false,
                    wordWrap: 'on',
                    renderWhitespace: 'selection',
                    smoothScrolling: true,
                    cursorBlinking: 'smooth',
                    padding: { top: 12 },
                  }}
                />
              )}
            </div>

            {/* Status bar */}
            {fileData && !fileData.isBinary && (
              <div className="flex items-center gap-4 px-4 py-1.5 border-t border-border text-xs text-text-tertiary flex-shrink-0 font-mono">
                <span>{language}</span>
                <span>{fileData.encoding}</span>
                <span>{fileData.lineEnding === 'LF' ? 'LF' : 'CRLF'}</span>
                <span>{fileData.lineCount} lines</span>
                <span className="ml-auto">{formatBytes(fileData.size)}</span>
              </div>
            )}
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>

      {/* Unsaved changes confirm */}
      {closeConfirm && (
        <div className="fixed inset-0 bg-black/70 z-[60] flex items-center justify-center">
          <div className="bg-bg-secondary border border-border rounded-xl p-6 max-w-sm w-full shadow-2xl">
            <h3 className="text-base font-semibold text-text-primary mb-2">Unsaved changes</h3>
            <p className="text-sm text-text-secondary mb-5">
              You have unsaved changes. Discard them?
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setCloseConfirm(false)}
                className="px-4 py-2 text-sm text-text-secondary border border-border rounded-lg hover:bg-bg-tertiary transition-colors"
              >
                Keep editing
              </button>
              <button
                onClick={() => { setCloseConfirm(false); setDirty(false); onClose() }}
                className="px-4 py-2 text-sm font-medium text-white bg-accent-red hover:bg-accent-red/80 rounded-lg transition-colors"
              >
                Discard
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
