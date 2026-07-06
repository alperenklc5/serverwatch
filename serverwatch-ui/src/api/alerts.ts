import { apiClient } from './axios'
import type { ApiResponse, AlertRule, AlertEvent } from '../types'

export async function listRules(): Promise<AlertRule[]> {
  const { data } = await apiClient.get<ApiResponse<AlertRule[]>>('/api/alerts/rules')
  return data.data
}

export async function getRule(id: number): Promise<AlertRule> {
  const { data } = await apiClient.get<ApiResponse<AlertRule>>(`/api/alerts/rules/${id}`)
  return data.data
}

export async function createRule(rule: Omit<AlertRule, 'id' | 'createdAt' | 'updatedAt'>): Promise<AlertRule> {
  const { data } = await apiClient.post<ApiResponse<AlertRule>>('/api/alerts/rules', rule)
  return data.data
}

export async function updateRule(id: number, rule: Partial<AlertRule>): Promise<AlertRule> {
  const { data } = await apiClient.put<ApiResponse<AlertRule>>(`/api/alerts/rules/${id}`, rule)
  return data.data
}

export async function deleteRule(id: number): Promise<void> {
  await apiClient.delete(`/api/alerts/rules/${id}`)
}

export async function toggleRule(id: number, enabled: boolean): Promise<void> {
  await apiClient.patch(`/api/alerts/rules/${id}/toggle`, { enabled })
}

export async function testRule(id: number): Promise<void> {
  await apiClient.post(`/api/alerts/rules/${id}/test`)
}

export async function getHistory(hours = 24, limit = 100): Promise<AlertEvent[]> {
  const { data } = await apiClient.get<ApiResponse<AlertEvent[]>>(
    `/api/alerts/history?hours=${hours}&limit=${limit}`,
  )
  return data.data
}

export async function getRuleHistory(ruleId: number, limit = 50): Promise<AlertEvent[]> {
  const { data } = await apiClient.get<ApiResponse<AlertEvent[]>>(
    `/api/alerts/history/rule/${ruleId}?limit=${limit}`,
  )
  return data.data
}
