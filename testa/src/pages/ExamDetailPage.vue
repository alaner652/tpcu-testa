<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue'
import { useRoute } from 'vue-router'
import { ChartColumn, ChevronLeft, ClipboardList, EyeOff, KeyRound, LoaderCircle, Lock, Printer, Shield, Ticket, TimerReset, Trash2, Upload, Users, X } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import TabBar from '@/components/ui/TabBar.vue'
import EmptyNote from '@/components/ui/EmptyNote.vue'
import ErrorNote from '@/components/ui/ErrorNote.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import MoreMenu, { type MoreAction } from '@/components/ui/MoreMenu.vue'
import SelectionBar, { barButton } from '@/components/ui/SelectionBar.vue'
import SelectMark from '@/components/ui/SelectMark.vue'
import EmergencyAccess from '@/components/app/EmergencyAccess.vue'
import ExamScoring from '@/components/app/ExamScoring.vue'
import { useConfirm } from '@/composables/useConfirm'
import { CANDIDATE_STATUS, EXAM_STATUS, retainLabel, useExams, when, type Candidate, type RosterPreview } from '@/composables/useExams'
import { useSelection } from '@/composables/useSelection'
import { useShortcuts } from '@/composables/useShortcuts'

/**
 * Everything for one exam on one page: candidates (roster and proctoring), login slips, grading, results.
 * Roster and proctoring are the same list: manage it before the exam, watch each candidate during it.
 */
const route = useRoute()
const store = useExams()
const { confirm } = useConfirm()

const examId = computed(() => Number(route.params.id))
const exam = computed(() => store.exams.value.find((e) => e.id === examId.value))
const list = ref<Candidate[]>([])
const listError = ref('')
const count = (status: Candidate['status']) => list.value.filter((c) => c.status === status).length
const locked = computed(() => list.value.filter((c) => c.locked || c.leaveCount > 0))
/** Proctors click a count to show only those candidates */
const filter = ref<Candidate['status'] | 'ATTENTION' | null>(null)
const shown = computed(() =>
  filter.value === null ? list.value : filter.value === 'ATTENTION' ? locked.value : list.value.filter((c) => c.status === filter.value),
)
const toggleFilter = (f: Candidate['status'] | 'ATTENTION') => (filter.value = filter.value === f ? null : f)
const STATUS_COUNTS = [
  { status: 'IN', label: '作答中' },
  { status: 'DONE', label: '已交卷' },
  { status: 'IDLE', label: '尚未登入' },
] as const
const fail = (e: unknown) => toast.error((e as Error).message)

type Step = 'roster' | 'tickets' | 'grading' | 'results' | 'access'
// Grading and results are for owners only (proctors never see scores)
const STEPS = computed<{ value: Step; label: string; icon: Component }[]>(() => [
  { value: 'roster', label: '考生', icon: Users },
  { value: 'tickets', label: '登入單', icon: Ticket },
  ...(exam.value?.canManage && exam.value.status !== 'draft'
    ? ([
        { value: 'grading', label: '評分', icon: ClipboardList },
        { value: 'results', label: '成績', icon: ChartColumn },
      ] as const)
    : []),
  // Emergency access is rare: it lives in the "..." menu by the title and shows as a tab only when opened
  ...(step.value === 'access' ? ([{ value: 'access', label: '緊急檢視', icon: Shield }] as const) : []),
])
const step = ref<Step>('roster')
// Moving to another exam starts over; once the exam has ended, open the tab for the next step
watch(
  () => exam.value?.id,
  () => {
    const e = exam.value
    filter.value = null
    step.value = e?.canManage && e.status === 'ended' ? (e.resultsPublishedAt ? 'results' : 'grading') : 'roster'
  },
  { immediate: true },
)
const headerActions = computed<MoreAction[]>(() =>
  exam.value?.canManage && exam.value.status !== 'draft'
    ? [{ label: '緊急檢視授權（給系統管理員）', icon: Shield, run: () => (step.value = 'access') }]
    : [],
)

async function loadList() {
  if (!exam.value) return
  try {
    list.value = await store.candidates(exam.value.id)
    listError.value = ''
  } catch (e) {
    listError.value = (e as Error).message
  }
}

// Load the roster once the exam list is in (or when this page is opened directly)
watch(() => exam.value?.id, (id) => id != null && loadList(), { immediate: true })

