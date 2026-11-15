<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { Check, ChevronLeft, ChevronRight, Clock, Flag, Minus, Pencil, RotateCcw, Send, X } from 'lucide-vue-next'
import Badge, { type BadgeTone } from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import { useConfirm } from '@/composables/useConfirm'
import { emptyAttempt, grade, isAnswered, type Attempt, type GradeStatus, type RemoteExam } from '@/lib/grading'
import type { Bank } from '@/types/question'
import StudentQuestion from './StudentQuestion.vue'

/**
 * Try-it mode: the teacher goes through the whole flow as a candidate: timer, answering, flags, submit, auto-grading.
 * Answers live in memory only and are never written back to the bank.
 */
const props = defineProps<{
  bank: Bank
  /** real: a candidate's real exam; by default the teacher's try-it mode */
  real?: boolean
  /** Exam title and time limit; defaults to the bank's settings */
  title?: string
  duration?: number
  /** When given: answers are saved on the server, the server owns the clock, and no grading result is shown after submitting (candidates never get answers) */
  remote?: RemoteExam
}>()
const emit = defineEmits<{
  edit: [id?: string]
  exit: []
  submitted: [result: { earned: number; total: number; pending: number }]
}>()

const bank = computed(() => props.bank)
const questions = computed(() => props.bank.questions)
const totalPoints = computed(() => questions.value.reduce((sum, q) => sum + (q.points || 0), 0))
const { confirm } = useConfirm()

const attempts = reactive<Record<string, Attempt>>({})
const attemptOf = (id: string) => (attempts[id] ??= emptyAttempt())
for (const [id, saved] of Object.entries(props.remote?.initial ?? {})) attempts[id] = { ...emptyAttempt(), ...saved }

const current = ref(0)
const submitted = ref(false)
const question = computed(() => questions.value[current.value] ?? null)
watch(questions, (list) => (current.value = Math.min(current.value, Math.max(0, list.length - 1))))

const answeredCount = computed(() => questions.value.filter((q) => isAnswered(q, attempts[q.id])).length)
const unanswered = computed(() => questions.value.length - answeredCount.value)

// Countdown
const total = computed(() => (props.duration ?? props.bank.duration ?? 60) * 60)
const elapsed = ref(0)
// Real exam: derive the deadline from the server's remaining time and resync on every save
const deadline = ref(Date.now() + (props.remote?.remainingSeconds ?? 0) * 1000)
const now = ref(Date.now())
const remaining = computed(() =>
  props.remote ? Math.max(0, Math.ceil((deadline.value - now.value) / 1000)) : Math.max(0, total.value - elapsed.value),
)
const clock = computed(() => {
  const m = Math.floor(remaining.value / 60)
  const s = remaining.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})
const timer = setInterval(() => {
  if (submitted.value || questions.value.length === 0) return
  elapsed.value += 1
  now.value = Date.now()
  if (remaining.value === 0 && !submitting.value) onTimeUp()
  else if (props.remote?.sync && elapsed.value % SYNC_EVERY === 0) syncClock()
}, 1000)

// Real exam: sync with the server periodically, so extra time from a proctor extends the countdown
const SYNC_EVERY = 30
let syncing = false
async function syncClock(): Promise<number | null> {
  if (!props.remote?.sync || syncing) return null
  syncing = true
  try {
    const left = await props.remote.sync()
    if (submitted.value) return left
    const before = remaining.value
    if (left > 0) deadline.value = Date.now() + left * 1000
    // Only notify when at least a minute was added, so latency is not mistaken for extra time
    if (left - before >= 50) toast.success(`監考人員已延長你的作答時間，約 ${Math.round((left - before) / 60)} 分鐘`, { duration: 10000 })
    return left
  } catch {
    return null
  } finally {
    syncing = false
  }
}

/** The clock ran out on screen: ask the server first (time may just have been added), then submit. */
let checkingTimeUp = false
async function onTimeUp() {
  if (checkingTimeUp) return
  checkingTimeUp = true
  try {
    const left = await syncClock()
    if (left != null && left > 0) return
    await submit('作答時間到，已自動交卷', true)
  } finally {
    checkingTimeUp = false
  }
}
onBeforeUnmount(() => {
  clearInterval(timer)
  clearTimeout(saveTimer)
})

