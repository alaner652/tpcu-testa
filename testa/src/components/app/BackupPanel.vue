<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ChevronDown, DatabaseBackup, Download, History, RotateCw, SquareTerminal, Trash2, TriangleAlert } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import CardHeader from '@/components/ui/CardHeader.vue'
import ErrorNote from '@/components/ui/ErrorNote.vue'
import EmptyNote from '@/components/ui/EmptyNote.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import MoreMenu from '@/components/ui/MoreMenu.vue'
import Switch from '@/components/ui/Switch.vue'
import BackupConsole from '@/components/app/BackupConsole.vue'
import ChoiceSelect from '@/components/app/ChoiceSelect.vue'
import SettingRow from '@/components/app/SettingRow.vue'
import { useConfirm } from '@/composables/useConfirm'
import { api, apiBlob } from '@/lib/api'
import { backupHealth, bytes, diskLow, type BackupSchedule, type BackupState } from '@/lib/backups'

/**
 * Backups (system administrators). The backend lists and downloads dumps and signals the isolated backup container
 * to create, delete or restore one. The page also sets the automatic schedule (saved on change), shows free disk space,
 * reminds the administrator to keep a copy somewhere else, and offers a small command console for those who prefer it.
 */
type BackupFile = BackupState['items'][number]

const { confirm } = useConfirm()
const backups = ref<BackupState | null>(null)
const loading = ref(false)
const error = ref('')
const starting = ref(false)
const busyWith = ref<string | null>(null)
const showAll = ref(false)
const shown = computed(() => (showAll.value ? backups.value?.items : backups.value?.items.slice(0, 7)) ?? [])
const health = computed(() => backups.value && backupHealth(backups.value))

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    backups.value = await api<BackupState>('/admin/backups')
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    if (!silent) loading.value = false
  }
}
onMounted(() => load())

const wait = () => new Promise((resolve) => window.setTimeout(resolve, 1000))
const fail = (e: unknown) => toast.error((e as Error).message)

async function start() {
  if (starting.value || backups.value?.running) return
  const previous = backups.value?.items[0]?.name
  starting.value = true
  try {
    await api('/admin/backups/run', { method: 'POST' })
    toast('正在建立備份', { description: '完成後會自動更新清單。' })
    for (let i = 0; i < 60; i++) {
      await wait()
      await load(true)
      if (!backups.value?.running) {
        if (backups.value?.lastFailed) throw new Error('備份建立失敗，請聯絡系統維護人員。')
        if (backups.value?.items[0]?.name !== previous) {
          toast.success('備份已建立')
          return
        }
      }
    }
    toast('備份仍在背景執行', { description: '稍後按「重新整理」查看結果。' })
  } catch (e) {
    fail(e)
  } finally {
    starting.value = false
  }
}

async function download(b: BackupFile) {
  try {
    const blob = await apiBlob(`/admin/backups/${encodeURIComponent(b.name)}`)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = b.name
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    toast.success('已開始下載備份', { description: '請存到這台主機以外的地方。' })
    load(true)
  } catch (e) {
    fail(e)
  }
}

async function remove(b: BackupFile) {
  const ok = await confirm({
    title: '刪除這份備份？',
    description: `${b.name} 將永久刪除，之後無法用它還原資料。`,
    confirmLabel: '刪除備份',
    tone: 'danger',
  })
  if (!ok) return
  busyWith.value = b.name
  try {
    await api(`/admin/backups/${encodeURIComponent(b.name)}`, { method: 'DELETE' })
    for (let i = 0; i < 30; i++) {
      await wait()
      await load(true)
      if (backups.value?.lastFailed) throw new Error('備份刪除失敗，請查看伺服器日誌。')
      if (!backups.value?.items.some((item) => item.name === b.name)) {
        toast.success('備份已刪除')
        return
      }
    }
    toast('刪除仍在背景執行', { description: '稍後按「重新整理」查看結果。' })
  } catch (e) {
    fail(e)
  } finally {
    busyWith.value = null
  }
}

