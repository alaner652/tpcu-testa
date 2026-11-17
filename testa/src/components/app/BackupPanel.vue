<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ChevronDown, DatabaseBackup, Download, HardDrive, History, RotateCw, Trash2, TriangleAlert } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import MoreMenu from '@/components/ui/MoreMenu.vue'
import { useConfirm } from '@/composables/useConfirm'
import { api, apiBlob } from '@/lib/api'
import { backupHealth, bytes, diskLow, type BackupState } from '@/lib/backups'

/**
 * Backups (system administrators). The backend lists and downloads dumps and signals the isolated backup container
 * to create, delete or restore one; this page also shows the schedule, recent runs, free disk space,
 * and reminds the administrator to keep a copy somewhere else.
 */
type BackupFile = BackupState['items'][number]

const { confirm } = useConfirm()
const backups = ref<BackupState | null>(null)
const loading = ref(false)
const error = ref('')
const starting = ref(false)
const busyWith = ref<string | null>(null)
const showAll = ref(false)
const showAllRuns = ref(false)
const shown = computed(() => (showAll.value ? backups.value?.items : backups.value?.items.slice(0, 7)) ?? [])
const runs = computed(() => (showAllRuns.value ? backups.value?.history : backups.value?.history.slice(0, 5)) ?? [])
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
  new Date(iso).toLocaleString('zh-TW', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
const whenShort = (iso: string) => new Date(iso).toLocaleString('zh-TW', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false })
const KIND: Record<string, string> = { auto: '自動備份', manual: '手動備份', delete: '刪除備份', restore: '還原備份' }

/** A copy kept elsewhere: remind after 30 days without a download */
const offsiteDue = computed(() => {
  const at = backups.value?.lastDownloadAt
  return !at || Date.now() - new Date(at).getTime() > 30 * 86400 * 1000
})
const diskShare = computed(() => (backups.value?.disk ? 1 - backups.value.disk.free / backups.value.disk.total : 0))
</script>