// While live: refresh candidate status every 10 seconds
const live = computed(() => exam.value?.status === 'live')
let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  timer = setInterval(() => {
    if (step.value !== 'roster' || !live.value || document.hidden || !exam.value || preview.value) return
    loadList()
    store.refresh(exam.value.id).catch(() => {})
  }, 10000)
})
onBeforeUnmount(() => clearInterval(timer))

// Import: pick a file, the server checks it, import after confirmation
const fileInput = ref<HTMLInputElement | null>(null)
const preview = ref<RosterPreview | null>(null)
// A pending import belongs to the exam it was picked for
watch(examId, () => (preview.value = null))
const busy = ref(false)

async function onFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !exam.value) return
  busy.value = true
  try {
    preview.value = await store.preview(exam.value.id, file)
  } catch (e) {
    fail(e)
  } finally {
    busy.value = false
  }
}

async function doImport() {
  if (!exam.value || !preview.value || busy.value) return
  busy.value = true
  try {
    const result = await store.importRows(exam.value.id, preview.value.rows)
    preview.value = null
    toast.success(`已匯入 ${result.added} 位考生`)
    await Promise.all([loadList(), store.refresh(exam.value.id)])
  } catch (e) {
    fail(e)
  } finally {
    busy.value = false
  }
}

async function reset(candidate: Candidate) {
  if (!exam.value) return
  const ok = await confirm({
    title: `重設 ${candidate.name} 的登入碼？`,
    description: '舊的登入碼會立刻失效（已印出的登入單也是），請把新的登入碼交給考生。',
    confirmLabel: '重設',
  })
  if (!ok) return
  try {
    const { code } = await store.resetCode(exam.value.id, candidate.id)
    candidate.code = code
    candidate.locked = false
    toast.success(`${candidate.name} 的新登入碼：${fmt(code)}`, { duration: 15000 })
  } catch (e) {
    fail(e)
  }
}

function rowActions(c: Candidate): MoreAction[] {
  const e = exam.value
  if (!e || e.archivedAt || e.status === 'ended') return []
  return [
    ...(c.status === 'IN' && e.status === 'live' ? [{ label: '補時', icon: TimerReset, run: () => startExtend(c) }] : []),
    ...(c.status !== 'DONE' && !c.locked ? [{ label: '重設登入碼', icon: KeyRound, run: () => reset(c) }] : []),
    ...(e.canManage && c.status === 'IDLE' ? [{ label: '從名單移除', icon: Trash2, run: () => removeCandidate(c), danger: true }] : []),
  ]
}

// Extra time: move the deadline when a candidate lost time to equipment problems
const extending = ref<number | null>(null)
const extendMinutes = ref(5)
const extendReason = ref('')
const EXTEND_OPTIONS = [5, 10, 15, 30, 60]
const clockTime = (iso: string) => new Date(iso).toLocaleTimeString('zh-TW', { hour: '2-digit', minute: '2-digit', hour12: false })

function startExtend(candidate: Candidate) {
  extending.value = candidate.id
  extendMinutes.value = 5
  extendReason.value = ''
}

async function extend(candidate: Candidate) {
  if (!exam.value || !extendReason.value.trim()) return
  try {
    const updated = await store.extendTime(exam.value.id, candidate.id, extendMinutes.value, extendReason.value.trim())
    list.value = list.value.map((c) => (c.id === updated.id ? updated : c))
    extending.value = null
    toast.success(`已替 ${candidate.name} 補時 ${extendMinutes.value} 分鐘${updated.deadlineAt ? `，作答到 ${clockTime(updated.deadlineAt)}` : ''}`)
  } catch (e) {
    fail(e)
    await loadList()
  }
}

async function removeCandidate(candidate: Candidate) {
  if (!exam.value) return
  const ok = await confirm({
    title: `把 ${candidate.name} 從名單移除？`,
    description: '移除後這位考生無法登入這場考試。之後可以再匯入。',
    confirmLabel: '移除',
    tone: 'danger',
  })
  if (!ok) return
  try {
    await store.removeCandidate(exam.value.id, candidate.id)
    toast.success(`已移除 ${candidate.name}`)
    await Promise.all([loadList(), store.refresh(exam.value.id)])
  } catch (e) {
    fail(e)
  }
}

