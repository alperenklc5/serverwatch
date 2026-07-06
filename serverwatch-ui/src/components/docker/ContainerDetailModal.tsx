import { useEffect, useState } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import { X, Loader2 } from 'lucide-react'
import { inspectContainer } from '../../api/docker'
import type { ContainerInfo, ContainerStats as ContainerStatsDTO, InspectResponse } from '../../types'
import type { DockerSocketHook } from '../../hooks/useDockerSocket'
import { formatRelative } from '../../lib/formatters'
import { cn } from '../../lib/utils'
import ContainerStatsPanel from './ContainerStats'
import ContainerLogs from './ContainerLogs'

interface ContainerDetailModalProps {
  container: ContainerInfo | null
  statsHistory: ContainerStatsDTO[]
  socket: DockerSocketHook
  onClose: () => void
}

const TABS = ['Overview', 'Stats', 'Logs', 'Environment', 'Networking'] as const
type TabValue = typeof TABS[number]

function KV({ label, value, mono = false }: { label: string; value: string | number; mono?: boolean }) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-xs text-text-tertiary">{label}</span>
      <span className={cn('text-sm text-text-primary break-all', mono && 'font-mono')}>{value}</span>
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="space-y-3">
      <h4 className="text-xs font-semibold text-text-tertiary uppercase tracking-wider">{title}</h4>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 bg-bg-primary rounded-lg p-3">
        {children}
      </div>
    </div>
  )
}

