import { Moon, Sun, Monitor } from 'lucide-react'
import { useSettingsStore } from '../../stores/settingsStore'
import { cn } from '../../lib/utils'

const THEMES = [
  { key: 'dark'   as const, label: 'Dark',   icon: Moon,    available: true  },
  { key: 'light'  as const, label: 'Light',  icon: Sun,     available: false },
  { key: 'system' as const, label: 'System', icon: Monitor, available: false },
]

export default function ThemeSelector() {
  const theme    = useSettingsStore(s => s.theme)
  const setTheme = useSettingsStore(s => s.setTheme)

  return (
    <div className="bg-bg-secondary border border-border rounded-xl p-6">
      <h2 className="text-sm font-semibold text-text-primary mb-4">Theme</h2>
      <div className="flex gap-3">
        {THEMES.map(({ key, label, icon: Icon, available }) => (
          <button
            key={key}
            onClick={() => { if (available) setTheme(key) }}
            disabled={!available}
            className={cn(
              'flex flex-col items-center gap-2 flex-1 py-4 rounded-xl border transition-all',
              theme === key && available
                ? 'border-accent-blue bg-accent-blue/10 text-accent-blue'
                : available
                  ? 'border-border text-text-secondary hover:bg-bg-tertiary hover:text-text-primary'
                  : 'border-border text-text-tertiary opacity-40 cursor-not-allowed',
            )}
          >
            <Icon className="w-5 h-5" />
            <span className="text-xs font-medium">{label}</span>
            {!available && <span className="text-[9px] text-text-tertiary">Soon</span>}
          </button>
        ))}
      </div>
    </div>
  )
}
