import { useCallback, useEffect, useRef } from 'react'
import type { TerminalSession } from '../types'
import type { WebSocketHook } from './useWebSocket'

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

/**
 * Layers terminal session management on top of the shared authenticated
 * WebSocket connection. No second STOMP client is created — all messages
 * are sent over the same connection that already completed JWT CONNECT.
 */
export function useTerminalSocket(ws: WebSocketHook): TerminalSocketHook {
  // Listener maps stored in refs so subscription effects don't re-run when
  // callers add/remove listeners.
  const outputListeners         = useRef<Map<string, (data: string, closed: boolean) => void>>(new Map())
  const sessionCreatedListeners = useRef<Set<(session: TerminalSession) => void>>(new Set())

  // Subscribe to the two terminal queues whenever the shared connection is up.
  // Cleanup (unsubscribe) runs automatically when the connection drops or the
  // component unmounts.
  useEffect(() => {
    if (!ws.isConnected) return

    const subOutput = ws.subscribe<{ sessionId: string; type: string; data?: string }>(
      '/user/queue/terminal',
      msg => {
        const cb = outputListeners.current.get(msg.sessionId)
        if (!cb) return
        if (msg.type === 'OUTPUT') cb(msg.data ?? '', false)
        else if (msg.type === 'CLOSED') cb('', true)
      },
    )

    const subCreated = ws.subscribe<TerminalSession>(
      '/user/queue/terminal-created',
      session => {
        sessionCreatedListeners.current.forEach(cb => cb(session))
      },
    )

    return () => {
      subOutput?.unsubscribe()
      subCreated?.unsubscribe()
    }
  }, [ws.isConnected, ws.subscribe])

  const createSession = useCallback((shell: string, cols: number, rows: number) => {
    ws.publish('/app/terminal/create', JSON.stringify({ shell, cols, rows }))
  }, [ws.publish])

  const sendInput = useCallback((sessionId: string, data: string) => {
    ws.publish('/app/terminal/input', JSON.stringify({ sessionId, type: 'INPUT', data }))
  }, [ws.publish])

  const sendResize = useCallback((sessionId: string, cols: number, rows: number) => {
    ws.publish('/app/terminal/input', JSON.stringify({ sessionId, type: 'RESIZE', cols, rows }))
  }, [ws.publish])

  const sendPing = useCallback((sessionId: string) => {
    ws.publish('/app/terminal/input', JSON.stringify({ sessionId, type: 'PING' }))
  }, [ws.publish])

  const closeSession = useCallback((sessionId: string) => {
    ws.publish('/app/terminal/close', JSON.stringify({ sessionId }))
  }, [ws.publish])

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
    isConnected: ws.isConnected,
    createSession,
    sendInput,
    sendResize,
    sendPing,
    closeSession,
    addOutputListener,
    addSessionCreatedListener,
  }
}
