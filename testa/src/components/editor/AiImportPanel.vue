<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { Check, FileText, LoaderCircle, TriangleAlert, Upload, X } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import Button from '@/components/ui/Button.vue'
import { useBanks } from '@/composables/useBanks'
import { createDetectedQuestions, IMPORT_STEPS, SAMPLE_FILE, type DetectedQuestion } from '@/data/mock-import'
import { typeMeta } from '@/lib/question'
import MathText from './MathText.vue'

const emit = defineEmits<{ close: []; done: [] }>()
const store = useBanks()

type Stage = 'idle' | 'processing' | 'review'
const stage = ref<Stage>('idle')
const file = ref<{ name: string; size: string } | null>(null)
const stepIndex = ref(0)
const detected = ref<DetectedQuestion[]>([])
const selected = ref<Set<string>>(new Set())
const dragging = ref(false)
const error = ref('')

let timer: ReturnType<typeof setTimeout> | undefined
onBeforeUnmount(() => clearTimeout(timer))

const selectedCount = computed(() => selected.value.size)

function formatSize(bytes: number) {
  return bytes > 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`
}

function accept(picked: File | undefined) {
  if (!picked) return
  if (!/\.(docx?|pdf)$/i.test(picked.name)) {
    error.value = `無法讀取「${picked.name}」。請上傳 .docx、.doc 或 .pdf 檔案。`
    return
  }
  start({ name: picked.name, size: formatSize(picked.size) })
}

/** Simulated parsing: walk through each step, then produce the result. */
function start(target: { name: string; size: string }) {
  error.value = ''
  file.value = target
  stage.value = 'processing'
  stepIndex.value = 0
  const advance = () => {
    if (stepIndex.value < IMPORT_STEPS.length - 1) {
      stepIndex.value += 1
      timer = setTimeout(advance, 750)
    } else {
      detected.value = createDetectedQuestions()
      selected.value = new Set(detected.value.map((d) => d.question.id))
      stage.value = 'review'
    }
  }
  timer = setTimeout(advance, 750)
}

function reset() {
  clearTimeout(timer)
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
      <p class="text-sm text-ink-2">上傳現有的考卷，AI 會辨識題號、題型、選項與數學公式，拆成可以編輯的題目。</p>

      <label
        class="mt-4 flex cursor-pointer flex-col items-center rounded-lg border border-dashed px-4 py-9 text-center transition-colors focus-within:border-brand"
        :class="dragging ? 'border-brand bg-brand-soft' : 'border-line-strong hover:border-ink-3 hover:bg-sunken'"
        @dragover.prevent="dragging = true"
        @dragleave.prevent="dragging = false"
        @drop.prevent="onDrop"
      >
        <input
          type="file"
          accept=".doc,.docx,.pdf"
          class="sr-only"
          @change="accept(($event.target as HTMLInputElement).files?.[0])"
        />
        <Upload class="size-5 text-ink-2" />
        <span class="mt-3 text-sm font-medium">把檔案拖到這裡，或點一下選擇檔案</span>
        <span class="mt-1 text-xs text-ink-3">支援 Word（.docx、.doc）與 PDF，單檔 20 MB 以內</span>
      </label>

      <p v-if="error" class="mt-3 flex items-start gap-1.5 text-sm text-grade" role="alert">
        <TriangleAlert class="mt-0.5 size-4 shrink-0" />{{ error }}
      </p>

      <div class="mt-5 flex items-center justify-between gap-3 rounded-control bg-sunken px-3 py-2.5">
        <p class="text-sm text-ink-2">手邊沒有檔案？</p>
        <Button variant="outline" @click="start(SAMPLE_FILE)">用範例考卷試試</Button>
      </div>

      <p class="mt-5 text-xs leading-relaxed text-ink-3">
        這是原型，檔案不會上傳到任何伺服器，辨識結果為示範資料。
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
      <ol class="mt-6 space-y-3" aria-live="polite">
        <li
          v-for="(name, index) in IMPORT_STEPS"
          :key="name"
          class="flex items-center gap-2.5 text-sm"
          :class="index > stepIndex ? 'text-ink-3' : 'text-ink'"
        >
          <span class="flex size-5 items-center justify-center">
            <Check v-if="index < stepIndex" class="size-4 text-brand" />
            <LoaderCircle v-else-if="index === stepIndex" class="size-4 animate-spin text-brand" />
            <span v-else class="size-1.5 rounded-full bg-line-strong" />
          </span>
          {{ name }}
        </li>
      </ol>
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
              <Check v-if="selected.has(question.id)" class="size-3" :stroke-width="3" />
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
