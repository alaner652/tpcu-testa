<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue'
import { useRoute } from 'vue-router'
import { ChartColumn, Check, ChevronLeft, KeyRound, ListChecks, LoaderCircle, Printer, RotateCw, ShieldCheck, Ticket, TimerReset, Trash2, Upload, Users, X } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import Button from '@/components/ui/Button.vue'
import MoreMenu, { type MoreAction } from '@/components/ui/MoreMenu.vue'
import EmergencyAccess from '@/components/app/EmergencyAccess.vue'
import ExamScoring from '@/components/app/ExamScoring.vue'
import { useConfirm } from '@/composables/useConfirm'
import { CANDIDATE_STATUS, EXAM_STATUS, retainLabel, useExams, when, type Candidate, type RosterPreview } from '@/composables/useExams'

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
        { value: 'grading', label: '評分', icon: ListChecks },
        { value: 'results', label: '成績', icon: ChartColumn },
      ] as const)
    : []),
  // Emergency access is rare: it lives in the "..." menu by the title and shows as a tab only when opened
  ...(step.value === 'access' ? ([{ value: 'access', label: '緊急檢視', icon: ShieldCheck }] as const) : []),
])
const step = ref<Step>('roster')
// Once the exam has ended, open the tab for the next step
watch(
  () => exam.value?.id,
  () => {
    const e = exam.value
    if (e?.canManage && e.status === 'ended') step.value = e.resultsPublishedAt ? 'results' : 'grading'
  },
  { immediate: true },
)
const headerActions = computed<MoreAction[]>(() =>
  exam.value?.canManage && exam.value.status !== 'draft'
    ? [{ label: '緊急檢視授權（給系統管理員）', icon: ShieldCheck, run: () => (step.value = 'access') }]
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
      <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-2">
        <h1 class="text-[24px] leading-tight font-bold sm:text-[30px]">{{ exam.name }}</h1>
        <span class="rounded-full px-2.5 py-0.5 text-[13px] font-medium" :class="EXAM_STATUS[exam.status].class">
          {{ EXAM_STATUS[exam.status].label }}
        </span>
        <MoreMenu class="ml-auto" :actions="headerActions" label="更多" />
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

    <div role="tablist" aria-label="考試步驟" class="mt-5 mb-4 flex flex-wrap gap-1 px-4 md:px-0">
      <button
        v-for="s in STEPS"
        :key="s.value"
        type="button"
        role="tab"
        :aria-selected="step === s.value"
        class="inline-flex h-10 items-center gap-1.5 rounded-full px-4 text-[15px] transition-colors"
        :class="step === s.value ? 'bg-brand font-medium text-brand-fg' : 'text-ink-2 hover:bg-ink/[0.06] hover:text-ink'"
        @click="step = s.value"
      >
        <component :is="s.icon" class="size-4" aria-hidden="true" />{{ s.label }}
      </button>
    </div>

    <p v-if="listError" class="card mb-3 flex items-center gap-3 px-5 py-4 text-[15px] text-grade sm:px-6" role="alert">
      <span class="min-w-0 flex-1">{{ listError }}</span>
      <Button variant="outline" class="rounded-full" @click="loadList"><RotateCw />重試</Button>
    </p>

    <!-- Candidates: roster and proctoring -->
    <section v-if="step === 'roster'" class="card">
      <div class="flex min-h-16 flex-wrap items-center justify-between gap-x-6 gap-y-3 px-5 py-3 sm:px-6">
        <!-- Before the exam only the head count; afterwards a count per status -->
        <p v-if="exam.status === 'draft' || exam.status === 'scheduled'" class="text-[15px] text-ink-2">
          <strong class="font-semibold text-ink tabular-nums">{{ list.length }}</strong> 位考生
        </p>
        <dl v-else class="flex flex-wrap items-baseline gap-x-6 gap-y-1 text-[15px] text-ink-2">
          <div v-for="g in STATUS_COUNTS" :key="g.status" class="flex items-baseline gap-1.5">
            <dd class="order-first text-xl font-bold text-ink tabular-nums">{{ count(g.status) }}</dd>
            <dt>{{ g.label }}</dt>
          </div>
          <div v-if="locked.length" class="flex items-baseline gap-1.5 text-grade">
            <dd class="order-first text-xl font-bold tabular-nums">{{ locked.length }}</dd>
            <dt>需要注意</dt>
          </div>
        </dl>
        <template v-if="exam.canManage && exam.status !== 'ended' && !preview">
          <input ref="fileInput" type="file" accept=".xlsx,.csv" class="hidden" @change="onFile" />
          <Button variant="primary" class="rounded-full" :disabled="busy" @click="fileInput?.click()">
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
          <Button size="md" class="rounded-full" @click="preview = null"><X />取消</Button>
          <Button variant="primary" size="md" class="rounded-full" :disabled="busy || !preview.rows.length" @click="doImport"><Check />匯入 {{ preview.rows.length }} 位考生</Button>
        </div>
      </div>

      <div v-if="list.length === 0 && !preview" class="border-t border-line px-5 py-10 sm:px-6">
        <h2 class="text-xl font-bold">還沒有考生</h2>
        <p class="mt-1.5 text-[15px] text-ink-2">
          上傳名單（Excel 的 .xlsx 或 .csv），就能建立白名單。檔案要有三欄：准考證號、姓名、報考科系，第一列可以是標題。
        </p>
      </div>

      <ul v-else-if="list.length" class="divide-y divide-line border-t border-line">
        <li v-for="c in list" :key="c.id" class="flex flex-wrap items-center gap-x-4 gap-y-1.5 px-5 py-3 sm:px-6">
          <span class="w-8 text-sm text-ink-3 tabular-nums" title="座位">{{ c.seat }}</span>
          <div class="min-w-0 flex-1 basis-40">
            <p class="text-base font-medium">{{ c.name }}</p>
            <p class="text-sm text-ink-2 tabular-nums">{{ c.ticket }}<template v-if="c.dept">，{{ c.dept }}</template></p>
          </div>
          <span v-if="c.locked" class="rounded-full bg-marker px-2.5 py-0.5 text-[13px] font-medium text-marker-ink">登入碼輸錯太多次，已鎖定</span>
          <span v-if="c.leaveCount" class="rounded-full bg-marker px-2.5 py-0.5 text-[13px] font-medium text-marker-ink">離開畫面 {{ c.leaveCount }} 次</span>
          <span v-if="c.extraMinutes" class="rounded-full bg-sunken px-2.5 py-0.5 text-[13px] font-medium text-ink-2">已補時 {{ c.extraMinutes }} 分鐘</span>
          <span v-if="c.status === 'IN' && c.deadlineAt" class="text-sm text-ink-2 tabular-nums">作答到 {{ clockTime(c.deadlineAt) }}</span>
          <!-- Before the exam everyone is "not signed in", so rows do not repeat it -->
          <span
            v-if="exam.status === 'live' || exam.status === 'ended'"
            class="rounded-full px-2.5 py-0.5 text-[13px] font-medium"
            :class="CANDIDATE_STATUS[c.status].class"
          >
            {{ CANDIDATE_STATUS[c.status].label }}
          </span>
          <Button v-if="c.locked && c.status !== 'DONE' && exam.status !== 'ended'" variant="primary" class="rounded-full" @click="reset(c)"><KeyRound />重設登入碼</Button>
          <MoreMenu :actions="rowActions(c)" :label="`${c.name} 的更多動作`" />
          <form v-if="extending === c.id && c.status === 'IN'" class="flex basis-full flex-wrap items-center gap-2 pt-1" @submit.prevent="extend(c)">
            <label class="text-[15px] text-ink-2" :for="`extend-min-${c.id}`">補</label>
            <select :id="`extend-min-${c.id}`" v-model.number="extendMinutes" class="field h-11 w-28">
              <option v-for="m in EXTEND_OPTIONS" :key="m" :value="m">{{ m }} 分鐘</option>
            </select>
            <input v-model="extendReason" class="field h-11 min-w-0 flex-1 basis-56" maxlength="200" placeholder="原因（必填，會留在操作紀錄），例如：電腦當機換座位" aria-label="補時的原因" />
            <Button variant="primary" size="md" class="h-11 rounded-full" type="submit" :disabled="!extendReason.trim()"><Check />確定補時</Button>
            <Button size="md" class="h-11 rounded-full" type="button" @click="extending = null"><X />取消</Button>
          </form>
        </li>
      </ul>
      <p v-if="live && list.length" class="border-t border-line px-5 py-3 text-sm text-ink-3 sm:px-6">每 10 秒自動更新。</p>
    </section>

    <!-- Login slips -->
    <section v-else-if="step === 'tickets'">
      <div class="no-print card flex flex-wrap items-center justify-between gap-3 px-5 py-4 sm:px-6">
        <p class="min-w-0 flex-1 basis-64 text-[15px] text-ink-2">每位考生一張。登入碼等同密碼，印出後請妥善保管。</p>
        <Button variant="primary" class="rounded-full" :disabled="list.length === 0" @click="print">
          <Printer />列印 {{ list.length }} 張
        </Button>
      </div>
      <p v-if="list.length === 0" class="mt-3 px-5 text-[15px] text-ink-2 md:px-1">先到「考生」匯入名單。</p>
      <ul class="mt-3 grid gap-3 px-4 sm:grid-cols-2 md:px-0 lg:grid-cols-3 print:mt-0 print:grid-cols-2 print:px-0">
        <li v-for="c in list" :key="c.id" class="ticket rounded-card border-[1.5px] border-dashed border-line-strong bg-paper p-5">
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
          <p class="mt-3 text-[12px] break-all text-ink-3">{{ when(exam.startAt) }} 開始，作答 {{ exam.duration }} 分鐘。登入網址：{{ site }}</p>
        </li>
      </ul>
    </section>

    <!-- Grading, results -->
    <EmergencyAccess v-else-if="step === 'access'" :exam="exam" />
    <ExamScoring v-else :key="step" :exam="exam" :view="step" />
  </div>
</template>
