import { useCallback, useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import { getAccessToken } from '../api/axios'
import { WS_URL } from '../lib/constants'
import type { TerminalSession } from '../types'

export interface TerminalSocketHook {
  isConnected: boolean
  createSession: (shell: string, cols: number, rows: number) => void
  sendInput: (sessionId: string, data: string) => void
  sendResize: (sessionId: string, cols: number, rows: number) => void
  sendPing: (sessionId: string) => void
  closeSession: (sessionId: string) => void
  addOutputListener: (
    sessionId: string,
    cb: (data: string, closed: boolean) => void,
  ) => () => void
  addSessionCreatedListener: (cb: (session: TerminalSession) => void) => () => void
}

export function useTerminalSocket(): TerminalSocketHook {
  const [isConnected, setIsConnected] = useState(false)
  const clientRef  = useRef<Client | null>(null)
  const reconnects = useRef(0)

  // Listener maps — stored in refs so effects referencing them don't need to re-run
  const outputListeners         = useRef<Map<string, (data: string, closed: boolean) => void>>(new Map())
  const sessionCreatedListeners = useRef<Set<(session: TerminalSession) => void>>(new Set())

  useEffect(() => {
    const token = getAccessToken()
    if (!token) return

    const client = new Client({
      brokerURL: WS_URL,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 2000,
      beforeConnect: async () => {
        const delay = Math.min(1000 * Math.pow(2, reconnects.current), 30_000)
        if (clientRef.current) clientRef.current.reconnectDelay = delay
        reconnects.current++
      },
      onConnect: () => {
        reconnects.current = 0
        setIsConnected(true)

        // Single subscription for all terminal output — routed by sessionId
        client.subscribe('/user/queue/terminal', frame => {
          try {
            const msg = JSON.parse(frame.body) as {
              sessionId: string
              type: string
              data?: string
            }
            const cb = outputListeners.current.get(msg.sessionId)
            if (!cb) return
            if (msg.type === 'OUTPUT') cb(msg.data ?? '', false)
            else if (msg.type === 'CLOSED') cb('', true)
          } catch { /* ignore malformed frames */ }
        })

        // One-shot listener pool for session-created responses
        client.subscribe('/user/queue/terminal-created', frame => {
          try {
            const session = JSON.parse(frame.body) as TerminalSession
            sessionCreatedListeners.current.forEach(cb => cb(session))
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

  const createSession = useCallback((shell: string, cols: number, rows: number) => {
    clientRef.current?.publish({
      destination: '/app/terminal/create',
      body: JSON.stringify({ shell, cols, rows }),
    })
  }, [])

  const sendInput = useCallback((sessionId: string, data: string) => {
    clientRef.current?.publish({
      destination: '/app/terminal/input',
      body: JSON.stringify({ sessionId, type: 'INPUT', data }),
    })
  }, [])

  const sendResize = useCallback((sessionId: string, cols: number, rows: number) => {
    clientRef.current?.publish({
      destination: '/app/terminal/input',
      body: JSON.stringify({ sessionId, type: 'RESIZE', cols, rows }),
    })
  }, [])

  const sendPing = useCallback((sessionId: string) => {
    clientRef.current?.publish({
      destination: '/app/terminal/input',
      body: JSON.stringify({ sessionId, type: 'PING' }),
    })
  }, [])

  const closeSession = useCallback((sessionId: string) => {
    clientRef.current?.publish({
      destination: '/app/terminal/close',
      body: JSON.stringify({ sessionId }),
    })
  }, [])

  const addOutputListener = useCallback((
    sessionId: string,
    cb: (data: string, closed: boolean) => void,
  ) => {
    outputListeners.current.set(sessionId, cb)
    return () => { outputListeners.current.delete(sessionId) }
  }, [])

  const addSessionCreatedListener = useCallback((cb: (session: TerminalSession) => void) => {
    sessionCreatedListeners.current.add(cb)
    return () => { sessionCreatedListeners.current.delete(cb) }
  }, [])

  return {
    isConnected,
    createSession,
    sendInput,
    sendResize,
    sendPing,
    closeSession,
    addOutputListener,
    addSessionCreatedListener,
  }
}
