import { useEffect, useRef, useState, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import { getAccessToken } from '../api/axios'
import { WS_URL } from '../lib/constants'
import type { ContainerStats } from '../types'

export interface DockerSocketHook {
  statsMap: Record<string, ContainerStats>
  isConnected: boolean
  publish: (destination: string, body: Record<string, string>) => void
  subscribeQueue: (dest: string, cb: (body: string) => void) => (() => void) | null
}

export function useDockerSocket(): DockerSocketHook {
  const [statsMap, setStatsMap] = useState<Record<string, ContainerStats>>({})
  const [isConnected, setIsConnected] = useState(false)
  const clientRef = useRef<Client | null>(null)
  const reconnectCount = useRef(0)

  useEffect(() => {
    const token = getAccessToken()
    if (!token) return

    const client = new Client({
      brokerURL: WS_URL,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 2000,
      beforeConnect: async () => {
        const delay = Math.min(1000 * Math.pow(2, reconnectCount.current), 30000)
        if (clientRef.current) clientRef.current.reconnectDelay = delay
        reconnectCount.current++
      },
      onConnect: () => {
        reconnectCount.current = 0
        setIsConnected(true)

        client.subscribe('/topic/containers', frame => {
          try {
            const batch = JSON.parse(frame.body) as ContainerStats[]
            setStatsMap(prev => {
              const next = { ...prev }
              batch.forEach(s => { next[s.containerId] = s })
              return next
            })
          } catch { /* ignore */ }
        })
      },
      onDisconnect: () => setIsConnected(false),
      onStompError:  () => setIsConnected(false),
    })

    clientRef.current = client
    client.activate()

    return () => {
      client.deactivate()
      clientRef.current = null
      setIsConnected(false)
    }
  }, [])

  const publish = useCallback((destination: string, body: Record<string, string>) => {
    clientRef.current?.publish({ destination, body: JSON.stringify(body) })
  }, [])

  const subscribeQueue = useCallback(
    (dest: string, cb: (body: string) => void): (() => void) | null => {
      if (!clientRef.current?.connected) return null
      const sub = clientRef.current.subscribe(dest, frame => cb(frame.body))
      return () => sub.unsubscribe()
    },
    [],
  )

  return { statsMap, isConnected, publish, subscribeQueue }
}
