import { useEffect, useRef, useCallback } from 'react'
import { Client, type StompSubscription } from '@stomp/stompjs'
import { getAccessToken } from '../api/axios'
import { WS_URL } from '../lib/constants'
import { useMetricsStore } from '../stores/metricsStore'
import { useAuthStore } from '../stores/authStore'

export interface WebSocketHook {
  isConnected: boolean
  subscribe: <T>(topic: string, callback: (data: T) => void) => StompSubscription | null
  publish: (destination: string, body: string) => void
}

export function useWebSocket(): WebSocketHook {
  const clientRef = useRef<Client | null>(null)
  const setConnected = useMetricsStore(s => s.setConnected)
  const isConnected = useMetricsStore(s => s.isConnected)
  const reconnectCount = useRef(0)
  const isAuthenticated = useAuthStore(s => s.isAuthenticated)

  useEffect(() => {
    if (!isAuthenticated) return
    const token = getAccessToken()
    if (!token) return

    const client = new Client({
      brokerURL: WS_URL,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 2000,
      beforeConnect: async () => {
        const delay = Math.min(1000 * Math.pow(2, reconnectCount.current), 30000)
        if (clientRef.current) clientRef.current.reconnectDelay = delay
        reconnectCount.current += 1
      },
      onConnect: () => {
        reconnectCount.current = 0
        setConnected(true)
      },
      onDisconnect: () => setConnected(false),
      onStompError: () => setConnected(false),
    })

    clientRef.current = client
    client.activate()

    return () => {
      client.deactivate()
      clientRef.current = null
      setConnected(false)
    }
  }, [isAuthenticated, setConnected])

  const subscribe = useCallback(
    <T>(topic: string, callback: (data: T) => void): StompSubscription | null => {
      if (!clientRef.current?.connected) return null
      return clientRef.current.subscribe(topic, frame => {
        try {
          callback(JSON.parse(frame.body) as T)
        } catch {
          // ignore malformed messages
        }
      })
    },
    [],
  )

  const publish = useCallback((destination: string, body: string): void => {
    clientRef.current?.publish({ destination, body })
  }, [])

  return { isConnected, subscribe, publish }
}