// ── Several candidates at once (long press or Ctrl click a row) ──
/** Rows can be picked only while something can still be done to them */
const selectable = computed(() => step.value === 'roster' && !!exam.value && !exam.value.archivedAt && exam.value.status !== 'ended')
const selection = useSelection(() => (selectable.value ? shown.value : []), (c) => c.id)
useShortcuts('考生名單', () => selection.shortcuts(() => selectable.value))
const canExtend = computed(() => selection.selected.value.filter((c) => c.status === 'IN' && exam.value?.status === 'live'))
const canReset = computed(() => selection.selected.value.filter((c) => c.status !== 'DONE'))
const canRemove = computed(() => (exam.value?.canManage ? selection.selected.value.filter((c) => c.status === 'IDLE') : []))
/** "3 位（另外 2 位不適用，會略過）" */
const skipping = (n: number) => (n < selection.count.value ? `另外 ${selection.count.value - n} 位不適用，會略過。` : '')
const bulkExtending = ref(false)
watch(selection.active, (on) => !on && (bulkExtending.value = false))

function startBulkExtend() {
  extendMinutes.value = 5
  extendReason.value = ''
  bulkExtending.value = true
}

async function bulk(run: () => Promise<unknown>, done: string) {
  busy.value = true
  try {
    await run()
    toast.success(done)
    selection.clear()
  } catch (e) {
    fail(e)
  } finally {
    busy.value = false
    await loadList()
    if (exam.value) store.refresh(exam.value.id).catch(() => {})
  }
}

async function bulkExtend() {
  const e = exam.value
  const ids = canExtend.value.map((c) => c.id)
  if (!e || !ids.length || !extendReason.value.trim() || busy.value) return
  await bulk(() => store.extendTimes(e.id, ids, extendMinutes.value, extendReason.value.trim()), `已替 ${ids.length} 位考生補時 ${extendMinutes.value} 分鐘`)
}

/** Printing again: the login slips tab then shows only the candidates just reset */
const ticketIds = ref<Set<number> | null>(null)
watch(examId, () => (ticketIds.value = null))
const ticketList = computed(() => (ticketIds.value ? list.value.filter((c) => ticketIds.value!.has(c.id)) : list.value))

async function bulkReset() {
  const e = exam.value
  const ids = canReset.value.map((c) => c.id)
  if (!e || !ids.length) return
  const ok = await confirm({
    title: `重設 ${ids.length} 位考生的登入碼？`,
    description: `舊的登入碼會立刻失效（已印出的登入單也是）。重設後可以只列印這幾位的登入單。${skipping(ids.length)}`,
    confirmLabel: '重設',
  })
  if (!ok) return
  await bulk(() => store.resetCodes(e.id, ids), `已重設 ${ids.length} 位考生的登入碼`)
  if (!list.value.some((c) => ids.includes(c.id))) return
  toast(`要列印這 ${ids.length} 位的新登入單嗎？`, {
    duration: 15000,
    action: { label: '列印登入單', onClick: () => ((ticketIds.value = new Set(ids)), (step.value = 'tickets')) },
  })
}

async function bulkRemove() {
  const e = exam.value
  const ids = canRemove.value.map((c) => c.id)
  if (!e || !ids.length) return
  const ok = await confirm({
    title: `把 ${ids.length} 位考生從名單移除？`,
    description: `移除後這些考生無法登入這場考試。之後可以再匯入。${skipping(ids.length)}`,
    confirmLabel: '移除',
    tone: 'danger',
  })
  if (ok) await bulk(() => store.removeCandidates(e.id, ids), `已移除 ${ids.length} 位考生`)
}

const print = () => window.print()
const fmt = (code: string) => `${code.slice(0, 3)} ${code.slice(3)}`
const site = location.origin + location.pathname
</script>

