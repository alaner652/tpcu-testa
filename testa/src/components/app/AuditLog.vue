<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { watchDebounced } from '@vueuse/core'
import { ChevronDown, ChevronRight, Download, LoaderCircle, Search, X } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import ErrorNote from '@/components/ui/ErrorNote.vue'
import EmptyNote from '@/components/ui/EmptyNote.vue'
import Button from '@/components/ui/Button.vue'
import { api } from '@/lib/api'
import { ACTION_GROUPS, ACTION_LABEL, ACTOR_TYPE_LABEL, DETAIL_LABEL, isAlarming, type AuditEntry } from '@/lib/audit'

/**
 * Audit log (system administrators): search by person, unit or text, filter by action and time,
 * open an entry for every detail, export what is shown to CSV. Entries can never be changed.
 */
const props = defineProps<{
  staff: { id: number; name: string; email: string }[]
  units: { id: number; name: string }[]
}>()

const PAGE = 50
const items = ref<AuditEntry[]>([])
const total = ref(0)
const page = ref(0)
const loading = ref(false)
const error = ref('')
const open = ref<number | null>(null)

const q = ref('')
const action = ref('')
type Range = 'all' | 'today' | '7d' | '30d' | 'custom'
const range = ref<Range>('all')
const fromDate = ref('')
const toDate = ref('')
const RANGES: { value: Range; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: 'today', label: '今天' },
  { value: '7d', label: '7 天內' },
  { value: '30d', label: '30 天內' },
  { value: 'custom', label: '自訂' },
]

const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate())
/** The time window as instants; dates typed by the user are whole days in local time */
function timeWindow(): { from?: Date; to?: Date } {
  const today = startOfDay(new Date())
  const daysAgo = (n: number) => new Date(today.getFullYear(), today.getMonth(), today.getDate() - n)
  if (range.value === 'today') return { from: today }
  if (range.value === '7d') return { from: daysAgo(6) }
  if (range.value === '30d') return { from: daysAgo(29) }
  if (range.value === 'custom') {
    const parse = (s: string) => (s ? new Date(`${s}T00:00:00`) : undefined)
    const to = parse(toDate.value)
    return { from: parse(fromDate.value), to: to && new Date(to.getFullYear(), to.getMonth(), to.getDate() + 1) }
  }
  return {}
}

function query(p: number, size = PAGE) {
  const params = new URLSearchParams({ page: String(p), size: String(size) })
  if (action.value) params.set('action', action.value)
  if (q.value.trim()) params.set('q', q.value.trim())
  const w = timeWindow()
  if (w.from) params.set('from', w.from.toISOString())
  if (w.to) params.set('to', w.to.toISOString())
  return `/admin/audit-logs?${params}`
}

