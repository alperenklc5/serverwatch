import { Terminal } from 'lucide-react'
import { useSettingsStore } from '../../stores/settingsStore'
import type { TerminalPrefs } from '../../stores/settingsStore'
import { cn } from '../../lib/utils'

const CURSOR_STYLES: TerminalPrefs['cursorStyle'][] = ['block', 'bar', 'underline']
const SCROLLBACK_OPTIONS = [1000, 2000, 5000, 10000] as const

export default function TerminalPreferences() {
  const prefs    = useSettingsStore(s => s.terminalPrefs)
  const setPrefs = useSettingsStore(s => s.setTerminalPrefs)

  function update(patch: Partial<TerminalPrefs>) {
    setPrefs({ ...prefs, ...patch })
  }

  return (
    <div className="bg-bg-secondary border border-border rounded-xl p-6">
      <div className="flex items-center gap-2 mb-5">
        <Terminal className="w-4 h-4 text-text-tertiary" />
        <h2 className="text-sm font-semibold text-text-primary">Terminal Preferences</h2>
      </div>

      <div className="space-y-5 max-w-sm">
        {/* Font size */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <label className="text-sm text-text-secondary">Font size</label>
            <span className="text-sm font-mono text-text-primary">{prefs.fontSize}px</span>
          </div>
          <input
            type="range"
            min={10} max={20} step={1}
            value={prefs.fontSize}
            onChange={e => update({ fontSize: Number(e.target.value) })}
            className="w-full accent-accent-blue"
          />
          <div className="flex justify-between text-xs text-text-tertiary mt-1">
            <span>10px</span><span>20px</span>
          </div>
        </div>

        {/* Scrollback */}
        <div>
          <label className="text-sm text-text-secondary block mb-2">Scrollback lines</label>
          <select
            value={prefs.scrollback}
            onChange={e => update({ scrollback: Number(e.target.value) })}
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
                onClick={() => update({ cursorStyle: style })}
                className={cn(
                  'flex-1 py-1.5 text-xs rounded-lg border transition-colors capitalize',
                  prefs.cursorStyle === style
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
            onClick={() => update({ cursorBlink: !prefs.cursorBlink })}
            className={cn(
              'relative w-10 h-5 rounded-full transition-colors',
              prefs.cursorBlink ? 'bg-accent-blue' : 'bg-bg-primary border border-border',
            )}
          >
            <span className={cn(
              'absolute top-0.5 w-4 h-4 rounded-full bg-white shadow transition-all',
              prefs.cursorBlink ? 'left-5' : 'left-0.5',
            )} />
          </button>
        </div>
      </div>
    </div>
  )
}