// ── Real exam: autosave ──────────────────────────────
type SaveState = 'saved' | 'saving' | 'error'
const saveState = ref<SaveState>('saved')
const lastSent = new Map<string, string>()
const answerOf = (a: Attempt) => ({ choices: a.choices, blanks: a.blanks, essay: a.essay })
for (const [id, a] of Object.entries(attempts)) lastSent.set(id, JSON.stringify(answerOf(a)))
let saveTimer: ReturnType<typeof setTimeout> | undefined
let saving: Promise<boolean> | null = null

/** Sends unsaved answers to the server; true when everything is saved. */
async function flushSaves(): Promise<boolean> {
  const remote = props.remote
  if (!remote) return true
  clearTimeout(saveTimer)
  if (saving) {
    await saving
    return flushSaves()
  }
  saving = (async () => {
    let ok = true
    for (const [id, a] of Object.entries(attempts)) {
      const body = JSON.stringify(answerOf(a))
      if (body === (lastSent.get(id) ?? JSON.stringify(answerOf(emptyAttempt())))) continue
      saveState.value = 'saving'
      try {
        const left = await remote.save(id, JSON.parse(body))
        lastSent.set(id, body)
        deadline.value = Date.now() + left * 1000
      } catch (e) {
        // 409: the server already closed the attempt (time-up, exam ended, submitted)
        if ((e as { status?: number }).status === 409) {
          finish((e as Error).message)
          return false
        }
        ok = false
      }
    }
    saveState.value = ok ? 'saved' : 'error'
    // Network down for now: keep answers on screen and resend shortly
    if (!ok && !submitted.value) saveTimer = setTimeout(flushSaves, 4000)
    return ok
  })()
  try {
    return await saving
  } finally {
    saving = null
  }
}

if (props.remote) {
  watch(attempts, () => {
    if (submitted.value) return
    clearTimeout(saveTimer)
    saveTimer = setTimeout(flushSaves, 700)
  })
}

const submitting = ref(false)

function finish(message: string) {
  submitted.value = true
  toast.success(message)
  emit('submitted', { earned: earned.value, total: objectiveTotal.value, pending: pendingPoints.value })
}

async function submit(message = '已交卷，自動批改完成', timeUp = false) {
  if (submitted.value || submitting.value) return
  if (!props.remote) return finish(message)
  submitting.value = true
  try {
    const saved = await flushSaves()
    if (submitted.value) return
    if (!saved && !timeUp) {
      toast.error('網路不穩，有答案還沒存到伺服器。請確認網路後再按一次交卷。')
      return
    }
    await props.remote.submit()
    finish(timeUp ? message : '已交卷')
  } catch (e) {
    // Time is up but the server is unreachable: it will close the attempt itself, so stop answering here
    if (timeUp) finish(message)
    else toast.error((e as Error).message)
  } finally {
    submitting.value = false
  }
}

async function requestSubmit() {
  const ok = await confirm({
    title: '確定要交卷嗎？',
    description: unanswered.value
      ? `還有 ${unanswered.value} 題未作答。交卷後無法再修改答案。`
      : '所有題目都已作答。交卷後無法再修改答案。',
    confirmLabel: '交卷',
    cancelLabel: '繼續作答',
  })
  if (ok) submit()
}

async function restart() {
  const ok = await confirm({
    title: '重新作答？',
    description: '這次的作答內容與批改結果會被清除，計時重新開始。',
    confirmLabel: '重新作答',
  })
  if (!ok) return
  for (const id of Object.keys(attempts)) delete attempts[id]
  elapsed.value = 0
  current.value = 0
  submitted.value = false
  toast.success('已重新開始作答')
}

// Lets the parent know about an unsubmitted attempt before leaving try-it mode
defineExpose({ hasProgress: () => !submitted.value && answeredCount.value > 0 })

