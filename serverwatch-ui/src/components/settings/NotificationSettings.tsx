import { useState, useEffect } from 'react'
import { Bell, BellOff, Volume2, VolumeX } from 'lucide-react'
import { useSettingsStore } from '../../stores/settingsStore'
import { cn } from '../../lib/utils'

type PermissionState = 'granted' | 'denied' | 'default' | 'unsupported'

export default function NotificationSettings() {
  const notificationsEnabled    = useSettingsStore(s => s.notificationsEnabled)
  const soundEnabled            = useSettingsStore(s => s.soundEnabled)
  const setNotificationsEnabled = useSettingsStore(s => s.setNotificationsEnabled)
  const setSoundEnabled         = useSettingsStore(s => s.setSoundEnabled)

  const [permission, setPermission] = useState<PermissionState>('default')
  const [requesting, setRequesting] = useState(false)

  useEffect(() => {
    if (!('Notification' in window)) { setPermission('unsupported'); return }
    setPermission(Notification.permission as PermissionState)
  }, [])

  async function requestPermission() {
    if (!('Notification' in window)) return
    setRequesting(true)
    try {
      const result = await Notification.requestPermission()
      setPermission(result as PermissionState)
      if (result === 'granted') setNotificationsEnabled(true)
    } finally {
      setRequesting(false)
    }
  }

  function toggleNotifications() {
    if (permission === 'granted') {
      setNotificationsEnabled(!notificationsEnabled)
    } else if (permission !== 'denied' && permission !== 'unsupported') {
      requestPermission()
    }
  }

  const notifActive = permission === 'granted' && notificationsEnabled

  return (
    <div className="bg-bg-secondary border border-border rounded-xl p-6 space-y-5">
      <div className="flex items-center gap-2 mb-1">
        {notifActive
          ? <Bell    className="w-4 h-4 text-text-tertiary" />
          : <BellOff className="w-4 h-4 text-text-tertiary" />
        }
        <h2 className="text-sm font-semibold text-text-primary">Notifications</h2>
      </div>

      {/* Browser notifications */}
      <div className="flex items-start justify-between gap-4 pb-5 border-b border-border">
        <div>
          <p className="text-sm text-text-primary">Browser Notifications</p>
          <p className="text-xs text-text-tertiary mt-0.5">
            Show a desktop notification when an alert fires.
          </p>
          {permission === 'denied' && (
            <p className="text-xs text-accent-amber mt-1">
              Blocked by browser. Enable notifications in your browser settings.
            </p>
          )}
          {permission === 'unsupported' && (
            <p className="text-xs text-text-tertiary mt-1">Not supported in this browser.</p>
          )}
          {permission === 'default' && !notifActive && (
            <button
              onClick={requestPermission}
              disabled={requesting}
              className="text-xs text-accent-blue hover:underline mt-1 disabled:opacity-50"
            >
              {requesting ? 'Requesting…' : 'Grant permission'}
            </button>
          )}
        </div>

        <button
          onClick={toggleNotifications}
          disabled={permission === 'denied' || permission === 'unsupported'}
          className={cn(
            'relative w-10 h-5 rounded-full transition-colors flex-shrink-0',
            notifActive ? 'bg-accent-blue' : 'bg-bg-primary border border-border',
            'disabled:opacity-40 disabled:cursor-not-allowed',
          )}
        >
          <span className={cn(
            'absolute top-0.5 w-4 h-4 rounded-full bg-white shadow transition-all',
            notifActive ? 'left-5' : 'left-0.5',
          )} />
        </button>
      </div>

      {/* Sound */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            {soundEnabled
              ? <Volume2 className="w-4 h-4 text-text-secondary" />
              : <VolumeX className="w-4 h-4 text-text-tertiary" />
            }
            <p className="text-sm text-text-primary">Alert Sound</p>
          </div>
          <p className="text-xs text-text-tertiary mt-0.5 ml-6">
            Play a chime when a new alert arrives in the Alerts page.
          </p>
        </div>

        <button
          onClick={() => setSoundEnabled(!soundEnabled)}
          className={cn(
            'relative w-10 h-5 rounded-full transition-colors flex-shrink-0',
            soundEnabled ? 'bg-accent-blue' : 'bg-bg-primary border border-border',
          )}
        >
          <span className={cn(
            'absolute top-0.5 w-4 h-4 rounded-full bg-white shadow transition-all',
            soundEnabled ? 'left-5' : 'left-0.5',
          )} />
        </button>
      </div>
    </div>
  )
}
