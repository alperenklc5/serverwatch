import { useEffect, useRef, useState, useCallback } from 'react'
import { Download, Copy, Trash2, ChevronsDown, Loader2 } from 'lucide-react'
import { getContainerLogs } from '../../api/docker'
import type { DockerSocketHook } from '../../hooks/useDockerSocket'
import { cn } from '../../lib/utils'

interface ContainerLogsProps {
  containerId: string
  containerName: string
  socket: DockerSocketHook
}

const TAIL_OPTIONS = [50, 100, 200, 500] as const
type TailOption = typeof TAIL_OPTIONS[number]

function ansiStrip(line: string): string {
  // eslint-disable-next-line no-control-regex
  return line.replace(/\x1b\[[0-9;]*m/g, '')
}

export default function ContainerLogs({
  containerId, containerName, socket,
}: ContainerLogsProps) {
  const [lines, setLines] = useState<string[]>([])
  const [tail, setTail] = useState<TailOption>(100)
  const [isLoading, setIsLoading] = useState(false)
  const [autoScroll, setAutoScroll] = useState(true)
  const [streaming, setStreaming] = useState(false)

  const bottomRef   = useRef<HTMLDivElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const unsubRef    = useRef<(() => void) | null>(null)

  // Fetch initial logs via REST
  const fetchLogs = useCallback(async (tailCount: TailOption) => {
    setIsLoading(true)
    try {
      const dto = await getContainerLogs(containerId, tailCount)
      setLines(dto.lines.map(ansiStrip))
    } catch {
      setLines(['[Error fetching logs]'])
    } finally {
      setIsLoading(false)
    }
  }, [containerId])

  useEffect(() => {
    void fetchLogs(tail)
  }, [fetchLogs, tail])

  // Start WebSocket log streaming
  useEffect(() => {
    if (!socket.isConnected) return

    socket.publish('/app/container/logs/start', { containerId })
    setStreaming(true)

    const unsub = socket.subscribeQueue('/user/queue/container-logs', (body) => {
      try {
        const msg = JSON.parse(body) as { containerId: string; line: string }
        if (msg.containerId !== containerId) return
        setLines(prev => [...prev.slice(-2000), ansiStrip(msg.line)])
      } catch { /* ignore */ }
    })

    unsubRef.current = unsub

    return () => {
      unsubRef.current?.()
      unsubRef.current = null
      setStreaming(false)
      if (socket.isConnected) {
        socket.publish('/app/container/logs/stop', { containerId })
      }
    }
  }, [containerId, socket, socket.isConnected])

  // Auto-scroll
  useEffect(() => {
    if (autoScroll) {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [lines, autoScroll])

  // Detect manual scroll up → disable auto-scroll
  const handleScroll = useCallback(() => {
    const el = containerRef.current
    if (!el) return
    const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 40
    setAutoScroll(atBottom)
  }, [])

  const handleCopy = useCallback(() => {
    void navigator.clipboard.writeText(lines.join('\n'))
  }, [lines])

  const handleDownload = useCallback(() => {
    const blob = new Blob([lines.join('\n')], { type: 'text/plain' })
    const url  = URL.createObjectURL(blob)
    const a    = document.createElement('a')
    a.href     = url
    a.download = `${containerName}-logs.txt`
    a.click()
    URL.revokeObjectURL(url)
  }, [lines, containerName])

  const handleClear = useCallback(() => setLines([]), [])

  const scrollToBottom = useCallback(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
    setAutoScroll(true)
  }, [])

  return (
    <div className="flex flex-col h-full gap-2">
      {/* Toolbar */}
      <div className="flex items-center gap-2 flex-wrap">
        <div className="flex items-center gap-1.5">
          <span className="text-xs text-text-secondary">Tail:</span>
          <select
            value={tail}
            onChange={e => setTail(Number(e.target.value) as TailOption)}
            className="text-xs bg-bg-primary border border-border rounded px-2 py-1 text-text-primary focus:outline-none focus:border-accent-blue"
          >
            {TAIL_OPTIONS.map(n => (
              <option key={n} value={n}>{n} lines</option>
            ))}
          </select>
        </div>

        <div className="flex items-center gap-1 ml-auto">
          {streaming && (
            <span className="flex items-center gap-1 text-xs text-accent-green mr-2">
              <span className="w-1.5 h-1.5 rounded-full bg-accent-green animate-pulse" />
              Live
            </span>
          )}
          <button
            onClick={handleCopy}
            title="Copy logs"
            className="p-1.5 rounded text-text-tertiary hover:text-text-primary hover:bg-bg-tertiary transition-colors"
          >
            <Copy className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={handleDownload}
            title="Download logs"
            className="p-1.5 rounded text-text-tertiary hover:text-text-primary hover:bg-bg-tertiary transition-colors"
          >
            <Download className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={handleClear}
            title="Clear"
            className="p-1.5 rounded text-text-tertiary hover:text-accent-red hover:bg-accent-red/10 transition-colors"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Log window */}
      <div
        ref={containerRef}
        onScroll={handleScroll}
        className="relative flex-1 min-h-0 bg-bg-primary rounded-lg border border-border overflow-y-auto font-mono text-xs leading-5 p-3"
        style={{ minHeight: 300 }}
      >
        {isLoading ? (
          <div className="flex items-center justify-center h-32 gap-2 text-text-tertiary">
            <Loader2 className="w-4 h-4 animate-spin" />
            <span>Loading logs…</span>
          </div>
        ) : lines.length === 0 ? (
          <div className="flex items-center justify-center h-32 text-text-tertiary">
            No log output
          </div>
        ) : (
          lines.map((line, i) => (
            <div
              key={i}
              className={cn(
                'whitespace-pre-wrap break-all',
                line.toLowerCase().includes('error') || line.toLowerCase().includes('err ')
                  ? 'text-accent-red'
                  : line.toLowerCase().includes('warn')
                    ? 'text-accent-amber'
                    : 'text-text-secondary',
              )}
            >
              {line || '\u00A0'}
            </div>
          ))
        )}
        <div ref={bottomRef} />
      </div>

      {/* Scroll-to-bottom FAB */}
      {!autoScroll && (
        <button
          onClick={scrollToBottom}
          className="absolute bottom-6 right-6 p-2 bg-accent-blue rounded-full shadow-lg hover:bg-accent-blue/80 transition-colors"
        >
          <ChevronsDown className="w-4 h-4 text-white" />
        </button>
      )}
    </div>
  )
}