async function load(p = 0) {
  loading.value = true
  try {
    const res = await api<{ items: AuditEntry[]; total: number }>(query(p))
    items.value = p === 0 ? res.items : [...items.value, ...res.items]
    total.value = res.total
    page.value = p
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
onMounted(() => load())
watch([action, range, fromDate, toDate], () => load())
watchDebounced(q, () => load(), { debounce: 300 })

const filtered = computed(() => !!(q.value.trim() || action.value || range.value !== 'all'))
function clearFilters() {
  q.value = ''
  action.value = ''
  range.value = 'all'
  fromDate.value = ''
  toDate.value = ''
}

// ── Showing an entry ──
const staffName = (id: string | number | null) => props.staff.find((s) => String(s.id) === String(id))
const actor = (a: AuditEntry) => {
  if (a.actorType === 'STAFF' && a.actorId != null) {
    const s = staffName(a.actorId)
    if (s) return s.name
  }
  return a.actorLabel ?? '系統'
}
const target = (a: AuditEntry) => {
  if (a.targetType === 'staff') return staffName(a.targetId)?.name ?? `帳號 #${a.targetId}`
  return null
}
const unit = (a: AuditEntry) => (a.unitId ? (props.units.find((u) => u.id === a.unitId)?.name ?? `單位 #${a.unitId}`) : null)
const detail = (a: AuditEntry) => (a.detail ? (DETAIL_LABEL[a.detail] ?? a.detail) : null)
const summary = (a: AuditEntry) => [target(a), unit(a), detail(a), a.reason && `理由：${a.reason}`].filter(Boolean).join('，')

const time = (iso: string) => new Date(iso).toLocaleTimeString('zh-TW', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
const full = (iso: string) => new Date(iso).toLocaleString('zh-TW', { hour12: false })
const day = (iso: string) => new Date(iso).toLocaleDateString('zh-TW', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' })

/** Entries under a heading per day */
const days = computed(() => {
  const list: { day: string; entries: AuditEntry[] }[] = []
  for (const a of items.value) {
    const d = day(a.at)
    if (list.at(-1)?.day !== d) list.push({ day: d, entries: [] })
    list.at(-1)!.entries.push(a)
  }
  return list
})

const fields = (a: AuditEntry) =>
  [
    ['時間', full(a.at)],
    ['動作', ACTION_LABEL[a.action] ?? a.action],
    ['操作者', [actor(a), a.actorLabel && a.actorLabel !== actor(a) ? a.actorLabel : null].filter(Boolean).join('　')],
    ['身分', ACTOR_TYPE_LABEL[a.actorType] ?? a.actorType],
    ['對象', target(a) ?? (a.targetId ? `${a.targetType ?? ''} ${a.targetId}`.trim() : null)],
    ['單位', unit(a)],
    ['說明', detail(a)],
    ['理由', a.reason],
    ['IP 位址', a.ip],
    ['紀錄編號', `#${a.id}`],
  ].filter((f): f is [string, string] => !!f[1])

// ── Export ──
const MAX_EXPORT = 10000
const exporting = ref(false)
async function exportCsv() {
  exporting.value = true
  try {
    const rows: AuditEntry[] = []
    for (let p = 0; rows.length < Math.min(total.value, MAX_EXPORT); p++) {
      const res = await api<{ items: AuditEntry[] }>(query(p, 500))
      if (!res.items.length) break
      rows.push(...res.items)
    }
    const cell = (v: string | null | undefined) => `"${(v ?? '').replaceAll('"', '""')}"`
    const header = ['時間', '動作', '操作者', '帳號', '身分', '對象', '單位', '說明', '理由', 'IP 位址']
    const lines = rows
      .slice(0, MAX_EXPORT)
      .map((a) =>
        [full(a.at), ACTION_LABEL[a.action] ?? a.action, actor(a), a.actorLabel, ACTOR_TYPE_LABEL[a.actorType] ?? a.actorType, target(a) ?? a.targetId, unit(a), detail(a), a.reason, a.ip]
          .map(cell)
          .join(','),
      )
    // BOM so Excel opens it as UTF-8
    const blob = new Blob(['﻿' + [header.map(cell).join(','), ...lines].join('\r\n')], { type: 'text/csv;charset=utf-8' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    const stamp = new Date().toLocaleDateString('sv').replaceAll('-', '')
    link.download = `操作紀錄-${stamp}.csv`
    link.click()
    URL.revokeObjectURL(link.href)
    toast.success(`已匯出 ${lines.length} 筆紀錄`, total.value > MAX_EXPORT ? { description: `一次最多 ${MAX_EXPORT} 筆，請縮小時間範圍分次匯出。` } : undefined)
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <section class="card">
    <!-- Filters -->
    <div class="space-y-3 px-5 py-4 sm:px-6">
      <div class="flex flex-wrap items-center gap-2">
        <label class="relative min-w-0 flex-1 basis-64">
          <Search class="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-ink-3" aria-hidden="true" />
          <input v-model="q" type="search" class="field h-10 pl-10" placeholder="搜尋姓名、Email 或准考證號" aria-label="搜尋操作紀錄" />
        </label>
        <select v-model="action" class="field h-10 w-full sm:w-52" aria-label="依動作篩選">
          <option value="">全部動作</option>
          <optgroup v-for="g in ACTION_GROUPS" :key="g.label" :label="g.label">
            <option v-for="(label, key) in g.actions" :key="key" :value="key">{{ label }}</option>
          </optgroup>
        </select>
      </div>
      <div class="flex flex-wrap items-center gap-1.5">
        <button
          v-for="r in RANGES"
          :key="r.value"
          type="button"
          class="h-8 rounded-full px-3 text-sm transition-colors"
          :class="range === r.value ? 'bg-brand-soft font-medium text-brand-strong' : 'text-ink-2 hover:bg-ink/[0.06]'"
          :aria-pressed="range === r.value"
          @click="range = r.value"
        >
          {{ r.label }}
        </button>
        <span v-if="range === 'custom'" class="flex flex-wrap items-center gap-1.5 text-sm text-ink-2">
          <input v-model="fromDate" type="date" class="field h-8 w-40 px-2.5 text-sm" aria-label="開始日期" />
          到
          <input v-model="toDate" type="date" class="field h-8 w-40 px-2.5 text-sm" aria-label="結束日期" />
        </span>
      </div>
    </div>

    <div class="flex flex-wrap items-center gap-x-3 gap-y-2 border-t border-line px-5 py-3 text-sm text-ink-2 sm:px-6">
      <span class="min-w-0 flex-1">
        <LoaderCircle v-if="loading" class="mr-1 inline size-4 animate-spin align-[-3px]" />
        {{ filtered ? `符合 ${total} 筆` : `共 ${total} 筆` }}。紀錄只會增加，任何人都不能修改或刪除。
      </span>
      <button v-if="filtered" type="button" class="inline-flex items-center gap-1 font-medium text-brand hover:underline" @click="clearFilters">
        <X class="size-4" />清除條件
      </button>
      <Button size="md" variant="outline" :disabled="!total || exporting" @click="exportCsv">
        <LoaderCircle v-if="exporting" class="animate-spin" /><Download v-else />匯出 CSV
      </Button>
    </div>

    <ErrorNote v-if="error" class="border-t border-line" :message="error" :retry="() => load()" />
    <EmptyNote v-else-if="!loading && !items.length" line>
      {{ filtered ? '沒有符合條件的紀錄。' : '還沒有任何紀錄。' }}
    </EmptyNote>

    <template v-for="d in days" :key="d.day">
      <h3 class="px-5 pt-5 pb-2 text-[13px] font-semibold text-ink-3 sm:px-6">{{ d.day }}</h3>
      <ul class="divide-y divide-line border-t border-line">
        <li v-for="a in d.entries" :key="a.id">
          <button
            type="button"
            class="flex w-full items-center gap-4 px-5 py-3 text-left text-[15px] hover:bg-ink/[0.03] sm:px-6"
            :aria-expanded="open === a.id"
            @click="open = open === a.id ? null : a.id"
          >
            <span class="flex min-w-0 flex-1 flex-wrap items-baseline gap-x-4 gap-y-0.5">
              <span class="w-16 shrink-0 text-sm text-ink-2 tabular-nums">{{ time(a.at) }}</span>
              <span class="w-28 shrink-0 font-medium" :class="isAlarming(a.action) && 'text-grade'">{{ ACTION_LABEL[a.action] ?? a.action }}</span>
              <span class="min-w-0 flex-1 basis-40 truncate">
                {{ actor(a) }}<span v-if="a.actorType === 'CANDIDATE'" class="text-ink-3">（考生）</span>
              </span>
              <span v-if="summary(a)" class="min-w-0 flex-1 basis-48 truncate text-sm text-ink-2">{{ summary(a) }}</span>
            </span>
            <ChevronRight class="size-4 shrink-0 self-center text-ink-3 transition-transform" :class="open === a.id && 'rotate-90'" />
          </button>
          <dl v-if="open === a.id" class="grid gap-x-6 gap-y-1.5 bg-sunken/60 px-5 py-4 text-sm sm:grid-cols-[6rem_1fr] sm:px-6">
            <template v-for="[k, v] in fields(a)" :key="k">
              <dt class="text-ink-3">{{ k }}</dt>
              <dd class="min-w-0 break-words max-sm:mb-1">{{ v }}</dd>
            </template>
          </dl>
        </li>
      </ul>
    </template>

    <div v-if="items.length < total" class="border-t border-line px-5 py-3 text-center sm:px-6">
      <Button variant="outline" :disabled="loading" @click="load(page + 1)"><ChevronDown />載入更早的紀錄</Button>
    </div>
  </section>
</template>
