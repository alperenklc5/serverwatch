import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { RefreshCw, Search, Eye, EyeOff, Wifi, WifiOff } from 'lucide-react'
import {
  listContainers, getDockerInfo,
  startContainer, stopContainer, restartContainer, removeContainer,
} from '../api/docker'
import { useDockerSocket } from '../hooks/useDockerSocket'
import { useToast, useToastStore } from '../stores/toastStore'
import type { ContainerInfo, ContainerStats, DockerInfoDTO } from '../types'
import ContainerCard from '../components/docker/ContainerCard'
import DockerOverview from '../components/docker/DockerOverview'
import ContainerDetailModal from '../components/docker/ContainerDetailModal'
import ConfirmDialog from '../components/ui/ConfirmDialog'

type ActionKind = 'stop' | 'restart' | 'remove'

interface PendingAction {
  containerId: string
  kind: ActionKind
}

export default function ContainersPage() {
  const [containers, setContainers] = useState<ContainerInfo[]>([])
  const [dockerInfo, setDockerInfo] = useState<DockerInfoDTO | null>(null)
  const [loading, setLoading]       = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [showAll, setShowAll]       = useState(false)
  const [filter, setFilter]         = useState('')
  const [actionLoading, setActionLoading] = useState<Record<string, string>>({})

  // Detail modal
  const [selectedContainer, setSelectedContainer] = useState<ContainerInfo | null>(null)
  // Per-container stats history for detail modal (keyed by containerId)
  const statsHistoryRef = useRef<Record<string, ContainerStats[]>>({})

  // Confirm dialog
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null)

  const socket = useDockerSocket()
  const toast  = useToast()

  // ── data loading ──────────────────────────────────────────────────────────
  const load = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true)
    else setLoading(true)
    try {
      const [list, info] = await Promise.all([
        listContainers(showAll),
        getDockerInfo(),
      ])
      setContainers(list)
      setDockerInfo(info)
    } catch {
      // Use getState() so this callback doesn't depend on the toast object
      // (useToast() returns a new object every render, which would cause load
      // to be recreated on every render and re-trigger the useEffect below,
      // flooding the user with repeated error toasts on a persistent failure)
      if (isRefresh) {
        useToastStore.getState().addToast('error', 'Failed to load containers')
      } else {
        // Initial load failure: show once, then set containers to empty so the
        // page renders the empty state rather than staying in the loading skeleton
        useToastStore.getState().addToast('error', 'Failed to load containers')
        setContainers([])
        setDockerInfo(null)
      }
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [showAll])  // toast deliberately excluded — accessed via getState() above

  useEffect(() => { void load() }, [load])

  // ── stats accumulation ────────────────────────────────────────────────────
  useEffect(() => {
    const latest = socket.statsMap
    Object.values(latest).forEach(s => {
      const hist = statsHistoryRef.current[s.containerId] ?? []
      statsHistoryRef.current[s.containerId] = [...hist.slice(-119), s]
    })
  }, [socket.statsMap])

  // ── derived ───────────────────────────────────────────────────────────────
  const filtered = useMemo(() => {
    const q = filter.toLowerCase()
    return q
      ? containers.filter(c =>
          c.name.toLowerCase().includes(q) || c.image.toLowerCase().includes(q),
        )
      : containers
  }, [containers, filter])

  const totalCpu    = useMemo(
    () => Object.values(socket.statsMap).reduce((sum, s) => sum + s.cpuPercent, 0),
    [socket.statsMap],
  )
  const totalMemory = useMemo(
    () => Object.values(socket.statsMap).reduce((sum, s) => sum + s.memoryUsageBytes, 0),
    [socket.statsMap],
  )

  // ── actions ───────────────────────────────────────────────────────────────
  const setLoaderFor = useCallback((id: string, kind: string) => {
    setActionLoading(prev => ({ ...prev, [id]: kind }))
  }, [])
  const clearLoaderFor = useCallback((id: string) => {
    setActionLoading(prev => { const n = { ...prev }; delete n[id]; return n })
  }, [])

  const handleStart = useCallback(async (c: ContainerInfo) => {
    setLoaderFor(c.containerId, 'start')
    try {
      await startContainer(c.containerId)
      toast.success(`Started ${c.name}`)
      await load(true)
    } catch {
      toast.error(`Failed to start ${c.name}`)
    } finally {
      clearLoaderFor(c.containerId)
    }
  }, [load, toast, setLoaderFor, clearLoaderFor])

  const handleStop = useCallback((c: ContainerInfo) => {
    setPendingAction({ containerId: c.containerId, kind: 'stop' })
  }, [])

  const handleRestart = useCallback((c: ContainerInfo) => {
    setPendingAction({ containerId: c.containerId, kind: 'restart' })
  }, [])

  const handleRemove = useCallback((c: ContainerInfo) => {
    setPendingAction({ containerId: c.containerId, kind: 'remove' })
  }, [])

  const confirmAction = useCallback(async () => {
    if (!pendingAction) return
    const { containerId, kind } = pendingAction
    const c = containers.find(x => x.containerId === containerId)
    if (!c) return
    setPendingAction(null)
    setLoaderFor(containerId, kind)
    try {
      if (kind === 'stop')    await stopContainer(containerId)
      if (kind === 'restart') await restartContainer(containerId)
      if (kind === 'remove')  await removeContainer(containerId)
      toast.success(`${kind.charAt(0).toUpperCase() + kind.slice(1)}ed ${c.name}`)
      await load(true)
    } catch {
      toast.error(`Failed to ${kind} ${c.name}`)
    } finally {
      clearLoaderFor(containerId)
    }
  }, [pendingAction, containers, load, toast, setLoaderFor, clearLoaderFor])

  // ── confirm dialog meta ───────────────────────────────────────────────────
  const pendingContainer = pendingAction
    ? containers.find(c => c.containerId === pendingAction.containerId)
    : null

  const confirmMeta = useMemo(() => {
    if (!pendingAction || !pendingContainer) return null
    const { kind } = pendingAction
    const name = pendingContainer.name
    if (kind === 'stop')    return { title: `Stop ${name}?`, description: `Container ${name} will be stopped.`, label: 'Stop', variant: 'warning' as const }
    if (kind === 'restart') return { title: `Restart ${name}?`, description: `Container ${name} will be restarted.`, label: 'Restart', variant: 'warning' as const }
    return { title: `Remove ${name}?`, description: `Container ${name} will be permanently removed.`, label: 'Remove', variant: 'danger' as const }
  }, [pendingAction, pendingContainer])

  // ── render ────────────────────────────────────────────────────────────────
  return (
    <div className="space-y-5">
      {/* Page header */}
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div>
          <h1 className="text-2xl font-semibold text-text-primary">Containers</h1>
          <p className="text-sm text-text-secondary mt-0.5">
            {containers.length} container{containers.length !== 1 ? 's' : ''}
            {!showAll && ' (running)'}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {socket.isConnected
            ? <span title="Live stats connected"><Wifi className="w-4 h-4 text-accent-green" /></span>
            : <span title="Stats disconnected"><WifiOff className="w-4 h-4 text-text-tertiary" /></span>
          }
          <button
            onClick={() => void load(true)}
            disabled={refreshing}
            className="flex items-center gap-2 px-3 py-2 text-sm border border-border rounded-lg text-text-secondary hover:text-text-primary hover:bg-bg-tertiary transition-colors disabled:opacity-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${refreshing ? 'animate-spin' : ''}`} />
            Refresh
          </button>
          <button
            onClick={() => setShowAll(v => !v)}
            className="flex items-center gap-2 px-3 py-2 text-sm border border-border rounded-lg text-text-secondary hover:text-text-primary hover:bg-bg-tertiary transition-colors"
            title={showAll ? 'Showing all containers' : 'Showing only running containers'}
          >
            {showAll ? <Eye className="w-3.5 h-3.5" /> : <EyeOff className="w-3.5 h-3.5" />}
            {showAll ? 'All' : 'Running'}
          </button>
        </div>
      </div>

      {/* Summary bar */}
      <DockerOverview info={dockerInfo} totalCpu={totalCpu} totalMemory={totalMemory} />

      {/* Search */}
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-tertiary pointer-events-none" />
        <input
          type="text"
          placeholder="Filter by name or image…"
          value={filter}
          onChange={e => setFilter(e.target.value)}
          className="w-full pl-9 pr-4 py-2 bg-bg-secondary border border-border rounded-lg text-sm text-text-primary placeholder:text-text-tertiary focus:outline-none focus:border-accent-blue transition-colors"
        />
      </div>

      {/* Grid */}
      {loading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="h-48 rounded-xl bg-bg-secondary border border-border animate-pulse" />
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <div className="rounded-xl border border-border bg-bg-secondary p-12 text-center text-text-secondary">
          {filter ? `No containers match "${filter}"` : 'No containers found'}
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {filtered.map(c => (
            <ContainerCard
              key={c.containerId}
              container={c}
              stats={socket.statsMap[c.containerId]}
              actionLoading={actionLoading[c.containerId]}
              onStart={()    => void handleStart(c)}
              onStop={()     => handleStop(c)}
              onRestart={()  => handleRestart(c)}
              onRemove={()   => handleRemove(c)}
              onViewLogs={()  => { setSelectedContainer(c) }}
              onInspect={()  => setSelectedContainer(c)}
            />
          ))}
        </div>
      )}

      {/* Detail modal */}
      <ContainerDetailModal
        container={selectedContainer}
        statsHistory={selectedContainer ? (statsHistoryRef.current[selectedContainer.containerId] ?? []) : []}
        socket={socket}
        onClose={() => setSelectedContainer(null)}
      />

      {/* Confirm dialog */}
      {confirmMeta && (
        <ConfirmDialog
          open={pendingAction !== null}
          onOpenChange={open => { if (!open) setPendingAction(null) }}
          title={confirmMeta.title}
          description={confirmMeta.description}
          confirmLabel={confirmMeta.label}
          confirmVariant={confirmMeta.variant}
          onConfirm={() => void confirmAction()}
        />
      )}
    </div>
  )
}