<template>
  <section class="card">
    <div class="flex flex-wrap items-center justify-between gap-3 px-5 py-4 sm:px-6">
      <div>
        <h2 class="text-[17px] font-semibold">資料庫備份</h2>
        <p class="mt-0.5 text-sm text-ink-2">系統每天自動備份一次，也可以隨時手動備份。</p>
      </div>
      <div class="flex flex-wrap gap-2">
        <Button variant="outline" :disabled="loading" @click="load()"><RotateCw :class="loading && 'animate-spin'" />重新整理</Button>
        <Button v-if="backups?.triggerSupported" variant="primary" :disabled="starting || backups.running" @click="start">
          <RotateCw v-if="starting || backups.running" class="animate-spin" /><DatabaseBackup v-else />
          {{ starting || backups.running ? operationLabel : '立即備份' }}
        </Button>
      </div>
    </div>

    <p v-if="error" class="border-t border-line px-5 py-6 text-[15px] text-grade sm:px-6" role="alert">{{ error }}</p>
    <p v-else-if="loading && !backups" class="border-t border-line px-5 py-6 text-[15px] text-ink-2 sm:px-6" role="status">正在讀取備份狀態…</p>
    <template v-else-if="backups && health">
      <!-- Health -->
      <div class="border-t border-line px-5 py-4 text-[15px] sm:px-6">
        <div class="flex flex-wrap items-center gap-2">
          <Badge :tone="backups.running ? 'warn' : health.tone">{{ backups.running ? operationLabel : health.label }}</Badge>
          <span v-if="backups.items.length">最近一次：<span class="font-medium tabular-nums">{{ when(backups.items[0].at) }}</span></span>
          <span v-if="backups.items.length" class="text-sm text-ink-2">{{ bytes(backups.items[0].size) }}</span>
        </div>
        <p v-if="health.advice" class="mt-2 text-sm" :class="health.tone === 'danger' ? 'text-grade' : 'text-ink-2'">{{ health.advice }}</p>
      </div>

      <!-- Numbers -->
      <dl v-if="backups.configured" class="grid grid-cols-2 gap-px border-t border-line bg-line lg:grid-cols-4">
        <div class="bg-paper px-5 py-3.5 sm:px-6">
          <dt class="text-[13px] text-ink-3">下次自動備份</dt>
          <dd class="mt-0.5 font-medium tabular-nums">{{ backups.nextRunAt ? whenShort(backups.nextRunAt) : '—' }}</dd>
        </div>
        <div class="bg-paper px-5 py-3.5 sm:px-6">
          <dt class="text-[13px] text-ink-3">保留</dt>
          <dd class="mt-0.5 font-medium">{{ backups.keepDays }} 天<span class="ml-1 text-[13px] font-normal text-ink-3">（在系統設定調整）</span></dd>
        </div>
        <div class="bg-paper px-5 py-3.5 sm:px-6">
          <dt class="text-[13px] text-ink-3">目前備份</dt>
          <dd class="mt-0.5 font-medium tabular-nums">{{ backups.items.length }} 份，共 {{ bytes(backups.totalSize) }}</dd>
        </div>
        <div class="bg-paper px-5 py-3.5 sm:px-6">
          <dt class="flex items-center gap-1 text-[13px] text-ink-3"><HardDrive class="size-3.5" />主機剩餘空間</dt>
          <dd v-if="backups.disk" class="mt-0.5">
            <span class="font-medium tabular-nums" :class="diskLow(backups.disk) && 'text-grade'">{{ bytes(backups.disk.free) }}</span>
            <span class="text-[13px] text-ink-3"> / {{ bytes(backups.disk.total) }}</span>
            <span class="mt-1.5 block h-1.5 overflow-hidden rounded-full bg-sunken">
              <span class="block h-full rounded-full" :class="diskLow(backups.disk) ? 'bg-grade' : 'bg-brand'" :style="{ width: `${diskShare * 100}%` }" />
            </span>
          </dd>
          <dd v-else class="mt-0.5 font-medium">—</dd>
        </div>
      </dl>

      <!-- Keep a copy somewhere else -->
      <div v-if="backups.items.length && offsiteDue" class="flex items-start gap-3 border-t border-line bg-marker/40 px-5 py-3.5 text-sm sm:px-6">
        <TriangleAlert class="mt-0.5 size-4 shrink-0 text-marker-ink" />
        <p class="min-w-0 flex-1 text-ink">
          建議每個月下載一份備份，存到這台主機以外的地方（例如學校 NAS 或加密隨身碟）。主機硬碟壞掉時，只有另外存放的備份救得回來。
          <span class="block text-ink-2">上次下載：{{ backups.lastDownloadAt ? whenShort(backups.lastDownloadAt) : '從來沒有' }}</span>
        </p>
        <Button size="md" variant="outline" class="shrink-0 bg-paper" @click="download(backups.items[0])"><Download />下載最新一份</Button>
      </div>

      <!-- Files -->
      <template v-if="backups.items.length">
        <h3 class="border-t border-line bg-sunken px-5 py-1.5 text-[13px] font-semibold text-ink-2 sm:px-6">備份檔案</h3>
        <ul class="divide-y divide-line border-t border-line">
          <li v-for="b in shown" :key="b.name" class="flex flex-wrap items-center gap-x-4 gap-y-2 px-5 py-3 text-[15px] sm:px-6">
            <span class="w-36 shrink-0 text-sm text-ink-2 tabular-nums">{{ when(b.at) }}</span>
            <span class="min-w-0 flex-1 basis-48 truncate font-mono text-sm">{{ b.name }}</span>
            <span class="text-sm text-ink-2 tabular-nums">{{ bytes(b.size) }}</span>
            <Button variant="outline" :disabled="backups.running || !!busyWith" @click="download(b)"><Download />下載</Button>
            <MoreMenu
              :label="`${b.name} 更多動作`"
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
        <div v-if="backups.items.length > 7" class="border-t border-line px-5 py-3 text-center sm:px-6">
          <Button @click="showAll = !showAll">
            <ChevronDown :class="showAll && 'rotate-180'" />{{ showAll ? '收合' : `查看全部 ${backups.items.length} 份` }}
          </Button>
        </div>
      </template>

      <!-- What the backup container did -->
      <template v-if="backups.history.length">
        <h3 class="border-t border-line bg-sunken px-5 py-1.5 text-[13px] font-semibold text-ink-2 sm:px-6">執行紀錄</h3>
        <ul class="divide-y divide-line border-t border-line">
          <li v-for="r in runs" :key="r.at + r.kind" class="flex flex-wrap items-center gap-x-4 gap-y-1 px-5 py-2.5 text-sm sm:px-6">
            <span class="w-36 shrink-0 text-ink-2 tabular-nums">{{ when(r.at) }}</span>
            <span class="w-20 shrink-0 font-medium">{{ KIND[r.kind] ?? r.kind }}</span>
            <Badge :tone="r.ok ? 'done' : 'danger'">{{ r.ok ? '成功' : '失敗' }}</Badge>
            <span class="min-w-0 flex-1 truncate font-mono text-[13px] text-ink-3">{{ r.name }}</span>
          </li>
        </ul>
        <div v-if="backups.history.length > 5" class="border-t border-line px-5 py-2 text-center sm:px-6">
          <Button @click="showAllRuns = !showAllRuns">
            <ChevronDown :class="showAllRuns && 'rotate-180'" />{{ showAllRuns ? '收合' : '查看更多' }}
          </Button>
        </div>
      </template>
    </template>
  </section>
</template>
