import { useState, useEffect, useCallback } from 'react'
import type { AlertRule } from '../types'
import { listRules, deleteRule, toggleRule, testRule } from '../api/alerts'
import AlertRuleList  from '../components/alerts/AlertRuleList'
import AlertRuleForm  from '../components/alerts/AlertRuleForm'
import AlertLiveFeed  from '../components/alerts/AlertLiveFeed'
import AlertHistory   from '../components/alerts/AlertHistory'
import { useToastStore } from '../stores/toastStore'
import { useAlertStore } from '../stores/alertStore'

type Tab = 'rules' | 'feed' | 'history'

export default function AlertsPage() {
  const [tab, setTab]           = useState<Tab>('rules')
  const [rules, setRules]       = useState<AlertRule[]>([])
  const [loading, setLoading]   = useState(false)
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing]   = useState<AlertRule | null>(null)
  const unreadCount             = useAlertStore(s => s.unreadCount)
  const clearUnread             = useAlertStore(s => s.clearUnread)

  const loadRules = useCallback(async () => {
    setLoading(true)
    try {
      const data = await listRules()
      setRules(data)
    } catch {
      useToastStore.getState().addToast('error', 'Failed to load alert rules')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void loadRules() }, [loadRules])

  // Clear badge when visiting this page
  useEffect(() => { clearUnread() }, [clearUnread])

  async function handleDelete(id: number) {
    try {
      await deleteRule(id)
      useToastStore.getState().addToast('success', 'Rule deleted')
      void loadRules()
    } catch {
      useToastStore.getState().addToast('error', 'Failed to delete rule')
    }
  }

  async function handleToggle(id: number, enabled: boolean) {
    try {
      await toggleRule(id, enabled)
      setRules(rs => rs.map(r => r.id === id ? { ...r, enabled } : r))
    } catch {
      useToastStore.getState().addToast('error', 'Failed to toggle rule')
    }
  }

  async function handleTest(id: number) {
    try {
      await testRule(id)
      useToastStore.getState().addToast('success', 'Test notification sent')
    } catch {
      useToastStore.getState().addToast('error', 'Failed to send test notification')
    }
  }

  function openCreate() {
    setEditing(null)
    setFormOpen(true)
  }

  function openEdit(rule: AlertRule) {
    setEditing(rule)
    setFormOpen(true)
  }

  const TABS: { id: Tab; label: string; badge?: number }[] = [
    { id: 'rules',   label: 'Rules'     },
    { id: 'feed',    label: 'Live Feed', badge: tab !== 'feed' ? unreadCount : undefined },
    { id: 'history', label: 'History'   },
  ]

  return (
    <div className="space-y-5">
      {/* Tabs */}
      <div className="flex items-center gap-1 border-b border-border">
        {TABS.map(t => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={`relative flex items-center gap-2 px-4 py-2.5 text-sm font-medium transition-colors border-b-2 -mb-px ${
              tab === t.id
                ? 'text-text-primary border-accent-blue'
                : 'text-text-secondary border-transparent hover:text-text-primary hover:border-border'
            }`}
          >
            {t.label}
            {t.badge != null && t.badge > 0 && (
              <span className="flex items-center justify-center w-4 h-4 text-[10px] font-semibold text-white bg-accent-red rounded-full">
                {t.badge > 9 ? '9+' : t.badge}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Tab content */}
      {tab === 'rules' && (
        <AlertRuleList
          rules={rules}
          loading={loading}
          onEdit={openEdit}
          onDelete={id => void handleDelete(id)}
          onToggle={(id, enabled) => void handleToggle(id, enabled)}
          onTest={id => void handleTest(id)}
          onCreate={openCreate}
        />
      )}

      {tab === 'feed' && <AlertLiveFeed />}

      {tab === 'history' && <AlertHistory />}

      {/* Rule form modal */}
      <AlertRuleForm
        open={formOpen}
        onOpenChange={setFormOpen}
        rule={editing}
        onSaved={() => void loadRules()}
      />
    </div>
  )
}
