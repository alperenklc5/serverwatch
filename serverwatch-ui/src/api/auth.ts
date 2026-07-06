import { apiClient, setTokens, clearTokens, getRefreshToken } from './axios'
import type { AuthResponse, User, ApiResponse } from '../types'

export async function login(username: string, password: string): Promise<AuthResponse> {
  const { data } = await apiClient.post<ApiResponse<AuthResponse>>('/api/auth/login', {
    username,
    password,
  })
  const authData = data.data
  setTokens(authData.accessToken, authData.refreshToken)
  return authData
}

export async function refresh(refreshToken: string): Promise<AuthResponse> {
  const { data } = await apiClient.post<ApiResponse<AuthResponse>>('/api/auth/refresh', {
    refreshToken,
  })
  return data.data
}

export async function logout(): Promise<void> {
  const refreshToken = getRefreshToken()
  try {
    await apiClient.post('/api/auth/logout', { refreshToken })
  } finally {
    clearTokens()
  }
}

export async function getMe(): Promise<User> {
  const { data } = await apiClient.get<ApiResponse<User>>('/api/auth/me')
  return data.data
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await apiClient.post('/api/auth/change-password', { currentPassword, newPassword })
}
