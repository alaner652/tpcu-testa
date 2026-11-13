<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronLeft, LoaderCircle, Printer, Upload } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import Button from '@/components/ui/Button.vue'
import ExamScoring from '@/components/app/ExamScoring.vue'
import { useConfirm } from '@/composables/useConfirm'
import { CANDIDATE_STATUS, EXAM_STATUS, MODE_LABEL, useExams, when, type Candidate, type RosterPreview } from '@/composables/useExams'

/**
 * 一場考試要做的事都在這一頁，照順序三步：考生名單 → 登入單 → 監考。
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
const fail = (e: unknown) => toast.error((e as Error).message)

type Step = 'roster' | 'tickets' | 'monitor' | 'grading' | 'results'
// 評分與成績只有負責人看得到（監考碰不到分數）
const STEPS = computed<{ value: Step; label: string }[]>(() => [
  { value: 'roster', label: '考生名單' },
  { value: 'tickets', label: '登入單' },
  { value: 'monitor', label: '監考' },
  ...(exam.value?.canManage && exam.value.status !== 'draft'
    ? ([
        { value: 'grading', label: '評分' },
        { value: 'results', label: '成績' },
      ] as const)
    : []),
])
const step = ref<Step>('roster')

async function loadList() {
  if (!exam.value) return
  try {
    list.value = await store.candidates(exam.value.id)
    listError.value = ''
  } catch (e) {
    listError.value = (e as Error).message
  }
}

// 考試清單載入後（或直接開這一頁時）再抓名單；進行中的考試預設停在監考
let opened = false
watch(
  () => exam.value?.id,
  (id) => {
    if (id == null) return
    if (!opened && exam.value?.status === 'live') step.value = 'monitor'
    opened = true
    loadList()
  },
  { immediate: true },
)

// 監考：每 10 秒更新一次狀態
let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  timer = setInterval(() => {
    if (step.value !== 'monitor' || document.hidden || !exam.value) return
    loadList()
    store.refresh(exam.value.id).catch(() => {})
  }, 10000)
})
onBeforeUnmount(() => clearInterval(timer))

// 匯入：選檔案 → 伺服器檢查 → 確認後匯入
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
      </div>
      <p class="mt-1.5 text-[15px] text-ink-2">
        {{ when(exam.startAt) }}，作答 {{ exam.duration }} 分鐘，{{ MODE_LABEL[exam.mode] }}。題庫：<RouterLink
          v-if="exam.bankId"
          :to="`/banks/${exam.bankId}`"
          class="rounded-sm text-brand underline-offset-2 hover:underline"
          >{{ exam.bankName }}</RouterLink
        ><template v-else>{{ exam.bankName ?? '題庫已刪除' }}</template>
      </p>
    </div>

    <!-- 三個步驟 -->
    <div role="tablist" aria-label="考試步驟" class="mt-5 mb-4 flex flex-wrap gap-1 px-4 md:px-0">
      <button
        v-for="(s, i) in STEPS"
        :key="s.value"
        type="button"
        role="tab"
        :aria-selected="step === s.value"
        class="flex h-11 items-center gap-2 rounded-full pr-4 pl-1.5 text-[15px] transition-colors"
        :class="step === s.value ? 'bg-brand font-medium text-brand-fg' : 'text-ink-2 hover:bg-ink/[0.06] hover:text-ink'"
        @click="step = s.value"
      >
        <span
          class="flex size-8 items-center justify-center rounded-full text-sm font-semibold"
          :class="step === s.value ? 'bg-brand-fg/20' : 'bg-paper text-ink'"
        >
          {{ i + 1 }}
        </span>
        {{ s.label }}
      </button>
    </div>

    <p v-if="listError" class="card mb-3 flex items-center gap-3 px-5 py-4 text-[15px] text-grade sm:px-6" role="alert">
      <span class="min-w-0 flex-1">{{ listError }}</span>
      <Button variant="outline" class="rounded-full" @click="loadList">重試</Button>
    </p>

    <!-- 1 考生名單 -->
    <section v-if="step === 'roster'" class="card">
      <div class="flex flex-wrap items-center justify-between gap-3 px-5 py-4 sm:px-6">
        <p class="text-[15px] text-ink-2">
          只有名單上的考生能登入這場考試。目前 <strong class="font-semibold text-ink">{{ list.length }}</strong> 位。
        </p>
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
          <Button size="md" class="rounded-full" @click="preview = null">取消</Button>
          <Button variant="primary" size="md" class="rounded-full" :disabled="busy || !preview.rows.length" @click="doImport">
            匯入 {{ preview.rows.length }} 位考生
          </Button>
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
          <span class="rounded-full px-2.5 py-0.5 text-[13px] font-medium" :class="CANDIDATE_STATUS[c.status].class">
            {{ CANDIDATE_STATUS[c.status].label }}
          </span>
          <Button variant="outline" class="rounded-full" :disabled="c.status === 'DONE'" @click="reset(c)">重設登入碼</Button>
          <Button v-if="exam.canManage && c.status === 'IDLE'" variant="danger" class="rounded-full" @click="removeCandidate(c)">移除</Button>
        </li>
      </ul>
    </section>

    <!-- 2 登入單 -->
    <section v-else-if="step === 'tickets'">
      <div class="no-print card flex flex-wrap items-center justify-between gap-3 px-5 py-4 sm:px-6">
        <p class="min-w-0 flex-1 basis-64 text-[15px] text-ink-2">
          每位考生一張，考場現場發放。考生用上面的准考證號和登入碼登入，不需要 Email。登入碼等同密碼，請妥善保管印出的紙本。
        </p>
        <Button variant="primary" class="rounded-full" :disabled="list.length === 0" @click="print">
          <Printer />列印 {{ list.length }} 張
        </Button>
      </div>
      <p v-if="list.length === 0" class="mt-3 px-5 text-[15px] text-ink-2 md:px-1">先到「考生名單」匯入考生。</p>
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
          <p class="mt-3 text-[12px] break-all text-ink-3">{{ when(exam.startAt) }}開始，作答 {{ exam.duration }} 分鐘。登入網址：{{ site }}</p>
        </li>
      </ul>
    </section>

    <!-- 4 評分、5 成績 -->
    <ExamScoring v-else-if="step === 'grading' || step === 'results'" :key="step" :exam="exam" :view="step" />

    <!-- 3 監考 -->
    <section v-else class="space-y-3">
      <div class="card flex flex-wrap items-baseline gap-x-10 gap-y-2 px-5 py-4 sm:px-6">
        <p><strong class="text-[28px] font-bold tabular-nums">{{ count('IN') }}</strong> <span class="text-ink-2">作答中</span></p>
        <p><strong class="text-[28px] font-bold text-brand tabular-nums">{{ count('DONE') }}</strong> <span class="text-ink-2">已交卷</span></p>
        <p><strong class="text-[28px] font-bold tabular-nums">{{ count('IDLE') }}</strong> <span class="text-ink-2">尚未登入</span></p>
        <p v-if="locked.length">
          <strong class="text-[28px] font-bold text-grade tabular-nums">{{ locked.length }}</strong> <span class="text-ink-2">需要注意</span>
        </p>
      </div>

      <div class="card">
        <p v-if="list.length === 0" class="px-5 py-10 text-[15px] text-ink-2 sm:px-6">還沒有考生。先到「考生名單」匯入。</p>
        <ul class="divide-y divide-line">
          <li v-for="c in list" :key="c.id" class="flex flex-wrap items-center gap-x-4 gap-y-1.5 px-5 py-3 sm:px-6">
            <span class="w-8 text-sm text-ink-3 tabular-nums" title="座位">{{ c.seat }}</span>
            <div class="min-w-0 flex-1 basis-40">
              <p class="text-base font-medium">{{ c.name }}</p>
              <p class="text-sm text-ink-2 tabular-nums">{{ c.ticket }}</p>
            </div>
            <span v-if="c.locked" class="rounded-full bg-marker px-2.5 py-0.5 text-[13px] font-medium text-marker-ink">登入碼輸錯太多次，已鎖定</span>
            <span v-if="c.leaveCount" class="rounded-full bg-marker px-2.5 py-0.5 text-[13px] font-medium text-marker-ink">離開考試畫面 {{ c.leaveCount }} 次</span>
            <span class="rounded-full px-2.5 py-0.5 text-[13px] font-medium" :class="CANDIDATE_STATUS[c.status].class">
              {{ CANDIDATE_STATUS[c.status].label }}
            </span>
            <Button v-if="c.status !== 'DONE'" variant="outline" class="rounded-full" @click="reset(c)">重設登入碼</Button>
          </li>
        </ul>
      </div>
      <p class="px-5 text-sm text-ink-2 md:px-1">這一頁每 10 秒自動更新。考生開始作答、交卷後，狀態會跟著改變。</p>
    </section>
  </div>
</template>
