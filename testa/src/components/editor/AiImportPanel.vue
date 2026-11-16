<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { Circle, FileText, LoaderCircle, TriangleAlert, Upload, X } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import Button from '@/components/ui/Button.vue'
import { useBanks } from '@/composables/useBanks'
import { api, ApiError } from '@/lib/api'
import { createQuestion, typeMeta } from '@/lib/question'
import type { Question, QuestionType } from '@/types/question'
import MathText from './MathText.vue'

const emit = defineEmits<{ close: []; done: [] }>()
const store = useBanks()

/** A question drafted by the model and checked by the server; note says what to look at. */
interface Draft {
  type: QuestionType
  stem: string
  points: number
  choices: string[]
  correct: number[]
  blanks: string[][]
  wordLimit: number | null
  note: string | null
}
interface Job {
  id: string
  state: 'running' | 'done' | 'failed'
  questions: Draft[]
  error: string | null
}
interface Detected {
  question: Question
  note: string | null
}

type Stage = 'idle' | 'processing' | 'review'
const stage = ref<Stage>('idle')
const file = ref<{ name: string; size: string } | null>(null)
const detected = ref<Detected[]>([])
const selected = ref<Set<string>>(new Set())
const dragging = ref(false)
const error = ref('')
const seconds = ref(0)

// Each upload gets a run number; a cancelled or replaced run stops polling and ignores its result
let run = 0
let ticker: ReturnType<typeof setInterval> | undefined
onBeforeUnmount(() => {
  run += 1
  clearInterval(ticker)
})

const selectedCount = computed(() => selected.value.size)

