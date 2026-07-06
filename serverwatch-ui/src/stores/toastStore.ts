import { create } from 'zustand'

export type ToastType = 'success' | 'error' | 'info'

export interface ToastItem {
  id: string
  type: ToastType
  message: string
}

interface ToastState {
  toasts: ToastItem[]
  addToast: (type: ToastType, message: string) => void
  removeToast: (id: string) => void
}

let _nextId = 0

export const useToastStore = create<ToastState>(set => ({
  toasts: [],

  addToast: (type, message) => {
    const id = String(++_nextId)
    set(state => ({ toasts: [...state.toasts, { id, type, message }] }))
    setTimeout(() => {
      set(state => ({ toasts: state.toasts.filter(t => t.id !== id) }))
    }, 4000)
  },

  removeToast: id =>
    set(state => ({ toasts: state.toasts.filter(t => t.id !== id) })),
}))

export function useToast() {
  const addToast = useToastStore(s => s.addToast)
  return {
    success: (msg: string) => addToast('success', msg),
    error:   (msg: string) => addToast('error',   msg),
    info:    (msg: string) => addToast('info',     msg),
  }
}
