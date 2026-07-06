import { apiClient } from './axios'
import type { ApiResponse, TerminalSession } from '../types'

export async function listSessions(): Promise<TerminalSession[]> {
  const { data } = await apiClient.get<ApiResponse<TerminalSession[]>>('/api/terminal/sessions')
  return data.data
}

export async function getSessionBuffer(sessionId: string): Promise<string> {
  const { data } = await apiClient.get<ApiResponse<string>>(
    `/api/terminal/sessions/${sessionId}/buffer`,
  )
  return data.data ?? ''
}

export async function getTerminalShells(): Promise<string[]> {
  const { data } = await apiClient.get<ApiResponse<string[]>>('/api/terminal/shells')
  return data.data
}
