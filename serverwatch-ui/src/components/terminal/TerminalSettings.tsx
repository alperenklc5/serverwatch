import * as Dialog from '@radix-ui/react-dialog'
import { Settings, X } from 'lucide-react'
import { useState } from 'react'
import { cn } from '../../lib/utils'

export interface TerminalSettings {
  fontSize:    number
  scrollback:  number
  cursorStyle: 'block' | 'bar' | 'underline'
  cursorBlink: boolean
}

export const DEFAULT_SETTINGS: TerminalSettings = {
  fontSize:    14,
  scrollback:  5000,
  cursorStyle: 'bar',
  cursorBlink: true,
}

interface TerminalSettingsProps {
  settings: TerminalSettings
  onUpdate: (s: TerminalSettings) => void
}

const CURSOR_STYLES = ['block', 'bar', 'underline'] as const
const SCROLLBACK_OPTIONS = [1000, 2000, 5000, 10000] as const

export default function TerminalSettingsPanel({ settings, onUpdate }: TerminalSettingsProps) {
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState<TerminalSettings>(settings)

  function handleOpen() { setDraft(settings); setOpen(true) }
  function handleApply() { onUpdate(draft); setOpen(false) }

  return (
    <>
      <button
        onClick={handleOpen}
        title="Terminal settings"
        className="p-1.5 rounded-lg text-text-secondary hover:text-text-primary hover:bg-bg-tertiary transition-colors"
      >
        <Settings className="w-4 h-4" />
      </button>

      <Dialog.Root open={open} onOpenChange={setOpen}>
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 bg-black/60 z-50 backdrop-blur-sm" />
          <Dialog.Content className="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-50 bg-bg-secondary border border-border rounded-xl shadow-2xl w-full max-w-sm p-6 focus:outline-none">
            <div className="flex items-center justify-between mb-5">
              <Dialog.Title className="text-base font-semibold text-text-primary">Terminal Settings</Dialog.Title>
              <Dialog.Close asChild>
                <button className="p-1.5 rounded text-text-tertiary hover:text-text-primary hover:bg-bg-tertiary transition-colors">
                  <X className="w-4 h-4" />
                </button>
              </Dialog.Close>
            </div>
            <Dialog.Description className="sr-only">Configure terminal appearance</Dialog.Description>

            <div className="space-y-5">
              {/* Font size */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-sm text-text-secondary">Font size</label>
                  <span className="text-sm font-mono text-text-primary">{draft.fontSize}px</span>
                </div>
                <input
                  type="range"
                  min={10} max={20} step={1}
                  value={draft.fontSize}
                  onChange={e => setDraft(d => ({ ...d, fontSize: Number(e.target.value) }))}
                  className="w-full accent-accent-blue"
                />
                <div className="flex justify-between text-xs text-text-tertiary mt-1">
                  <span>10</span><span>20</span>
                </div>
              </div>

              {/* Scrollback */}
              <div>
                <label className="text-sm text-text-secondary block mb-2">Scrollback lines</label>
                <select
                  value={draft.scrollback}
                  onChange={e => setDraft(d => ({ ...d, scrollback: Number(e.target.value) }))}
                  className="w-full px-3 py-2 bg-bg-primary border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-accent-blue"
                >
                  {SCROLLBACK_OPTIONS.map(n => (
                    <option key={n} value={n}>{n.toLocaleString()} lines</option>
                  ))}
                </select>
              </div>

              {/* Cursor style */}
              <div>
                <label className="text-sm text-text-secondary block mb-2">Cursor style</label>
                <div className="flex gap-2">
                  {CURSOR_STYLES.map(style => (
                    <button
                      key={style}
                      onClick={() => setDraft(d => ({ ...d, cursorStyle: style }))}
                      className={cn(
                        'flex-1 py-1.5 text-xs rounded-lg border transition-colors capitalize',
                        draft.cursorStyle === style
                          ? 'border-accent-blue bg-accent-blue/20 text-accent-blue'
                          : 'border-border text-text-secondary hover:bg-bg-tertiary',
                      )}
                    >
                      {style}
                    </button>
                  ))}
                </div>
              </div>

              {/* Cursor blink */}
              <div className="flex items-center justify-between">
                <label className="text-sm text-text-secondary">Cursor blink</label>
                <button
                  onClick={() => setDraft(d => ({ ...d, cursorBlink: !d.cursorBlink }))}
                  className={cn(
                    'relative w-10 h-5 rounded-full transition-colors',
                    draft.cursorBlink ? 'bg-accent-blue' : 'bg-bg-primary border border-border',
                  )}
                >
                  <span className={cn(
                    'absolute top-0.5 w-4 h-4 rounded-full bg-white shadow transition-all',
                    draft.cursorBlink ? 'left-5' : 'left-0.5',
                  )} />
                </button>
              </div>
            </div>

            <div className="flex justify-end gap-3 mt-6">
              <Dialog.Close asChild>
                <button className="px-4 py-2 text-sm text-text-secondary border border-border rounded-lg hover:bg-bg-tertiary transition-colors">
                  Cancel
                </button>
              </Dialog.Close>
              <button
                onClick={handleApply}
                className="px-4 py-2 text-sm font-medium text-white bg-accent-blue hover:bg-accent-blue/80 rounded-lg transition-colors"
              >
                Apply
              </button>
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </>
  )
}
