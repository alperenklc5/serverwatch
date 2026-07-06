import { create } from 'zustand'
import type { SystemMetric, NetworkMetric, ProcessInfo, UptimeInfo } from '../types'

export interface NetworkDataPoint {
  time: string
  rx: number
  tx: number
}

interface MetricsState {
  systemMetric: SystemMetric | null
  systemHistory: SystemMetric[]
  networkMetrics: NetworkMetric[]
  networkPrimary: NetworkDataPoint[]
  processes: ProcessInfo[]
  uptime: UptimeInfo | null
  isConnected: boolean
  updateSystemMetric: (metric: SystemMetric) => void
  updateNetworkMetrics: (metrics: NetworkMetric[]) => void
  updateProcesses: (processes: ProcessInfo[]) => void
  setUptime: (uptime: UptimeInfo) => void
  setConnected: (connected: boolean) => void
}

const MAX_HISTORY = 300

function isPrimaryInterface(name: string): boolean {
  const excluded = ['lo', 'docker', 'veth', 'br-', 'virbr', 'vmnet', 'tun', 'tap']
  return !excluded.some(p => name.toLowerCase().startsWith(p))
}

export const useMetricsStore = create<MetricsState>(set => ({
  systemMetric: null,
  systemHistory: [],
  networkMetrics: [],
  networkPrimary: [],
  processes: [],
  uptime: null,
  isConnected: false,

  updateSystemMetric: metric =>
    set(state => ({
      systemMetric: metric,
      systemHistory: [...state.systemHistory, metric].slice(-MAX_HISTORY),
    })),

  updateNetworkMetrics: metrics => {
    const primary = metrics
      .filter(m => isPrimaryInterface(m.interfaceName))
      .sort((a, b) => (b.receivedPerSecond + b.sentPerSecond) - (a.receivedPerSecond + a.sentPerSecond))
    const top = primary[0]

    set(state => ({
      networkMetrics: metrics,
      networkPrimary: top
        ? [
            ...state.networkPrimary,
            { time: top.timestamp, rx: top.receivedPerSecond, tx: top.sentPerSecond },
          ].slice(-MAX_HISTORY)
        : state.networkPrimary,
    }))
  },

  updateProcesses: processes => set({ processes }),
  setUptime: uptime => set({ uptime }),
  setConnected: isConnected => set({ isConnected }),
}))