// Grading result
const grades = computed(() => questions.value.map((q) => grade(q, attempts[q.id])))
const objective = computed(() => questions.value.filter((q) => q.type !== 'essay'))
const objectiveTotal = computed(() => objective.value.reduce((sum, q) => sum + q.points, 0))
const earned = computed(() => Math.round(grades.value.reduce((sum, g) => sum + g.earned, 0) * 100) / 100)
const pendingPoints = computed(() => totalPoints.value - objectiveTotal.value)
const correctCount = computed(() => grades.value.filter((g) => g.status === 'correct').length)

const STATUS: Record<GradeStatus, { label: string; tone: BadgeTone }> = {
  correct: { label: '答對', tone: 'done' },
  partial: { label: '部分答對', tone: 'warn' },
  wrong: { label: '答錯', tone: 'danger' },
  unanswered: { label: '未作答', tone: 'neutral' },
  pending: { label: '待閱卷', tone: 'warn' },
}

function cellClass(index: number) {
  const q = questions.value[index]
  if (submitted.value) {
    const status = grades.value[index].status
    if (status === 'correct') return 'bg-brand text-brand-fg'
    if (status === 'wrong') return 'bg-grade text-white dark:text-desk'
    if (status === 'unanswered') return 'text-ink-3 shadow-[inset_0_0_0_1.5px_var(--line-strong)]'
    return 'bg-marker text-marker-ink'
  }
  return isAnswered(q, attempts[q.id])
    ? 'bg-ink text-paper'
    : 'text-ink-2 shadow-[inset_0_0_0_1.5px_var(--line-strong)] hover:shadow-[inset_0_0_0_1.5px_var(--ink)]'
}

function jump(index: number) {
  current.value = index
  if (submitted.value) document.getElementById(`r-${questions.value[index].id}`)?.scrollIntoView({ block: 'start', behavior: 'smooth' })
}
</script>

