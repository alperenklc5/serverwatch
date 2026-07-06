import { useState, useEffect } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import { X, Loader2 } from 'lucide-react'
import type { AlertRule } from '../../types'
import { createRule, updateRule } from '../../api/alerts'
import { useToastStore } from '../../stores/toastStore'

interface AlertRuleFormProps {
  open:         boolean
  onOpenChange: (open: boolean) => void
  rule?:        AlertRule | null   // null = create, set = edit
  onSaved:      () => void
}

const METRIC_TYPES = [
  { value: 'CPU_USAGE',        label: 'CPU Usage'          },
  { value: 'MEMORY_USAGE',     label: 'Memory Usage'       },
  { value: 'DISK_USAGE',       label: 'Disk Usage'         },
  { value: 'SWAP_USAGE',       label: 'Swap Usage'         },
  { value: 'CONTAINER_CPU',    label: 'Container CPU'      },
  { value: 'CONTAINER_MEMORY', label: 'Container Memory'   },
]

const OPERATORS = [
  { value: 'GT',  label: '> (greater than)'         },
  { value: 'GTE', label: '≥ (greater than or equal)' },
  { value: 'LT',  label: '< (less than)'            },
  { value: 'LTE', label: '≤ (less than or equal)'   },
  { value: 'EQ',  label: '= (equal to)'             },
]

const CONTAINER_METRICS = ['CONTAINER_CPU', 'CONTAINER_MEMORY']

function isWebhookUrl(url: string): string {
  if (url.includes('discord.com')) return 'Discord webhook'
  if (url.includes('hooks.slack.com')) return 'Slack webhook'
  if (url) return 'Generic webhook'
  return ''
}

type FormState = {
  name:             string
  metricType:       string
  operator:         string
  threshold:        string
  containerName:    string
  cooldownMinutes:  string
  notifyEmail:      boolean
  emailRecipients:  string
  notifyWebhook:    boolean
  webhookUrl:       string
  enabled:          boolean
}

const DEFAULTS: FormState = {
  name:            '',
  metricType:      'CPU_USAGE',
  operator:        'GT',
  threshold:       '80',
  containerName:   '',
  cooldownMinutes: '5',
  notifyEmail:     false,
  emailRecipients: '',
  notifyWebhook:   false,
  webhookUrl:      '',
  enabled:         true,
}

function Toggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="flex items-center gap-2 cursor-pointer select-none">
      <button
        type="button"
        onClick={() => onChange(!checked)}
        className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
          checked ? 'bg-accent-blue' : 'bg-border'
        }`}
      >
        <span className={`inline-block h-3.5 w-3.5 rounded-full bg-white shadow transition-transform ${
          checked ? 'translate-x-4.5' : 'translate-x-0.5'
        }`} />
      </button>
      <span className="text-sm text-text-primary">{label}</span>
    </label>
  )
}

export default function AlertRuleForm({ open, onOpenChange, rule, onSaved }: AlertRuleFormProps) {
  const [form, setForm]       = useState<FormState>(DEFAULTS)
  const [errors, setErrors]   = useState<Partial<Record<keyof FormState, string>>>({})
  const [loading, setLoading] = useState(false)

  const isEdit = !!rule

  useEffect(() => {
    if (rule) {
      setForm({
        name:            rule.name,
        metricType:      rule.metricType,
        operator:        rule.operator,
        threshold:       String(rule.threshold),
        containerName:   rule.containerName ?? '',
        cooldownMinutes: String(rule.cooldownMinutes),
        notifyEmail:     rule.notifyEmail,
        emailRecipients: rule.emailRecipients ?? '',
        notifyWebhook:   rule.notifyWebhook,
        webhookUrl:      rule.webhookUrl ?? '',
        enabled:         rule.enabled,
      })
    } else {
      setForm(DEFAULTS)
    }
    setErrors({})
  }, [rule, open])

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm(f => ({ ...f, [key]: value }))
    setErrors(e => ({ ...e, [key]: undefined }))
  }

  function validate(): boolean {
    const newErrors: Partial<Record<keyof FormState, string>> = {}
    if (!form.name.trim())                     newErrors.name = 'Name is required'
    const t = Number(form.threshold)
    if (isNaN(t) || t <= 0)                    newErrors.threshold = 'Must be a positive number'
    const c = Number(form.cooldownMinutes)
    if (isNaN(c) || c < 1)                     newErrors.cooldownMinutes = 'Minimum 1 minute'
    if (form.notifyEmail && !form.emailRecipients.trim())
      newErrors.emailRecipients = 'Enter at least one email address'
    if (form.notifyWebhook && !form.webhookUrl.trim())
      newErrors.webhookUrl = 'Webhook URL is required'
    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  async function handleSubmit() {
    if (!validate()) return
    setLoading(true)
    const payload = {
      name:            form.name.trim(),
      metricType:      form.metricType,
      operator:        form.operator,
      threshold:       Number(form.threshold),
      containerName:   CONTAINER_METRICS.includes(form.metricType) ? form.containerName.trim() || undefined : undefined,
      cooldownMinutes: Number(form.cooldownMinutes),
      notifyEmail:     form.notifyEmail,
      emailRecipients: form.notifyEmail ? form.emailRecipients.trim() : undefined,
      notifyWebhook:   form.notifyWebhook,
      webhookUrl:      form.notifyWebhook ? form.webhookUrl.trim() : undefined,
      enabled:         form.enabled,
    }
    try {
      if (isEdit && rule) {
        await updateRule(rule.id, payload)
        useToastStore.getState().addToast('success', 'Rule updated')
      } else {
        await createRule(payload)
        useToastStore.getState().addToast('success', 'Rule created')
      }
      onSaved()
      onOpenChange(false)
    } catch {
      useToastStore.getState().addToast('error', isEdit ? 'Failed to update rule' : 'Failed to create rule')
    } finally {
      setLoading(false)
    }
  }

  const inputCls = (err?: string) =>
    `w-full px-3 py-2 bg-bg-primary border rounded-lg text-sm text-text-primary placeholder:text-text-tertiary focus:outline-none transition-colors ${
      err ? 'border-accent-red focus:border-accent-red' : 'border-border focus:border-accent-blue'
    }`

  const selectCls = 'w-full px-3 py-2 bg-bg-primary border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-accent-blue transition-colors'

  return (
    <Dialog.Root open={open} onOpenChange={v => { if (!v) setErrors({}); onOpenChange(v) }}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-black/60 z-50 backdrop-blur-sm" />
        <Dialog.Content className="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-50 bg-bg-secondary border border-border rounded-xl shadow-2xl w-full max-w-lg p-6 focus:outline-none max-h-[90vh] overflow-y-auto">
          <div className="flex items-center justify-between mb-5">
            <Dialog.Title className="text-base font-semibold text-text-primary">
              {isEdit ? 'Edit Rule' : 'Create Alert Rule'}
            </Dialog.Title>
            <Dialog.Close asChild>
              <button className="p-1.5 rounded text-text-tertiary hover:text-text-primary hover:bg-bg-tertiary transition-colors">
                <X className="w-4 h-4" />
              </button>
            </Dialog.Close>
          </div>
          <Dialog.Description className="sr-only">
            {isEdit ? 'Edit an existing alert rule' : 'Create a new alert rule'}
          </Dialog.Description>

          <div className="space-y-4">
            {/* Name */}
            <div>
              <label className="text-xs text-text-secondary block mb-1.5">Name *</label>
              <input
                autoFocus
                value={form.name}
                onChange={e => set('name', e.target.value)}
                placeholder="High CPU Warning"
                className={inputCls(errors.name)}
              />
              {errors.name && <p className="text-xs text-accent-red mt-1">{errors.name}</p>}
            </div>

            {/* Metric + Operator + Threshold */}
            <div className="grid grid-cols-3 gap-3">
              <div>
                <label className="text-xs text-text-secondary block mb-1.5">Metric *</label>
                <select value={form.metricType} onChange={e => set('metricType', e.target.value)} className={selectCls}>
                  {METRIC_TYPES.map(m => (
                    <option key={m.value} value={m.value}>{m.label}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="text-xs text-text-secondary block mb-1.5">Operator *</label>
                <select value={form.operator} onChange={e => set('operator', e.target.value)} className={selectCls}>
                  {OPERATORS.map(o => (
                    <option key={o.value} value={o.value}>{o.label}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="text-xs text-text-secondary block mb-1.5">Threshold *</label>
                <div className="relative">
                  <input
                    type="number"
                    value={form.threshold}
                    onChange={e => set('threshold', e.target.value)}
                    min={0}
                    className={`${inputCls(errors.threshold)} pr-6`}
                  />
                  <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-text-tertiary">%</span>
                </div>
                {errors.threshold && <p className="text-xs text-accent-red mt-1">{errors.threshold}</p>}
              </div>
            </div>

            {/* Container name (conditional) */}
            {CONTAINER_METRICS.includes(form.metricType) && (
              <div>
                <label className="text-xs text-text-secondary block mb-1.5">Container Name</label>
                <input
                  value={form.containerName}
                  onChange={e => set('containerName', e.target.value)}
                  placeholder="my-container (leave blank for all)"
                  className={inputCls()}
                />
              </div>
            )}

            {/* Cooldown */}
            <div>
              <label className="text-xs text-text-secondary block mb-1.5">Cooldown *</label>
              <div className="relative">
                <input
                  type="number"
                  value={form.cooldownMinutes}
                  onChange={e => set('cooldownMinutes', e.target.value)}
                  min={1}
                  className={`${inputCls(errors.cooldownMinutes)} pr-16`}
                />
                <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-text-tertiary">minutes</span>
              </div>
              {errors.cooldownMinutes && <p className="text-xs text-accent-red mt-1">{errors.cooldownMinutes}</p>}
            </div>

            {/* Email notification */}
            <div className="space-y-2">
              <Toggle
                label="Email Notifications"
                checked={form.notifyEmail}
                onChange={v => set('notifyEmail', v)}
              />
              {form.notifyEmail && (
                <div>
                  <input
                    value={form.emailRecipients}
                    onChange={e => set('emailRecipients', e.target.value)}
                    placeholder="user@example.com, another@example.com"
                    className={inputCls(errors.emailRecipients)}
                  />
                  {errors.emailRecipients && <p className="text-xs text-accent-red mt-1">{errors.emailRecipients}</p>}
                </div>
              )}
            </div>

            {/* Webhook notification */}
            <div className="space-y-2">
              <Toggle
                label="Webhook Notifications"
                checked={form.notifyWebhook}
                onChange={v => set('notifyWebhook', v)}
              />
              {form.notifyWebhook && (
                <div>
                  <input
                    value={form.webhookUrl}
                    onChange={e => set('webhookUrl', e.target.value)}
                    placeholder="https://discord.com/api/webhooks/..."
                    className={inputCls(errors.webhookUrl)}
                  />
                  {form.webhookUrl && (
                    <p className="text-[10px] text-text-tertiary mt-1">
                      {isWebhookUrl(form.webhookUrl)}
                    </p>
                  )}
                  {errors.webhookUrl && <p className="text-xs text-accent-red mt-1">{errors.webhookUrl}</p>}
                </div>
              )}
            </div>

            {/* Enabled */}
            <Toggle
              label="Rule Enabled"
              checked={form.enabled}
              onChange={v => set('enabled', v)}
            />
          </div>

          <div className="flex justify-end gap-3 mt-6">
            <Dialog.Close asChild>
              <button className="px-4 py-2 text-sm text-text-secondary border border-border rounded-lg hover:bg-bg-tertiary transition-colors">
                Cancel
              </button>
            </Dialog.Close>
            <button
              onClick={() => void handleSubmit()}
              disabled={loading}
              className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-accent-blue hover:bg-accent-blue/80 rounded-lg transition-colors disabled:opacity-50"
            >
              {loading && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
              {isEdit ? 'Save Changes' : 'Create Rule'}
            </button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