function formatSize(bytes: number) {
  return bytes > 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`
}

function accept(picked: File | undefined) {
  if (!picked) return
  if (!/\.(docx|pdf|txt)$/i.test(picked.name)) {
    error.value = `無法讀取「${picked.name}」。請上傳 .docx、.pdf 或 .txt 檔案；舊版 .doc 請先另存成 .docx。`
    return
  }
  if (picked.size > 5 * 1024 * 1024) {
    error.value = '檔案超過 5 MB，請分成幾個檔案匯入。'
    return
  }
  void start(picked)
}

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

async function start(picked: File) {
  const unitId = store.bank.value?.unitId
  if (!unitId) return
  const mine = ++run
  error.value = ''
  file.value = { name: picked.name, size: formatSize(picked.size) }
  stage.value = 'processing'
  seconds.value = 0
  clearInterval(ticker)
  ticker = setInterval(() => (seconds.value += 1), 1000)
  try {
    const form = new FormData()
    form.append('file', picked)
    form.append('unitId', String(unitId))
    let job = await api<Job>('/ai/question-import', { method: 'POST', body: form })
    while (job.state === 'running' && mine === run) {
      await wait(1500)
      job = await api<Job>(`/ai/question-import/${job.id}`)
    }
    if (mine !== run) return
    if (job.state === 'failed') throw new ApiError(job.error ?? '匯入失敗，請再試一次。', 0)
    detected.value = job.questions.map((d) => ({
      note: d.note,
      question: createQuestion(d.type, {
        stem: d.stem,
        points: d.points,
        choices: d.choices.length ? d.choices : undefined,
        correct: d.correct,
        blanks: d.blanks.length ? d.blanks : undefined,
        wordLimit: d.wordLimit,
        review: true,
      }),
    }))
    selected.value = new Set(detected.value.map((d) => d.question.id))
    stage.value = 'review'
  } catch (e) {
    if (mine !== run) return
    error.value = e instanceof ApiError ? e.message : '匯入失敗，請再試一次。'
    stage.value = 'idle'
    file.value = null
  } finally {
    if (mine === run) clearInterval(ticker)
  }
}

function reset() {
  run += 1
  clearInterval(ticker)
  stage.value = 'idle'
  file.value = null
  detected.value = []
}

function toggle(id: string) {
  const next = new Set(selected.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selected.value = next
}

function toggleAll() {
  selected.value =
    selectedCount.value === detected.value.length ? new Set() : new Set(detected.value.map((d) => d.question.id))
}

function confirm() {
  const list = detected.value.filter((d) => selected.value.has(d.question.id)).map((d) => d.question)
  store.insert(list)
  toast(`已匯入 ${list.length} 題`, { description: '匯入的題目已標示為待確認，請逐題核對。' })
  reset()
  emit('done')
}

function onDrop(event: DragEvent) {
  dragging.value = false
  accept(event.dataTransfer?.files[0])
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <header class="flex h-14 shrink-0 items-center gap-3 border-b border-line pr-2 pl-5">
      <h2 class="text-[17px] font-semibold">AI 匯入題目</h2>
      <Button size="icon" class="ml-auto" aria-label="關閉 AI 匯入" @click="emit('close')"><X /></Button>
    </header>

    <!-- 1. Pick a file -->
    <div v-if="stage === 'idle'" class="thin-scroll min-h-0 flex-1 overflow-y-auto px-5 py-6">
      <p class="text-sm text-ink-2">上傳現有的考卷，AI 會辨識題型、選項、配分與答案，拆成可以編輯的題目。每一題都要你核對過才算數。</p>

      <label
        class="mt-4 flex cursor-pointer flex-col items-center rounded-lg border border-dashed px-4 py-9 text-center transition-colors focus-within:border-brand"
        :class="dragging ? 'border-brand bg-brand-soft' : 'border-line-strong hover:border-ink-3 hover:bg-sunken'"
        @dragover.prevent="dragging = true"
        @dragleave.prevent="dragging = false"
        @drop.prevent="onDrop"
      >
        <input
          type="file"
          accept=".docx,.pdf,.txt"
          class="sr-only"
          @change="accept(($event.target as HTMLInputElement).files?.[0])"
        />
        <Upload class="size-5 text-ink-2" />
        <span class="mt-3 text-sm font-medium">把檔案拖到這裡，或點一下選擇檔案</span>
        <span class="mt-1 text-xs text-ink-3">支援 Word（.docx）、PDF 與純文字，5 MB、約 8000 字以內</span>
      </label>

      <p v-if="error" class="mt-3 flex items-start gap-1.5 text-sm text-grade" role="alert">
        <TriangleAlert class="mt-0.5 size-4 shrink-0" />{{ error }}
      </p>

      <p class="mt-5 text-xs leading-relaxed text-ink-3">
        考卷文字只會送到學校設定的 AI 服務。掃描成圖片的 PDF、Word 的方程式物件目前讀不到，請匯入後自行補上。
      </p>
    </div>

    <!-- 2. Parsing -->
    <div v-else-if="stage === 'processing'" class="min-h-0 flex-1 px-5 py-6">
      <div class="flex items-center gap-3">
        <FileText class="size-5 shrink-0 text-ink-2" />
        <div class="min-w-0">
          <p class="truncate text-sm font-medium">{{ file?.name }}</p>
          <p class="text-xs text-ink-3">{{ file?.size }}</p>
        </div>
      </div>
      <p class="mt-6 flex items-center gap-2.5 text-sm" aria-live="polite">
        <LoaderCircle class="size-4 animate-spin text-brand" />AI 正在讀這份考卷，已經 {{ seconds }} 秒
      </p>
      <p class="mt-2 text-xs leading-relaxed text-ink-3">題目多的考卷可能要一兩分鐘，請先不要關掉這個面板。</p>
      <Button variant="outline" class="mt-8" @click="reset">取消</Button>
    </div>

    <!-- 3. Review the result -->
    <template v-else>
      <div class="flex shrink-0 items-center justify-between gap-3 border-b border-line px-5 py-3">
        <div class="min-w-0">
          <p class="truncate text-sm font-medium">找到 {{ detected.length }} 題</p>
          <p class="truncate text-xs text-ink-3">{{ file?.name }}</p>
        </div>
        <button type="button" class="shrink-0 rounded-sm text-sm text-brand underline-offset-2 hover:underline" @click="toggleAll">
          {{ selectedCount === detected.length ? '全部取消' : '全選' }}
        </button>
      </div>

      <ul class="thin-scroll min-h-0 flex-1 divide-y divide-line overflow-y-auto">
        <li v-for="{ question, note } in detected" :key="question.id">
          <button
            type="button"
            role="checkbox"
            :aria-checked="selected.has(question.id)"
            class="flex w-full items-start gap-3 px-5 py-3 text-left transition-colors hover:bg-sunken"
            @click="toggle(question.id)"
          >
            <span
              class="mt-1 flex size-4 shrink-0 items-center justify-center rounded-[4px] border transition-colors"
              :class="selected.has(question.id) ? 'border-brand bg-brand text-brand-fg' : 'border-line-strong'"
            >
              <Circle v-if="selected.has(question.id)" class="size-2 fill-current" />
            </span>
            <span class="min-w-0 flex-1">
              <MathText :text="question.stem" tag="span" class="exam-text block text-[15px]" />
              <span class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-ink-3">
                <span>{{ typeMeta(question.type).label }}題</span>
                <span v-if="question.type === 'single' || question.type === 'multiple'">{{ question.choices.length }} 個選項</span>
                <span v-if="note" class="rounded-[4px] bg-marker px-1.5 py-0.5 text-marker-ink">{{ note }}</span>
              </span>
            </span>
          </button>
        </li>
      </ul>

      <footer class="flex shrink-0 items-center justify-between gap-2 border-t border-line px-4 py-3">
        <Button size="md" @click="reset">換一個檔案</Button>
        <Button variant="primary" size="md" :disabled="selectedCount === 0" @click="confirm">
          匯入 {{ selectedCount }} 題
        </Button>
      </footer>
    </template>
  </div>
</template>
