<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import { ArrowRight, CalendarClock, CircleCheck, Clock, DatabaseBackup, HardDrive, KeyRound, Lock, PenLine, RotateCw, ShieldAlert, Users, Wifi } from 'lucide-vue-next'
import { version as webVersion } from '../../../package.json'
import CardHeader from '@/components/ui/CardHeader.vue'
import ErrorNote from '@/components/ui/ErrorNote.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import { api } from '@/lib/api'
import { ACTION_LABEL, isAlarming, type AuditEntry } from '@/lib/audit'
import { backupHealth, bytes, diskLow, type BackupState } from '@/lib/backups'

/**
 * First admin tab: whether the system is healthy and what needs an administrator now.
 * Counts only; administrators never see exam content or scores.
 */
const props = defineProps<{ staff: { id: number; name: string }[] }>()
const emit = defineEmits<{ go: [tab: 'staff' | 'audit' | 'backups'] }>()

interface Overview {
  version: string
  startedAt: string
  databaseBytes: number
  disk: { free: number; total: number } | null
  counts: {
    staff: number
    units: number
    lockedStaff: number
    temporaryPasswords: number
    liveExams: number
    upcomingExams: number
    answering: number
    online: number
    failedLogins24h: number
  }
  recent: AuditEntry[]
}

const overview = ref<Overview | null>(null)
const backups = ref<BackupState | null>(null)
const error = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [o, b] = await Promise.all([api<Overview>('/admin/overview'), api<BackupState>('/admin/backups').catch(() => null)])
    overview.value = o
    backups.value = b
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
onMounted(load)

interface Alert {
  icon: Component
  tone: 'danger' | 'warn'
  text: string
  action?: { label: string; tab: 'staff' | 'audit' | 'backups' }
}
/** Things to act on, most serious first; empty when all is well */
const alerts = computed<Alert[]>(() => {
  const o = overview.value
  if (!o) return []
  const list: Alert[] = []
  const b = backups.value
  if (b) {
    const h = backupHealth(b)
    if (h.tone !== 'done') list.push({ icon: DatabaseBackup, tone: h.tone === 'danger' ? 'danger' : 'warn', text: `備份：${h.label}。${h.advice ?? ''}`, action: { label: '看備份', tab: 'backups' } })
    const offsite = !b.lastDownloadAt || Date.now() - new Date(b.lastDownloadAt).getTime() > 30 * 86400 * 1000
    if (b.items.length && offsite) list.push({ icon: DatabaseBackup, tone: 'warn', text: '超過一個月沒有下載備份存到別處。', action: { label: '下載備份', tab: 'backups' } })
  } else if (o.disk && diskLow(o.disk)) {
    list.push({ icon: HardDrive, tone: 'warn', text: '主機剩餘空間不到一成。' })
  }
  if (o.counts.lockedStaff) list.push({ icon: Lock, tone: 'danger', text: `${o.counts.lockedStaff} 個帳號因密碼輸錯被暫時鎖定。`, action: { label: '看使用者', tab: 'staff' } })
  if (o.counts.failedLogins24h >= 10)
    list.push({ icon: ShieldAlert, tone: 'warn', text: `過去 24 小時有 ${o.counts.failedLogins24h} 次登入失敗。`, action: { label: '看紀錄', tab: 'audit' } })
  if (o.counts.temporaryPasswords)
    list.push({ icon: KeyRound, tone: 'warn', text: `${o.counts.temporaryPasswords} 位教職員還沒換掉臨時密碼。`, action: { label: '看使用者', tab: 'staff' } })
  return list
})

const tiles = computed(() => {
  const c = overview.value?.counts
  if (!c) return []
  return [
    { icon: Wifi, value: c.online, label: '目前在線', note: '15 分鐘內有操作的人' },
    { icon: PenLine, value: c.answering, label: '正在作答', note: '考生' },
    { icon: Clock, value: c.liveExams, label: '進行中的考試', note: '已開始、還沒結束' },
    { icon: CalendarClock, value: c.upcomingExams, label: '即將舉行', note: '已發布、還沒開始' },
    { icon: Users, value: c.staff, label: '教職員帳號', note: `${c.units} 個單位` },
  ]
})

function since(iso: string) {
  const minutes = Math.floor((Date.now() - new Date(iso).getTime()) / 60000)
  if (minutes < 60) return `${minutes} 分鐘`
  const hours = Math.floor(minutes / 60)
  return hours < 48 ? `${hours} 小時` : `${Math.floor(hours / 24)} 天`
}
const when = (iso: string) => new Date(iso).toLocaleString('zh-TW', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false })
const actor = (a: AuditEntry) => (a.actorType === 'STAFF' && props.staff.find((s) => s.id === a.actorId)?.name) || a.actorLabel || '系統'
</script>

