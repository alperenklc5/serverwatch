import { create } from 'zustand'
import type { AlertEvent } from '../types'

interface AlertStoreState {
  unreadCount:  number
  recentAlerts: AlertEvent[]   // max 50, newest first
  addAlert:     (alert: AlertEvent) => void
  clearUnread:  () => void
}

export const useAlertStore = create<AlertStoreState>(set => ({
  unreadCount:  0,
  recentAlerts: [],

  addAlert: alert =>
    set(state => ({
      unreadCount:  state.unreadCount + 1,
      recentAlerts: [alert, ...state.recentAlerts].slice(0, 50),
    })),

  clearUnread: () => set({ unreadCount: 0 }),
}))
