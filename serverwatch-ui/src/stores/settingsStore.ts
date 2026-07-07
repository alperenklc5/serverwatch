import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export interface DashboardWidgets {
  cpu:        boolean
  memory:     boolean
  disk:       boolean
  network:    boolean
  processes:  boolean
  serverInfo: boolean
}

export interface TerminalPrefs {
  fontSize:    number
  scrollback:  number
  cursorStyle: 'block' | 'bar' | 'underline'
  cursorBlink: boolean
}

interface SettingsState {
  theme:                'dark' | 'light' | 'system'
  dashboardWidgets:     DashboardWidgets
  terminalPrefs:        TerminalPrefs
  notificationsEnabled: boolean
  soundEnabled:         boolean

  setTheme:                (theme: SettingsState['theme']) => void
  setDashboardWidget:      (key: keyof DashboardWidgets, value: boolean) => void
  setTerminalPrefs:        (prefs: TerminalPrefs) => void
  setNotificationsEnabled: (enabled: boolean) => void
  setSoundEnabled:         (enabled: boolean) => void
}

export const useSettingsStore = create<SettingsState>()(
  persist(
    set => ({
      theme: 'dark',
      dashboardWidgets: {
        cpu:        true,
        memory:     true,
        disk:       true,
        network:    true,
        processes:  true,
        serverInfo: true,
      },
      terminalPrefs: {
        fontSize:    14,
        scrollback:  5000,
        cursorStyle: 'bar',
        cursorBlink: true,
      },
      notificationsEnabled: false,
      soundEnabled:         true,

      setTheme: theme => set({ theme }),
      setDashboardWidget: (key, value) =>
        set(state => ({ dashboardWidgets: { ...state.dashboardWidgets, [key]: value } })),
      setTerminalPrefs: terminalPrefs => set({ terminalPrefs }),
      setNotificationsEnabled: notificationsEnabled => set({ notificationsEnabled }),
      setSoundEnabled: soundEnabled => set({ soundEnabled }),
    }),
    { name: 'serverwatch-settings' },
  ),
)
