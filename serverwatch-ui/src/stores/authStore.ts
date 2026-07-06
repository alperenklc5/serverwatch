import { create } from 'zustand'
import { login as apiLogin, logout as apiLogout, getMe, refresh } from '../api/auth'
import { setTokens, clearTokens, getRefreshToken } from '../api/axios'
import type { User } from '../types'

interface AuthState {
  user: User | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  checkAuth: () => Promise<void>
  setUser: (user: User) => void
}

export const useAuthStore = create<AuthState>(set => ({
  user: null,
  isAuthenticated: false,
  isLoading: true,

  login: async (username, password) => {
    const authData = await apiLogin(username, password)
    set({ user: authData.user, isAuthenticated: true })
  },

  logout: async () => {
    try {
      await apiLogout()
    } finally {
      set({ user: null, isAuthenticated: false })
      window.location.href = '/login'
    }
  },

  checkAuth: async () => {
    set({ isLoading: true })
    try {
      const user = await getMe()
      set({ user, isAuthenticated: true, isLoading: false })
    } catch {
      const refreshToken = getRefreshToken()
      if (refreshToken) {
        try {
          const authData = await refresh(refreshToken)
          setTokens(authData.accessToken, authData.refreshToken)
          const user = await getMe()
          set({ user, isAuthenticated: true, isLoading: false })
        } catch {
          clearTokens()
          set({ user: null, isAuthenticated: false, isLoading: false })
        }
      } else {
        set({ user: null, isAuthenticated: false, isLoading: false })
      }
    }
  },

  setUser: user => set({ user }),
}))
