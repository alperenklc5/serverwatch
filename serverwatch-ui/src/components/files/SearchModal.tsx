import { useEffect, useRef, useState } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import { Search, X, Loader2, File, Folder } from 'lucide-react'
import { searchFiles } from '../../api/files'
import type { FileEntry } from '../../types'
import { formatBytes, formatRelative } from '../../lib/formatters'

interface SearchModalProps {
  open: boolean
  rootPath: string
  onOpenChange: (open: boolean) => void
  onNavigate: (path: string, highlight: string) => void
}

export default function SearchModal({ open, rootPath, onOpenChange, onNavigate }: SearchModalProps) {
  const [query, setQuery]     = useState('')
  const [results, setResults] = useState<FileEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [searched, setSearched] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    if (open) {
      setQuery('')
      setResults([])
      setSearched(false)
      setTimeout(() => inputRef.current?.focus(), 30)
    }
  }, [open])

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    if (!query.trim() || query.length < 2) {
      setResults([])
      setSearched(false)
      return
    }
    debounceRef.current = setTimeout(async () => {
      setLoading(true)
      try {
        const list = await searchFiles(rootPath, query.trim())
        setResults(list)
        setSearched(true)
      } catch {
        setResults([])
      } finally {
        setLoading(false)
      }
    }, 300)
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
  }, [query, rootPath])

  function handleSelect(entry: FileEntry) {
    const dir = entry.type === 'DIRECTORY'
      ? entry.path
      : entry.path.substring(0, entry.path.lastIndexOf('/')) || '/'
    onNavigate(dir, entry.name)
    onOpenChange(false)
  }

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-black/60 z-50 backdrop-blur-sm" />
        <Dialog.Content className="fixed left-1/2 top-24 -translate-x-1/2 z-50 bg-bg-secondary border border-border rounded-xl shadow-2xl w-full max-w-lg focus:outline-none overflow-hidden">
          <Dialog.Title className="sr-only">Search files</Dialog.Title>
          <Dialog.Description className="sr-only">Search for files by name</Dialog.Description>

          {/* Search input */}
          <div className="flex items-center gap-3 px-4 py-3 border-b border-border">
            {loading
              ? <Loader2 className="w-4 h-4 text-text-tertiary animate-spin flex-shrink-0" />
              : <Search className="w-4 h-4 text-text-tertiary flex-shrink-0" />
            }
            <input
              ref={inputRef}
              value={query}
              onChange={e => setQuery(e.target.value)}
              placeholder={`Search in ${rootPath}…`}
              className="flex-1 bg-transparent text-sm text-text-primary placeholder:text-text-tertiary focus:outline-none"
            />
            {query && (
              <button onClick={() => setQuery('')} className="p-0.5 text-text-tertiary hover:text-text-primary">
                <X className="w-3.5 h-3.5" />
              </button>
            )}
            <Dialog.Close asChild>
              <button className="p-1 rounded text-text-tertiary hover:text-text-primary hover:bg-bg-tertiary transition-colors">
                <X className="w-4 h-4" />
              </button>
            </Dialog.Close>
          </div>

          {/* Results */}
          <div className="max-h-96 overflow-y-auto">
            {results.length > 0 ? (
              results.map(entry => (
                <button
                  key={entry.path}
                  onClick={() => handleSelect(entry)}
                  className="flex items-center gap-3 w-full px-4 py-2.5 hover:bg-bg-tertiary transition-colors text-left"
                >
                  {entry.type === 'DIRECTORY'
                    ? <Folder className="w-4 h-4 text-accent-amber flex-shrink-0" />
                    : <File  className="w-4 h-4 text-text-tertiary flex-shrink-0" />
                  }
                  <div className="flex-1 min-w-0">
                    <div className="text-sm text-text-primary truncate">{entry.name}</div>
                    <div className="text-xs text-text-tertiary truncate font-mono">{entry.path}</div>
                  </div>
                  <div className="text-right flex-shrink-0 space-y-0.5">
                    <div className="text-xs text-text-tertiary">{formatBytes(entry.size)}</div>
                    <div className="text-xs text-text-tertiary">{formatRelative(entry.modifiedAt)}</div>
                  </div>
                </button>
              ))
            ) : searched && !loading ? (
              <div className="px-4 py-8 text-center text-sm text-text-tertiary">
                No files found for &ldquo;{query}&rdquo;
              </div>
            ) : query.length >= 2 ? null : (
              <div className="px-4 py-6 text-center text-sm text-text-tertiary">
                Type at least 2 characters to search
              </div>
            )}
          </div>

          {results.length > 0 && (
            <div className="px-4 py-2 border-t border-border text-xs text-text-tertiary">
              {results.length} result{results.length !== 1 ? 's' : ''}
            </div>
          )}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
