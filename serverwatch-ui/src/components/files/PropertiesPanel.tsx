import { useState } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import { X, Loader2 } from 'lucide-react'
import type { FileEntry } from '../../types'
import { formatBytes, formatDate } from '../../lib/formatters'
import { chmodEntry } from '../../api/files'
import { useToastStore } from '../../stores/toastStore'

interface PropertiesPanelProps {
  entry: FileEntry | null
  onClose: () => void
}

function Row({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex justify-between gap-4 py-2 border-b border-border/50 last:border-0">
      <span className="text-xs text-text-tertiary flex-shrink-0">{label}</span>
      <span className={`text-xs text-text-primary text-right break-all ${mono ? 'font-mono' : ''}`}>{value}</span>
    </div>
  )
}

export default function PropertiesPanel({ entry, onClose }: PropertiesPanelProps) {
  const [permissions, setPermissions] = useState('')
  const [chmodLoading, setChmodLoading] = useState(false)

  async function handleChmod() {
    if (!entry || !permissions.trim()) return
    setChmodLoading(true)
    try {
      await chmodEntry(entry.path, permissions.trim())
      useToastStore.getState().addToast('success', 'Permissions updated')
      onClose()
    } catch {
      useToastStore.getState().addToast('error', 'Failed to update permissions')
    } finally {
      setChmodLoading(false)
    }
  }

  return (
    <Dialog.Root open={entry !== null} onOpenChange={v => { if (!v) onClose() }}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-black/60 z-50 backdrop-blur-sm" />
        <Dialog.Content className="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-50 bg-bg-secondary border border-border rounded-xl shadow-2xl w-full max-w-sm p-6 focus:outline-none">
          <div className="flex items-center justify-between mb-4">
            <Dialog.Title className="text-base font-semibold text-text-primary">Properties</Dialog.Title>
            <Dialog.Close asChild>
              <button
                onClick={onClose}
                className="p-1.5 rounded text-text-tertiary hover:text-text-primary hover:bg-bg-tertiary transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </Dialog.Close>
          </div>
          <Dialog.Description className="sr-only">File properties</Dialog.Description>

          {entry && (
            <div className="space-y-1">
              <Row label="Name"     value={entry.name} />
              <Row label="Path"     value={entry.path} mono />
              <Row label="Type"     value={entry.type} />
              {entry.mimeType && <Row label="MIME"   value={entry.mimeType} mono />}
              <Row label="Size"     value={`${formatBytes(entry.size)} (${entry.size.toLocaleString()} bytes)`} />
              <Row label="Modified" value={formatDate(entry.modifiedAt)} />
              <Row label="Created"  value={formatDate(entry.createdAt)} />
              <Row label="Owner"    value={`${entry.owner} / ${entry.group}`} mono />
              <Row label="Permissions" value={entry.permissions} mono />
              {entry.symlinkTarget && (
                <Row label="Symlink target" value={entry.symlinkTarget} mono />
              )}

              {/* Chmod */}
              {entry.isWritable && (
                <div className="pt-3 mt-2 border-t border-border">
                  <p className="text-xs text-text-secondary mb-2">Change permissions (e.g. 755)</p>
                  <div className="flex gap-2">
                    <input
                      value={permissions}
                      onChange={e => setPermissions(e.target.value)}
                      placeholder={entry.permissionsNumeric}
                      maxLength={4}
                      className="flex-1 px-3 py-1.5 bg-bg-primary border border-border rounded-lg text-sm font-mono text-text-primary placeholder:text-text-tertiary focus:outline-none focus:border-accent-blue transition-colors"
                    />
                    <button
                      onClick={() => void handleChmod()}
                      disabled={chmodLoading || !permissions.trim()}
                      className="px-3 py-1.5 text-sm font-medium text-white bg-accent-blue hover:bg-accent-blue/80 rounded-lg transition-colors disabled:opacity-50 flex items-center gap-1.5"
                    >
                      {chmodLoading && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                      Apply
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
