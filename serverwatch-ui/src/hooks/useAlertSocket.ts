import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import { getAccessToken } from '../api/axios'
import { WS_URL } from '../lib/constants'
import { useAlertStore } from '../stores/alertStore'
import type { AlertEvent } from '../types'

/**
 * Maintains a long-lived STOMP subscription to /topic/alerts.
 * Mount once in MainLayout so the badge in Header always stays current.
 */
export function useAlertSocket() {
  const clientRef = useRef<Client | null>(null)

  useEffect(() => {
    const token = getAccessToken()
    if (!token) return

    const client = new Client({
      brokerURL:      WS_URL,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/topic/alerts', frame => {
          try {
            const alert = JSON.parse(frame.body) as AlertEvent
            useAlertStore.getState().addAlert(alert)
          } catch { /* ignore malformed frames */ }
        })
      },
    })

    clientRef.current = client
    client.activate()

    return () => {
      client.deactivate()
      clientRef.current = null
    }
  }, [])
}