<template>
  <div v-if="store.loading.value" class="px-4 text-[15px] text-ink-2 md:px-0">載入中…</div>

  <div v-else-if="!exam" class="card px-5 py-12 sm:px-10">
    <h1 class="text-2xl font-bold">找不到這場考試</h1>
    <RouterLink to="/exams" class="mt-3 inline-block rounded-sm text-brand underline-offset-2 hover:underline">回到考試列表</RouterLink>
  </div>

  <div v-else>
    <div class="no-print px-4 md:px-0">
      <RouterLink to="/exams" class="inline-flex h-9 items-center gap-1 rounded-full pr-3 text-sm text-ink-2 hover:text-ink">
        <ChevronLeft class="size-4" />考試
      </RouterLink>
      <div class="mt-1 flex items-start gap-3">
        <div class="flex min-w-0 flex-1 flex-wrap items-center gap-x-3 gap-y-2">
          <h1 class="text-[24px] leading-tight font-bold sm:text-[30px]">{{ exam.name }}</h1>
          <Badge :tone="EXAM_STATUS[exam.status].tone">{{ EXAM_STATUS[exam.status].label }}</Badge>
        </div>
        <MoreMenu class="shrink-0" :actions="headerActions" label="更多" />
      </div>
      <p class="mt-1.5 text-[15px] text-ink-2">
        {{ when(exam.startAt) }}，作答 {{ exam.duration }} 分鐘。題庫：<RouterLink
          v-if="exam.bankId"
          :to="`/banks/${exam.bankId}`"
          class="rounded-sm text-brand underline-offset-2 hover:underline"
          >{{ exam.bankName }}</RouterLink
        ><template v-else>{{ exam.bankName ?? '題庫已刪除' }}</template>
      </p>
    </div>

    <p v-if="exam.archivedAt" class="no-print mx-4 mt-4 rounded-control bg-marker px-4 py-3 text-[15px] text-marker-ink md:mx-0">
      這場考試已於 {{ when(exam.archivedAt) }} 封存，只能檢視。考生不能再登入查成績。資料保留到 {{ retainLabel(exam.retainUntil) }}。
    </p>

    <TabBar class="mt-5" label="考試步驟" :tabs="STEPS" :current="step" @select="step = $event" />

    <ErrorNote v-if="listError" class="card mb-3" :message="listError" :retry="loadList" />

    <!-- Candidates: roster and proctoring -->
    <section v-if="step === 'roster'" class="card">
      <div class="flex min-h-16 flex-wrap items-center justify-between gap-x-6 gap-y-3 px-5 py-3 sm:px-6">
        <!-- Before the exam only the head count; afterwards a count per status -->
        <p v-if="exam.status === 'draft' || exam.status === 'scheduled'" class="text-[15px] text-ink-2">
          <strong class="font-semibold text-ink tabular-nums">{{ list.length }}</strong> 位考生
        </p>
        <div v-else role="group" aria-label="依狀態篩選考生" class="-mx-2.5 flex flex-wrap items-center gap-x-1 gap-y-1 text-[15px] text-ink-2">
          <button
            v-for="g in STATUS_COUNTS"
            :key="g.status"
            type="button"
            class="flex h-10 items-baseline gap-1.5 rounded-full px-2.5 pt-1.5 transition-colors hover:bg-ink/[0.06]"
            :class="filter === g.status && 'bg-brand-soft text-brand-strong hover:bg-brand-soft'"
            :aria-pressed="filter === g.status"
            @click="toggleFilter(g.status)"
          >
            <span class="text-xl leading-none font-bold text-ink tabular-nums">{{ count(g.status) }}</span>{{ g.label }}
          </button>
          <button
            v-if="locked.length"
            type="button"
            class="flex h-10 items-baseline gap-1.5 rounded-full px-2.5 pt-1.5 text-grade transition-colors hover:bg-grade-soft"
            :class="filter === 'ATTENTION' && 'bg-grade-soft'"
            :aria-pressed="filter === 'ATTENTION'"
            @click="toggleFilter('ATTENTION')"
          >
            <span class="text-xl leading-none font-bold tabular-nums">{{ locked.length }}</span>需要注意
          </button>
        </div>
        <template v-if="exam.canManage && exam.status !== 'ended' && !preview">
          <input ref="fileInput" type="file" accept=".xlsx,.csv" class="hidden" @change="onFile" />
          <Button variant="primary" :disabled="busy" @click="fileInput?.click()">
            <LoaderCircle v-if="busy" class="animate-spin" /><Upload v-else />匯入名單
          </Button>
        </template>
      </div>

      <div v-if="preview" class="border-t border-line bg-sunken/60 px-5 py-5 sm:px-6">
        <p class="text-sm text-ink-2">{{ preview.file }}</p>
        <p class="mt-1 text-lg font-semibold">
          {{ preview.rows.length }} 筆可以匯入<template v-if="preview.problems.length">，{{ preview.problems.length }} 筆需要修正</template>
        </p>
        <ul v-if="preview.problems.length" class="thin-scroll mt-3 max-h-64 space-y-1.5 overflow-y-auto">
          <li v-for="p in preview.problems" :key="p.row" class="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-control bg-paper px-4 py-2.5 text-[15px]">
            <span class="text-ink-2 tabular-nums">{{ p.row }}</span>
            <span class="font-medium">{{ p.who }}</span>
            <span class="text-grade">{{ p.reason }}</span>
          </li>
        </ul>
        <p class="mt-3 text-sm text-ink-2">
          <template v-if="preview.problems.length">有問題的資料不會匯入，修正後再上傳一次即可。</template>系統會自動排座位並產生登入碼。
        </p>
        <div class="mt-4 flex flex-wrap justify-end gap-2">
          <Button size="md" @click="preview = null"><X />取消</Button>
          <Button variant="primary" size="md" :disabled="busy || !preview.rows.length" @click="doImport"><Upload />匯入 {{ preview.rows.length }} 位考生</Button>
        </div>
      </div>

      <div v-if="list.length === 0 && !preview" class="border-t border-line px-5 py-10 sm:px-6">
        <h2 class="text-xl font-bold">還沒有考生</h2>
        <p class="mt-1.5 text-[15px] text-ink-2">
          上傳名單（Excel 的 .xlsx 或 .csv），就能建立白名單。檔案要有三欄：准考證號、姓名、報考科系，第一列可以是標題。
        </p>
      </div>

      <ul v-else-if="list.length" class="divide-y divide-line border-t border-line">
        <li
          v-for="c in shown"
          :key="c.id"
          v-bind="selectable ? selection.row(c) : {}"
          class="group/row flex flex-wrap items-center gap-x-3 gap-y-1.5 py-3 pr-5 pl-3 outline-none focus-visible:bg-sunken sm:pr-6 sm:pl-4 pointer-coarse:select-none"
          :class="selectable && selection.has(c) && 'bg-brand-soft/50 focus-visible:bg-brand-soft'"
        >
          <SelectMark
            v-if="selectable"
            :checked="selection.has(c)"
            :picking="selection.active.value"
            :label="`選取 ${c.name}`"
            @toggle="(e) => selection.mark(c, e)"
          >
            <span class="text-sm text-ink-3 tabular-nums" title="座位">{{ c.seat }}</span>
          </SelectMark>
          <span v-else class="w-10 text-center text-sm text-ink-3 tabular-nums" title="座位">{{ c.seat }}</span>
          <div class="min-w-0 flex-1 basis-48">
            <p class="flex flex-wrap items-center gap-x-2 gap-y-1">
              <span class="mr-0.5 text-base font-medium">{{ c.name }}</span>
              <!-- Before the exam everyone is "not signed in", so rows do not repeat it -->
              <Badge v-if="exam.status === 'live' || exam.status === 'ended'" :tone="CANDIDATE_STATUS[c.status].tone">{{ CANDIDATE_STATUS[c.status].label }}</Badge>
              <Badge v-if="c.locked" tone="danger"><Lock />登入碼輸錯，已鎖定</Badge>
              <Badge v-if="c.leaveCount" tone="warn"><EyeOff />離開畫面 {{ c.leaveCount }} 次</Badge>
              <Badge v-if="c.extraMinutes" tone="neutral"><TimerReset />已補時 {{ c.extraMinutes }} 分鐘</Badge>
            </p>
            <p class="mt-0.5 text-sm text-ink-2 tabular-nums">
              {{ c.ticket }}<template v-if="c.dept">，{{ c.dept }}</template><template v-if="c.status === 'IN' && c.deadlineAt">，作答到 {{ clockTime(c.deadlineAt) }}</template>
            </p>
          </div>
          <Button v-if="c.locked && c.status !== 'DONE' && exam.status !== 'ended'" variant="primary" class="ml-auto" @click="reset(c)"><KeyRound />重設登入碼</Button>
          <MoreMenu :actions="rowActions(c)" :label="`${c.name} 的更多動作`" />
          <form v-if="extending === c.id && c.status === 'IN'" class="flex basis-full flex-wrap items-center gap-2 pt-1" @submit.prevent="extend(c)">
            <label class="text-[15px] text-ink-2" :for="`extend-min-${c.id}`">補</label>
            <select :id="`extend-min-${c.id}`" v-model.number="extendMinutes" class="field w-28">
              <option v-for="m in EXTEND_OPTIONS" :key="m" :value="m">{{ m }} 分鐘</option>
            </select>
            <input v-model="extendReason" class="field min-w-0 flex-1 basis-56" maxlength="200" placeholder="原因（必填，會留在操作紀錄），例如：電腦當機換座位" aria-label="補時的原因" />
            <Button size="md" type="button" @click="extending = null"><X />取消</Button>
            <Button variant="primary" size="md" type="submit" :disabled="!extendReason.trim()"><TimerReset />確定補時</Button>
          </form>
        </li>
        <EmptyNote v-if="!shown.length" as="li">
          目前沒有這個狀態的考生。<button type="button" class="rounded-sm font-medium text-brand underline-offset-2 hover:underline" @click="filter = null">顯示全部</button>
        </EmptyNote>
      </ul>
      <p v-if="live && list.length" class="border-t border-line px-5 py-3 text-sm text-ink-3 sm:px-6">每 10 秒自動更新。</p>
    </section>

    <!-- Login slips -->
    <section v-else-if="step === 'tickets'">
      <div class="no-print card flex flex-wrap items-center justify-between gap-3 px-5 py-4 sm:px-6">
        <p v-if="ticketIds" class="min-w-0 flex-1 basis-64 text-[15px] text-ink-2">
          只顯示剛重設登入碼的 {{ ticketList.length }} 位。<button type="button" class="rounded-sm font-medium text-brand underline-offset-2 hover:underline" @click="ticketIds = null">顯示全部</button>
        </p>
        <p v-else class="min-w-0 flex-1 basis-64 text-[15px] text-ink-2">每位考生一張。登入碼等同密碼，印出後請妥善保管。</p>
        <Button variant="primary" :disabled="ticketList.length === 0" @click="print">
          <Printer />列印 {{ ticketList.length }} 張
        </Button>
      </div>
      <p v-if="list.length === 0" class="mt-3 px-5 text-[15px] text-ink-2 md:px-1">先到「考生」匯入名單。</p>
      <ul class="mt-3 grid gap-3 px-4 sm:grid-cols-2 md:px-0 lg:grid-cols-3 print:mt-0 print:grid-cols-2 print:px-0">
        <li v-for="c in ticketList" :key="c.id" class="ticket rounded-card border-[1.5px] border-dashed border-line-strong bg-paper p-5">
          <p class="text-[13px] text-ink-2">{{ exam.name }}</p>
          <p class="mt-2 text-lg font-semibold">{{ c.name }}</p>
          <dl class="mt-2 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
            <dt class="text-ink-2">准考證號</dt>
            <dd class="font-medium tabular-nums">{{ c.ticket }}</dd>
            <dt class="text-ink-2">座位</dt>
            <dd class="font-medium tabular-nums">第 {{ c.seat }} 號</dd>
            <dt class="self-center text-ink-2">登入碼</dt>
            <dd class="text-2xl font-bold tracking-[0.14em] tabular-nums">{{ fmt(c.code) }}</dd>
          </dl>
          <p class="mt-3 text-[12px] text-ink-3">
            {{ when(exam.startAt) }} 開始，作答 {{ exam.duration }} 分鐘。<br />
            登入網址：<span class="font-medium break-all text-ink-2">{{ site }}</span>
          </p>
        </li>
      </ul>
    </section>

    <!-- Grading, results -->
    <EmergencyAccess v-else-if="step === 'access'" :exam="exam" />
    <ExamScoring v-else :key="`${exam.id}-${step}`" :exam="exam" :view="step" />

    <SelectionBar :count="selection.count.value" :total="shown.length" unit="位" @all="selection.all" @clear="selection.clear">
      <form v-if="bulkExtending" class="flex flex-wrap items-center gap-1.5" @submit.prevent="bulkExtend">
        <select v-model.number="extendMinutes" class="field h-9 w-28 bg-paper text-ink" aria-label="補幾分鐘">
          <option v-for="m in EXTEND_OPTIONS" :key="m" :value="m">補 {{ m }} 分鐘</option>
        </select>
        <input v-model="extendReason" class="field h-9 w-56 bg-paper text-ink max-sm:w-full" maxlength="200" placeholder="原因（必填）" aria-label="補時的原因" />
        <button type="button" :class="barButton" @click="bulkExtending = false">取消</button>
        <button type="submit" :class="[barButton, 'bg-brand text-brand-fg hover:bg-brand-strong']" :disabled="!extendReason.trim() || busy">
          <TimerReset />替 {{ canExtend.length }} 位補時
        </button>
      </form>
      <template v-else>
        <button v-if="canExtend.length" type="button" :class="barButton" @click="startBulkExtend"><TimerReset />補時</button>
        <button v-if="canReset.length" type="button" :class="barButton" :disabled="busy" @click="bulkReset"><KeyRound />重設登入碼</button>
        <button v-if="canRemove.length" type="button" :class="barButton" :disabled="busy" @click="bulkRemove"><Trash2 />移除</button>
        <span v-if="!canExtend.length && !canReset.length && !canRemove.length" class="px-2 text-sm text-paper/70">這些考生沒有可以一起做的動作</span>
      </template>
    </SelectionBar>
  </div>
</template>
