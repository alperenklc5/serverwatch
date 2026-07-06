import { useEffect } from 'react'
import type { SystemMetric, NetworkMetric, ProcessInfo } from '../types'
import { useWebSocket } from './useWebSocket'
import { useMetricsStore } from '../stores/metricsStore'
import { getUptime } from '../api/metrics'

export function useMetrics(): void {
  const { isConnected, subscribe } = useWebSocket()

  // Fetch uptime once on mount
  useEffect(() => {
    getUptime()
      .then(uptime => useMetricsStore.getState().setUptime(uptime))
      .catch(() => {})
  }, [])

  // Set up topic subscriptions whenever connected
  useEffect(() => {
    if (!isConnected) return

    const store = useMetricsStore.getState()
    const subs = [
      subscribe<SystemMetric>('/topic/metrics/system', store.updateSystemMetric),
      subscribe<NetworkMetric[]>('/topic/metrics/network', store.updateNetworkMetrics),
      subscribe<ProcessInfo[]>('/topic/metrics/processes', store.updateProcesses),
    ].filter((s): s is NonNullable<typeof s> => s !== null)

    return () => subs.forEach(s => s.unsubscribe())
  }, [isConnected, subscribe])
}
