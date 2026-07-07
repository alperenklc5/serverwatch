import { LayoutDashboard } from 'lucide-react'
import { useSettingsStore } from '../../stores/settingsStore'
import type { DashboardWidgets } from '../../stores/settingsStore'
import { cn } from '../../lib/utils'

const WIDGETS: { key: keyof DashboardWidgets; label: string; description: string }[] = [
  { key: 'serverInfo', label: 'Server Info Bar',  description: 'Hostname, OS, uptime'    },
  { key: 'cpu',        label: 'CPU',               description: 'CPU usage chart & stat'  },
  { key: 'memory',     label: 'Memory',            description: 'RAM usage chart & stat'  },
  { key: 'disk',       label: 'Disk',              description: 'Disk usage chart & stat' },
  { key: 'network',    label: 'Network',           description: 'Network traffic chart'   },
  { key: 'processes',  label: 'Top Processes',     description: 'Process table'           },
]

export default function DashboardWidgetToggle() {
  const widgets           = useSettingsStore(s => s.dashboardWidgets)
  const setDashboardWidget = useSettingsStore(s => s.setDashboardWidget)

  return (
    <div className="bg-bg-secondary border border-border rounded-xl p-6">
      <div className="flex items-center gap-2 mb-4">
        <LayoutDashboard className="w-4 h-4 text-text-tertiary" />
        <h2 className="text-sm font-semibold text-text-primary">Dashboard Widgets</h2>
      </div>

      <div className="space-y-2">
        {WIDGETS.map(({ key, label, description }) => {
          const checked = widgets[key]
          return (
            <button
              key={key}
              onClick={() => setDashboardWidget(key, !checked)}
              className={cn(
                'w-full flex items-center gap-3 px-3 py-2.5 rounded-lg border transition-colors text-left',
                checked
                  ? 'border-accent-blue/30 bg-accent-blue/5'
                  : 'border-border hover:bg-bg-tertiary',
              )}
            >
              <span className={cn(
                'w-4 h-4 rounded border flex-shrink-0 flex items-center justify-center transition-colors',
                checked ? 'bg-accent-blue border-accent-blue' : 'border-border',
              )}>
                {checked && (
                  <svg className="w-3 h-3 text-white" fill="none" viewBox="0 0 12 12">
                    <path d="M2 6l3 3 5-5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                )}
              </span>
              <div className="flex-1 min-w-0">
                <span className="text-sm text-text-primary">{label}</span>
                <span className="text-xs text-text-tertiary ml-2">{description}</span>
              </div>
            </button>
          )
        })}
      </div>
    </div>
  )
}