<template>
  <ErrorNote v-if="error" class="card" :message="error" :retry="load" />
  <p v-else-if="!overview" class="card px-5 py-8 text-center text-ink-2 sm:px-6">載入中…</p>

  <div v-else class="space-y-4">
    <!-- Needs attention -->
    <section class="card">
      <CardHeader title="需要處理">
        <Button size="md" :disabled="loading" @click="load"><RotateCw :class="loading && 'animate-spin'" />重新整理</Button>
      </CardHeader>
      <p v-if="!alerts.length" class="flex items-center gap-2 border-t border-line px-5 py-4 text-[15px] sm:px-6">
        <CircleCheck class="size-5 text-brand" />一切正常，目前沒有需要處理的事。
      </p>
      <ul v-else class="divide-y divide-line border-t border-line">
        <li v-for="a in alerts" :key="a.text" class="flex flex-wrap items-center gap-x-3 gap-y-2 px-5 py-3.5 sm:px-6">
          <span
            class="flex size-9 shrink-0 items-center justify-center rounded-full"
            :class="a.tone === 'danger' ? 'bg-grade-soft text-grade' : 'bg-marker text-marker-ink'"
          >
            <component :is="a.icon" class="size-4.5" />
          </span>
          <span class="min-w-0 flex-1 basis-56 text-[15px]">{{ a.text }}</span>
          <button v-if="a.action" type="button" class="inline-flex items-center gap-1 text-sm font-medium text-brand hover:underline" @click="emit('go', a.action.tab)">
            {{ a.action.label }}<ArrowRight class="size-4" />
          </button>
        </li>
      </ul>
    </section>

    <!-- Right now -->
    <section aria-label="目前狀態" class="grid grid-cols-2 gap-3 px-4 sm:grid-cols-3 md:px-0 lg:grid-cols-5">
      <div v-for="t in tiles" :key="t.label" class="rounded-card bg-paper px-4 py-4 shadow-card max-sm:last:col-span-2">
        <p class="flex items-center gap-1.5 text-[13px] text-ink-2"><component :is="t.icon" class="size-4 text-ink-3" />{{ t.label }}</p>
        <p class="mt-1 text-[28px] leading-tight font-bold tabular-nums">{{ t.value }}</p>
        <p v-if="t.note" class="text-[13px] text-ink-3">{{ t.note }}</p>
      </div>
    </section>

    <div class="grid gap-4 lg:grid-cols-[2fr_3fr]">
      <!-- System -->
      <section class="card">
        <CardHeader title="系統" />
        <dl class="divide-y divide-line border-t border-line text-[15px]">
          <div class="flex items-center justify-between gap-3 px-5 py-3 sm:px-6">
            <dt class="text-ink-2">備份</dt>
            <dd>
              <button v-if="backups" type="button" @click="emit('go', 'backups')"><Badge :tone="backupHealth(backups).tone">{{ backupHealth(backups).label }}</Badge></button>
              <span v-else class="text-ink-3">—</span>
            </dd>
          </div>
          <div v-if="overview.disk" class="px-5 py-3 sm:px-6">
            <div class="flex items-center justify-between gap-3">
              <dt class="text-ink-2">主機剩餘空間</dt>
              <dd class="tabular-nums" :class="diskLow(overview.disk) && 'font-medium text-grade'">
                {{ bytes(overview.disk.free) }}<span class="text-sm text-ink-3"> / {{ bytes(overview.disk.total) }}</span>
              </dd>
            </div>
            <span class="mt-2 block h-1.5 overflow-hidden rounded-full bg-sunken">
              <span
                class="block h-full rounded-full"
                :class="diskLow(overview.disk) ? 'bg-grade' : 'bg-brand'"
                :style="{ width: `${(1 - overview.disk.free / overview.disk.total) * 100}%` }"
              />
            </span>
          </div>
          <div class="flex items-center justify-between gap-3 px-5 py-3 sm:px-6">
            <dt class="text-ink-2">資料庫大小</dt>
            <dd class="tabular-nums">{{ bytes(overview.databaseBytes) }}</dd>
          </div>
          <div class="flex items-center justify-between gap-3 px-5 py-3 sm:px-6">
            <dt class="text-ink-2">已連續運作</dt>
            <dd class="tabular-nums">{{ since(overview.startedAt) }}</dd>
          </div>
          <div class="flex items-center justify-between gap-3 px-5 py-3 sm:px-6">
            <dt class="text-ink-2">版本</dt>
            <dd class="tabular-nums">
              {{ overview.version === webVersion || overview.version === 'dev' ? `v${webVersion}` : `伺服器 v${overview.version}・網頁 v${webVersion}` }}
            </dd>
          </div>
        </dl>
      </section>

      <!-- Recent -->
      <section class="card">
        <CardHeader title="最近的重要操作">
          <button type="button" class="inline-flex items-center gap-1 text-sm font-medium text-brand hover:underline" @click="emit('go', 'audit')">
            全部紀錄<ArrowRight class="size-4" />
          </button>
        </CardHeader>
        <ul class="divide-y divide-line border-t border-line">
          <li v-for="a in overview.recent" :key="a.id" class="flex flex-wrap items-baseline gap-x-3 gap-y-0.5 px-5 py-2.5 text-[15px] sm:px-6">
            <span class="w-24 shrink-0 text-sm text-ink-2 tabular-nums">{{ when(a.at) }}</span>
            <span class="w-28 shrink-0 font-medium" :class="isAlarming(a.action) && 'text-grade'">{{ ACTION_LABEL[a.action] ?? a.action }}</span>
            <span class="min-w-0 flex-1 truncate text-sm text-ink-2">{{ actor(a) }}</span>
          </li>
          <li v-if="!overview.recent.length" class="px-5 py-4 text-[15px] text-ink-2 sm:px-6">還沒有紀錄。</li>
        </ul>
      </section>
    </div>
  </div>
</template>