async function restore(b: BackupFile) {
  const ok = await confirm({
    title: '從這份備份還原整個系統？',
    description: `目前的資料會先自動備份，再以 ${b.name} 取代整個資料庫。還原期間所有人會暫時無法操作，完成後可能需要重新登入。`,
    confirmLabel: '開始還原',
    confirmText: '還原',
    tone: 'danger',
  })
  if (!ok) return
  busyWith.value = b.name
  try {
    await api(`/admin/backups/${encodeURIComponent(b.name)}/restore`, { method: 'POST', body: { confirmation: '還原' } })
    toast('正在還原備份', { description: '請勿關閉伺服器；完成後頁面會自動重新載入。' })
    let started = false
    for (let i = 0; i < 300; i++) {
      await wait()
      const state = await api<{ active: boolean; status: string }>('/maintenance')
      if (state.active || state.status === 'restoring') started = true
      if (started && state.status === 'version-mismatch') {
        throw new Error('這份備份來自不同版本的系統，無法從網頁還原，請由系統維護人員在主機上還原。原本的資料未被取代。')
      }
      if (started && !state.active && state.status === 'error') throw new Error('備份還原失敗，原本的資料未被取代。')
      if (started && !state.active && state.status === 'ok') {
        toast.success('備份已還原，正在重新載入')
        window.setTimeout(() => window.location.reload(), 800)
        return
      }
    }
    throw new Error('還原時間超過五分鐘，請查看伺服器狀態。')
  } catch (e) {
    fail(e)
  } finally {
    busyWith.value = null
  }
}

const operationLabel = computed(() => {
  if (backups.value?.operation === 'deleting') return '正在刪除備份'
  if (backups.value?.operation === 'restoring' || backups.value?.maintenance) return '正在還原備份'
  return '正在建立備份'
})

const when = (iso: string) =>
  new Date(iso).toLocaleString('zh-TW', { month: 'numeric', day: 'numeric', weekday: 'short', hour: '2-digit', minute: '2-digit', hour12: false })
const whenShort = (iso: string) => new Date(iso).toLocaleString('zh-TW', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false })

/** Schedule choices: saved as soon as one is picked */
const savingSchedule = ref(false)
const EVERY: [number, string][] = [
  [6, '每 6 小時'],
  [12, '每 12 小時'],
  [24, '每天'],
  [168, '每週'],
]
const HOURS: [number, string][] = Array.from({ length: 24 }, (_, h) => [h, `${String(h).padStart(2, '0')}:00`])
const WEEKDAYS: [number, string][] = [...'一二三四五六日'].map((d, i) => [i + 1, `星期${d}`])
const KEEP: [number, string][] = [7, 14, 30, 60, 90, 180, 365].map((n) => [n, `${n} 天`])
const at = (h: number) => `${String(h).padStart(2, '0')}:00`
/** "每天 02:00", "每週日 02:00", "每 6 小時（02:00、08:00…）" */
function describe(s: BackupSchedule) {
  if (s.everyHours === 24) return `每天 ${at(s.hour)}`
  if (s.everyHours === 168) return `每週${'一二三四五六日'[s.weekday - 1]} ${at(s.hour)}`
  const times = Array.from({ length: 24 / s.everyHours }, (_, i) => at((s.hour + i * s.everyHours) % 24)).sort()
  return `每 ${s.everyHours} 小時（${times.join('、')}）`
}
const timeHint = computed(() => {
  const s = backups.value?.schedule
  if (!s || s.everyHours === 24 || s.everyHours === 168) return '建議選沒有考試的半夜。'
  return `${describe(s).replace(/^每 \d+ 小時/, '每天')}各一次。`
})