<template>
  <div>
    <!-- Status bar. After a real exam is submitted the card below says it all -->
    <div v-if="!(remote && submitted)" class="sticky top-0 z-10 border-b border-line bg-paper">
      <div class="mx-auto flex h-14 max-w-[1040px] items-center gap-3 px-4 sm:gap-5 md:px-6">
        <template v-if="!submitted">
          <p
            class="flex items-center gap-1.5 text-lg font-semibold tabular-nums"
            :class="remaining <= 300 && 'text-grade'"
            role="timer"
            :aria-label="`剩餘時間 ${clock}`"
          >
            <Clock class="size-4 text-ink-3" />{{ clock }}
          </p>
          <div class="min-w-0 flex-1">
            <p class="text-[13px] text-ink-2">已作答 {{ answeredCount }} / {{ questions.length }} 題</p>
            <div class="mt-1 h-1.5 overflow-hidden rounded-full bg-sunken">
              <div
                class="h-full rounded-full bg-brand transition-[width] duration-200"
                :style="{ width: `${questions.length ? (answeredCount / questions.length) * 100 : 0}%` }"
              />
            </div>
          </div>
          <Button variant="primary" :disabled="questions.length === 0 || submitting" @click="requestSubmit">
            <Send />{{ submitting ? '交卷中…' : '交卷' }}
          </Button>
        </template>
        <template v-else>
          <p class="min-w-0 flex-1 truncate text-[15px] font-medium">已交卷，以下是自動批改結果</p>
          <button
            type="button"
            class="flex h-9 shrink-0 items-center gap-1.5 rounded-full border border-line-strong px-4 text-sm font-medium hover:border-brand hover:text-brand"
            @click="restart"
          >
            <RotateCcw class="size-4" />重新作答
          </button>
        </template>
      </div>
    </div>

    <div class="mx-auto grid max-w-[1040px] items-start gap-6 py-3 md:px-6 md:py-7 lg:grid-cols-[minmax(0,1fr)_15rem]">
      <!-- No questions -->
      <div v-if="questions.length === 0" class="bg-paper px-5 py-12 shadow-card sm:px-10 md:rounded-card lg:col-span-2">
        <h2 class=" text-2xl font-semibold">還沒有可以作答的題目</h2>
        <p class="mt-2 mb-6 text-[15px] text-ink-2">
          {{ real ? '這場考試還沒有題目，請聯絡監考老師。' : '回到編輯模式新增題目後，就能在這裡以考生身分試考一次。' }}
        </p>
        <Button v-if="!real" variant="primary" size="md" @click="emit('edit')"><Pencil />回到編輯</Button>
      </div>

      <!-- Answering: one question at a time -->
      <div v-else-if="!submitted && question" class="min-w-0">
        <section class="bg-paper px-5 pt-5 pb-5 shadow-card sm:px-10 sm:pt-7 md:rounded-card">
          <p class="mb-3 flex items-center justify-between gap-3 text-sm text-ink-2">
            <span class="tabular-nums">第 {{ current + 1 }} 題，共 {{ questions.length }} 題</span>
            <button
              type="button"
              class="inline-flex h-9 items-center gap-1.5 rounded-full px-3 transition-colors"
              :class="attemptOf(question.id).flagged ? 'bg-marker text-marker-ink' : 'hover:bg-ink/[0.06] hover:text-ink'"
              :aria-pressed="attemptOf(question.id).flagged"
              @click="attemptOf(question.id).flagged = !attemptOf(question.id).flagged"
            >
              <Flag class="size-4" />{{ attemptOf(question.id).flagged ? '已標記' : '標記此題' }}
            </button>
          </p>
          <StudentQuestion
            :key="question.id"
            :question="question"
            :index="current"
            :attempt="attemptOf(question.id)"
            :show-answers="false"
          />
          <footer class="mt-8 flex items-center justify-between border-t border-line pt-4">
            <Button variant="outline" size="md" :disabled="current === 0" @click="current -= 1">
              <ChevronLeft />上一題
            </Button>
            <Button
              v-if="current < questions.length - 1"
              variant="primary"
              size="md"
              @click="current += 1"
            >
              下一題<ChevronRight />
            </Button>
            <Button v-else variant="primary" size="md" @click="requestSubmit"><Send />交卷</Button>
          </footer>
        </section>
        <p v-if="remote" class="mt-3 px-5 text-[13px] md:px-1" :class="saveState === 'error' ? 'font-medium text-grade' : 'text-ink-3'" role="status">
          {{
            saveState === 'error'
              ? '網路連不上，剛才的作答還沒存到伺服器。請不要關閉這個畫面，連線恢復後會自動重送。'
              : saveState === 'saving'
                ? '儲存中…'
                : '作答內容已自動儲存。'
          }}
        </p>
        <p v-else class="mt-3 px-5 text-[13px] text-ink-3 md:px-1">
          <template v-if="real">作答內容會自動儲存。</template><template v-else>這是模擬作答，不會留下紀錄。</template>
        </p>
      </div>

      <!-- Real exam submitted: confirm only; results come after grading -->
      <div v-else-if="remote" class="min-w-0 lg:col-span-2">
        <section class="bg-paper px-5 py-12 shadow-card sm:px-10 md:rounded-card">
          <h2 class="text-3xl font-bold">已交卷</h2>
          <p class="mt-3 text-[15px] text-ink-2">你的作答已經送出。成績會在評分完成後公布，現在可以離開了。</p>
          <Button variant="primary" size="md" class="mt-6" @click="emit('exit')"><ChevronLeft />回到我的考試</Button>
        </section>
      </div>

      <!-- Submitted: score and per-question review -->
      <div v-else class="min-w-0 space-y-3">
        <section class="bg-paper px-5 py-7 shadow-card sm:px-10 md:rounded-card">
          <p class="text-sm text-ink-2">{{ title ?? bank.name }}</p>
          <p class="mt-2 flex items-baseline gap-2">
            <span class="text-6xl leading-none font-bold text-ink tabular-nums">{{ earned }}</span>
            <span class="text-xl text-ink-2 tabular-nums">/ {{ objectiveTotal }} 分</span>
          </p>
          <p class="mt-3 text-[15px] text-ink-2">
            自動批改 {{ objective.length }} 題，答對 {{ correctCount }} 題。<template v-if="pendingPoints"
              >另有申論題 {{ pendingPoints }} 分，需要教師閱卷後才會計入。</template
            >
          </p>
        </section>

        <section
          v-for="(q, index) in questions"
          :id="`r-${q.id}`"
          :key="q.id"
          class="relative scroll-mt-20 bg-paper px-5 py-6 shadow-card sm:px-10 md:rounded-card"
        >
          <p class="mb-3 flex items-center gap-2 pl-9 sm:pl-10">
            <Badge :tone="STATUS[grades[index].status].tone">
              <Check v-if="grades[index].status === 'correct'" />
              <X v-else-if="grades[index].status === 'wrong'" />
              <Minus v-else-if="grades[index].status === 'unanswered'" />
              {{ STATUS[grades[index].status].label }}
            </Badge>
            <button
              v-if="!real"
              type="button"
              class="ml-auto inline-flex h-8 items-center gap-1.5 rounded-full px-2.5 text-[13px] text-ink-2 hover:bg-ink/[0.06] hover:text-ink"
              @click="emit('edit', q.id)"
            >
              <Pencil class="size-3.5" />編輯這題
            </button>
          </p>
          <StudentQuestion :question="q" :index="index" :attempt="attemptOf(q.id)" :show-answers="true" locked>
            <template #score>
              <template v-if="q.type === 'essay'">{{ q.points }} 分</template>
              <template v-else><strong class="font-semibold text-ink">{{ grades[index].earned }}</strong> / {{ q.points }} 分</template>
            </template>
          </StudentQuestion>
        </section>
      </div>

      <!-- Answer sheet -->
      <aside v-if="questions.length && !(remote && submitted)" class="bg-paper p-5 shadow-card max-lg:mb-6 md:rounded-card lg:sticky lg:top-[5.25rem]">
        <h2 class="text-[15px] font-semibold">答案卡</h2>
        <ol class="mt-3 grid grid-cols-8 gap-2 lg:grid-cols-5">
          <li v-for="(q, index) in questions" :key="q.id" class="relative">
            <button
              type="button"
              class="flex aspect-square w-full items-center justify-center rounded-full text-sm font-medium tabular-nums transition-all duration-100"
              :class="[cellClass(index), !submitted && index === current && 'ring-2 ring-brand ring-offset-2 ring-offset-paper']"
              :aria-current="!submitted && index === current ? 'true' : undefined"
              :aria-label="`第 ${index + 1} 題`"
              @click="jump(index)"
            >
              {{ index + 1 }}
            </button>
            <span
              v-if="!submitted && attempts[q.id]?.flagged"
              class="absolute -top-0.5 -right-0.5 size-3 rounded-full bg-marker shadow-[0_0_0_2px_var(--paper),inset_0_0_0_1px_var(--marker-ink)]"
              title="已標記"
            />
          </li>
        </ol>
        <ul class="mt-4 flex flex-wrap gap-x-4 gap-y-1.5 text-[13px] text-ink-2 lg:flex-col">
          <template v-if="!submitted">
            <li class="flex items-center gap-2"><span class="size-3 rounded-full bg-ink" />已作答</li>
            <li class="flex items-center gap-2"><span class="size-3 rounded-full shadow-[inset_0_0_0_1.5px_var(--line-strong)]" />未作答</li>
            <li class="flex items-center gap-2"><span class="size-3 rounded-full bg-marker shadow-[inset_0_0_0_1px_var(--marker-ink)]" />已標記</li>
          </template>
          <template v-else>
            <li class="flex items-center gap-2"><span class="size-3 rounded-full bg-brand" />答對</li>
            <li class="flex items-center gap-2"><span class="size-3 rounded-full bg-grade" />答錯</li>
            <li class="flex items-center gap-2"><span class="size-3 rounded-full bg-marker shadow-[inset_0_0_0_1px_var(--marker-ink)]" />部分答對或待閱卷</li>
            <li class="flex items-center gap-2"><span class="size-3 rounded-full shadow-[inset_0_0_0_1.5px_var(--line-strong)]" />未作答</li>
          </template>
        </ul>
      </aside>
    </div>
  </div>
</template>