function OverviewTab({ container, inspect }: { container: ContainerInfo; inspect: InspectResponse | null }) {
  const state = inspect?.State
  return (
    <div className="space-y-4">
      <Section title="Identity">
        <KV label="Name"         value={container.name} />
        <KV label="Image"        value={container.image} />
        <KV label="Container ID" value={container.containerIdFull} mono />
        <KV label="Created"      value={formatRelative(container.created)} />
      </Section>

      {state && (
        <Section title="State">
          <KV label="Status"    value={state.Status} />
          <KV label="Exit code" value={state.ExitCode} />
          <KV label="Started"   value={state.StartedAt ? formatRelative(state.StartedAt) : '—'} />
          <KV label="Finished"  value={
            state.FinishedAt && state.FinishedAt !== '0001-01-01T00:00:00Z'
              ? formatRelative(state.FinishedAt)
              : '—'
          } />
        </Section>
      )}

      {inspect?.HostConfig?.RestartPolicy && (
        <Section title="Config">
          <KV label="Restart policy" value={inspect.HostConfig.RestartPolicy.Name || 'no'} />
          {inspect.Config?.Cmd && (
            <div className="sm:col-span-2">
              <KV label="Command" value={inspect.Config.Cmd.join(' ')} mono />
            </div>
          )}
        </Section>
      )}

      {container.volumes.length > 0 && (
        <div className="space-y-3">
          <h4 className="text-xs font-semibold text-text-tertiary uppercase tracking-wider">Volumes</h4>
          <div className="bg-bg-primary rounded-lg p-3 space-y-2">
            {inspect?.Mounts.map((m, i) => (
              <div key={i} className="text-xs font-mono text-text-secondary">
                <span className="text-text-tertiary">{m.Type}</span>{' '}
                <span className="text-accent-blue">{m.Source}</span>
                {' → '}
                <span className="text-text-primary">{m.Destination}</span>
                {m.Mode && <span className="text-text-tertiary"> ({m.Mode})</span>}
              </div>
            )) ?? container.volumes.map((v, i) => (
              <div key={i} className="text-xs font-mono text-text-secondary">{v}</div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function EnvTab({ inspect }: { inspect: InspectResponse | null }) {
  const env = inspect?.Config?.Env ?? []
  if (env.length === 0) {
    return <p className="text-sm text-text-tertiary py-4">No environment variables.</p>
  }
  return (
    <div className="bg-bg-primary rounded-lg p-3 space-y-1.5">
      {env.map((line, i) => {
        const eq  = line.indexOf('=')
        const key = eq >= 0 ? line.slice(0, eq) : line
        const val = eq >= 0 ? line.slice(eq + 1) : ''
        const sensitive = /password|secret|key|token/i.test(key)
        return (
          <div key={i} className="flex gap-2 text-xs font-mono">
            <span className="text-accent-blue flex-shrink-0">{key}</span>
            <span className="text-text-tertiary">=</span>
            <span className={cn(
              'text-text-secondary break-all',
              sensitive && 'blur-sm hover:blur-none transition-all cursor-pointer',
            )}>
              {val || '""'}
            </span>
          </div>
        )
      })}
    </div>
  )
}

function NetworkingTab({ container, inspect }: { container: ContainerInfo; inspect: InspectResponse | null }) {
  const nets  = inspect?.NetworkSettings?.Networks ?? {}
  const ports = inspect?.NetworkSettings?.Ports ?? {}

  return (
    <div className="space-y-4">
      {Object.entries(nets).length > 0 && (
        <div className="space-y-3">
          <h4 className="text-xs font-semibold text-text-tertiary uppercase tracking-wider">Networks</h4>
          {Object.entries(nets).map(([name, net]) => (
            <div key={name} className="bg-bg-primary rounded-lg p-3 grid grid-cols-2 gap-3">
              <KV label="Network"    value={name} />
              <KV label="IP Address" value={net.IPAddress || '—'} mono />
              <KV label="Gateway"    value={net.Gateway || '—'} mono />
              <KV label="MAC"        value={net.MacAddress || '—'} mono />
            </div>
          ))}
        </div>
      )}

      {Object.entries(ports).length > 0 && (
        <div className="space-y-3">
          <h4 className="text-xs font-semibold text-text-tertiary uppercase tracking-wider">Port Bindings</h4>
          <div className="bg-bg-primary rounded-lg p-3 space-y-2">
            {Object.entries(ports).map(([containerPort, bindings]) => (
              <div key={containerPort} className="flex gap-3 text-xs font-mono">
                <span className="text-accent-blue">{containerPort}</span>
                <span className="text-text-tertiary">→</span>
                <span className="text-text-primary">
                  {bindings?.map(b => `${b.HostIp}:${b.HostPort}`).join(', ') ?? 'not published'}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {Object.entries(nets).length === 0 && Object.entries(ports).length === 0 && (
        <p className="text-sm text-text-tertiary py-4">
          No network info available.
          {container.networks.length > 0 && ` Networks: ${container.networks.join(', ')}`}
        </p>
      )}
    </div>
  )
}

export default function ContainerDetailModal({
  container, statsHistory, socket, onClose,
}: ContainerDetailModalProps) {
  const [inspect, setInspect]       = useState<InspectResponse | null>(null)
  const [inspecting, setInspecting] = useState(false)
  const [activeTab, setActiveTab]   = useState<TabValue>('Overview')

  useEffect(() => {
    if (!container) return
    setInspect(null)
    setActiveTab('Overview')
    setInspecting(true)
    inspectContainer(container.containerId)
      .then(setInspect)
      .catch(() => { /* degrades gracefully */ })
      .finally(() => setInspecting(false))
  }, [container])

  return (
    <Dialog.Root open={container !== null} onOpenChange={v => { if (!v) onClose() }}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-black/60 z-50 backdrop-blur-sm" />
        <Dialog.Content
          className={cn(
            'fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-50',
            'bg-bg-secondary border border-border rounded-xl shadow-2xl',
            'w-full max-w-2xl max-h-[85vh] flex flex-col focus:outline-none',
          )}
        >
          {/* Header */}
          <div className="flex items-center justify-between px-5 py-4 border-b border-border flex-shrink-0">
            <div>
              <Dialog.Title className="text-base font-semibold text-text-primary">
                {container?.name ?? ''}
              </Dialog.Title>
              <Dialog.Description className="text-xs text-text-secondary mt-0.5">
                {container?.image ?? ''}
              </Dialog.Description>
            </div>
            <div className="flex items-center gap-2">
              {inspecting && <Loader2 className="w-4 h-4 animate-spin text-text-tertiary" />}
              <Dialog.Close asChild>
                <button className="p-1.5 rounded-lg text-text-tertiary hover:text-text-primary hover:bg-bg-tertiary transition-colors">
                  <X className="w-4 h-4" />
                </button>
              </Dialog.Close>
            </div>
          </div>

          {/* Tab bar */}
          <div className="flex gap-1 px-5 pt-3 border-b border-border flex-shrink-0">
            {TABS.map(tab => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                className={cn(
                  'px-3 py-2 text-sm transition-colors -mb-px border-b-2',
                  activeTab === tab
                    ? 'text-text-primary border-accent-blue'
                    : 'text-text-secondary border-transparent hover:text-text-primary',
                )}
              >
                {tab}
              </button>
            ))}
          </div>

          {/* Tab content */}
          <div className="flex-1 min-h-0 overflow-y-auto p-5">
            {activeTab === 'Overview' && container && (
              <OverviewTab container={container} inspect={inspect} />
            )}
            {activeTab === 'Stats' && (
              <ContainerStatsPanel history={statsHistory} />
            )}
            {activeTab === 'Logs' && container && (
              <div className="relative" style={{ minHeight: 400 }}>
                <ContainerLogs
                  containerId={container.containerId}
                  containerName={container.name}
                  socket={socket}
                />
              </div>
            )}
            {activeTab === 'Environment' && (
              <EnvTab inspect={inspect} />
            )}
            {activeTab === 'Networking' && container && (
              <NetworkingTab container={container} inspect={inspect} />
            )}
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