async function saveSchedule(patch: Partial<BackupSchedule>) {
  if (!backups.value) return false
  savingSchedule.value = true
  try {
    await api('/admin/settings/backup', { method: 'PUT', body: { ...backups.value.schedule, ...patch } })
    await load(true)
    toast.success('已儲存')
    return true
  } catch (e) {
    fail(e)
    return false
  } finally {
    savingSchedule.value = false
  }
}
const showConsole = ref(false)

/** How each listed file was made, from the run history */
const madeBy = computed(() => new Map((backups.value?.history ?? []).filter((r) => r.ok && r.name).map((r) => [r.name, r.kind])))

/** A copy kept elsewhere: remind after 30 days without a download */
const offsiteDue = computed(() => {
  const at = backups.value?.lastDownloadAt
  return !at || Date.now() - new Date(at).getTime() > 30 * 86400 * 1000
})
</script>

<template>
  <div class="space-y-4">
    <!-- Status -->
    <section class="card">
      <CardHeader title="備份狀態">
        <div class="flex flex-wrap gap-2">
          <Button variant="outline" :disabled="loading" @click="load()"><RotateCw :class="loading && 'animate-spin'" />重新整理</Button>
          <Button v-if="backups?.triggerSupported" variant="primary" :disabled="starting || backups.running" @click="start">
            <DatabaseBackup />立即備份
          </Button>
        </div>
      </CardHeader>
      <ErrorNote v-if="error" class="border-t border-line" :message="error" :retry="() => load()" />
      <EmptyNote v-else-if="loading && !backups" line role="status">正在讀取備份狀態…</EmptyNote>
      <div v-else-if="backups && health" class="space-y-3 border-t border-line px-5 py-4 text-[15px] sm:px-6">
        <p class="flex flex-wrap items-center gap-x-2 gap-y-1">
          <Badge :tone="backups.running ? 'warn' : health.tone">{{ backups.running ? operationLabel : health.label }}</Badge>
          <span v-if="health.advice" :class="health.tone === 'danger' ? 'text-grade' : 'text-ink-2'">{{ health.advice }}</span>
        </p>
        <p v-if="backups.disk" class="text-ink-2">
          主機剩餘空間
          <span class="tabular-nums" :class="diskLow(backups.disk) ? 'font-medium text-grade' : 'text-ink'">{{ bytes(backups.disk.free) }}</span>
          <span class="text-ink-3 tabular-nums"> / {{ bytes(backups.disk.total) }}</span>
        </p>
        <div v-if="backups.items.length && offsiteDue" class="flex flex-wrap items-center gap-x-3 gap-y-2 text-sm">
          <p class="flex min-w-0 flex-1 basis-64 items-start gap-1.5 text-marker-ink">
            <TriangleAlert class="mt-0.5 size-4 shrink-0" />
            {{ backups.lastDownloadAt ? '超過一個月沒有' : '還沒有' }}把備份另存到主機以外的地方（NAS、隨身碟）。主機壞了，只有另存的備份救得回來。
          </p>
          <Button variant="outline" @click="download(backups.items[0])"><Download />下載最新一份</Button>
        </div>
      </div>
    </section>

    <template v-if="backups && health">
      <!-- Schedule: saved on change -->
      <section v-if="backups.triggerSupported" class="card divide-y divide-line">
        <CardHeader
          title="自動備份"
          for="backup-on"
          :hint="backups.schedule.enabled ? (backups.nextRunAt ? `下次：${when(backups.nextRunAt)}` : '等候備份服務排定時間。') : '關閉後只有按「立即備份」才會備份。'"
        >
          <Switch id="backup-on" :model-value="backups.schedule.enabled" :disabled="savingSchedule" @update:model-value="saveSchedule({ enabled: $event })" />
        </CardHeader>
        <template v-if="backups.schedule.enabled">
          <SettingRow title="多久一次" for="backup-every">
            <ChoiceSelect id="backup-every" :model-value="backups.schedule.everyHours" :options="EVERY" :default-value="24" :disabled="savingSchedule" @update:model-value="saveSchedule({ everyHours: $event })" />
          </SettingRow>
          <SettingRow v-if="backups.schedule.everyHours === 168" title="星期幾" for="backup-weekday">
            <ChoiceSelect id="backup-weekday" :model-value="backups.schedule.weekday" :options="WEEKDAYS" :default-value="7" :disabled="savingSchedule" @update:model-value="saveSchedule({ weekday: $event })" />
          </SettingRow>
          <SettingRow :title="backups.schedule.everyHours < 24 ? '從幾點開始' : '幾點'" :hint="timeHint" for="backup-hour">
            <ChoiceSelect id="backup-hour" :model-value="backups.schedule.hour" :options="HOURS" :default-value="2" :disabled="savingSchedule" @update:model-value="saveSchedule({ hour: $event })" />
          </SettingRow>
        </template>
        <SettingRow title="保留天數" hint="更舊的備份會在下次備份後自動刪除。" for="backup-keep">
          <ChoiceSelect id="backup-keep" :model-value="backups.schedule.keepDays" :options="KEEP" :default-value="backups.keepDaysDefault" :format="(n) => `${n} 天`" :disabled="savingSchedule" @update:model-value="saveSchedule({ keepDays: $event })" />
        </SettingRow>
      </section>

      <!-- Files -->
      <section class="card">
        <CardHeader title="備份檔案" :meta="backups.items.length ? `${backups.items.length} 份，共 ${bytes(backups.totalSize)}` : undefined" />
        <EmptyNote v-if="!backups.items.length" line>還沒有備份。</EmptyNote>
        <ul v-else class="divide-y divide-line border-t border-line">
          <li v-for="b in shown" :key="b.name" class="flex items-center gap-3 py-2 pr-3 pl-5 sm:pr-4 sm:pl-6" :title="b.name">
            <span class="min-w-0 flex-1 text-[15px] whitespace-nowrap tabular-nums">{{ when(b.at) }}</span>
            <span class="text-sm whitespace-nowrap text-ink-2 tabular-nums">
              <template v-if="madeBy.get(b.name) === 'manual'">手動 · </template>{{ bytes(b.size) }}
            </span>
            <Button variant="outline" :disabled="backups.running || !!busyWith" :aria-label="`下載 ${whenShort(b.at)} 的備份`" @click="download(b)"><Download /><span class="max-sm:hidden">下載</span></Button>
            <MoreMenu
              :label="`${whenShort(b.at)} 的備份，更多動作`"
              :actions="
                backups.running || busyWith
                  ? []
                  : [
                      { label: '還原到這份備份', icon: History, run: () => restore(b), danger: true },
                      { label: '刪除備份', icon: Trash2, run: () => remove(b), danger: true },
                    ]
              "
            />
          </li>
        </ul>
        <div v-if="backups.items.length > 7" class="border-t border-line px-5 py-2 text-center sm:px-6">
          <Button @click="showAll = !showAll">
            <ChevronDown :class="showAll && 'rotate-180'" />{{ showAll ? '收合' : `查看全部 ${backups.items.length} 份` }}
          </Button>
        </div>

        <!-- Console, for those who prefer typing -->
        <button
          type="button"
          class="flex w-full items-center gap-2 border-t border-line px-5 py-3 text-left text-[15px] text-ink-2 hover:bg-sunken sm:px-6"
          :aria-expanded="showConsole"
          @click="showConsole = !showConsole"
        >
          <SquareTerminal class="size-4" />終端機<span class="text-sm text-ink-3">（進階）</span>
          <ChevronDown class="ml-auto size-4 transition-transform" :class="showConsole && 'rotate-180'" />
        </button>
        <BackupConsole
          v-if="showConsole"
          :backups="backups"
          :describe="describe"
          :actions="{ refresh: () => load(true), start, download, remove, restore, schedule: saveSchedule }"
        />
      </section>
    </template>
  </div>
</template>
