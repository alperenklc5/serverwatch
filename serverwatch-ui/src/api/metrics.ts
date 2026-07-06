import { apiClient } from './axios'
import type { ApiResponse, SystemMetric, NetworkMetric, ProcessInfo, UptimeInfo } from '../types'

export async function getSystemMetrics(): Promise<SystemMetric> {
  const { data } = await apiClient.get<ApiResponse<SystemMetric>>('/api/metrics/system')
  return data.data
}

export async function getNetworkMetrics(): Promise<NetworkMetric[]> {
  const { data } = await apiClient.get<ApiResponse<NetworkMetric[]>>('/api/metrics/network')
  return data.data
}

export async function getProcesses(limit = 20): Promise<ProcessInfo[]> {
  const { data } = await apiClient.get<ApiResponse<ProcessInfo[]>>(`/api/metrics/processes?limit=${limit}`)
  return data.data
}

export async function getUptime(): Promise<UptimeInfo> {
  const { data } = await apiClient.get<ApiResponse<UptimeInfo>>('/api/metrics/uptime')
  return data.data
}
