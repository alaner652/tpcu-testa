<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Check } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import Button from '@/components/ui/Button.vue'
import MathText from '@/components/editor/MathText.vue'
import PointsStepper from '@/components/editor/PointsStepper.vue'
import { useGrading, type GradingItem } from '@/composables/useGrading'

/** Grading: one anonymous paper at a time, the next opens after submitting. Other graders' scores are never shown. */
const { exams, load, items: fetchItems, score: sendScore } = useGrading()

const examId = ref<number | null>(null)
const items = ref<GradingItem[]>([])
const loading = ref(true)
const error = ref('')
const sending = ref(false)

const exam = computed(() => exams.value.find((e) => e.examId === examId.value) ?? null)
const total = computed(() => items.value.length)
const done = computed(() => items.value.filter((i) => i.myScore !== null).length)
const current = computed(() => items.value.find((i) => i.myScore === null) ?? null)

/** Starts blank so a grader cannot submit 0 by accident; submitted scores cannot be changed by the grader */
const score = ref<number | null>(null)
const comment = ref('')
const missing = ref(false)
watch(current, () => {
  score.value = null
  comment.value = ''
  missing.value = false
})
watch(score, (v) => v !== null && (missing.value = false))

async function open(id: number) {
  examId.value = id
  loading.value = true
  try {
    items.value = await fetchItems(id)
    error.value = ''
  } catch (e) {
    items.value = []
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await load()
  // Open the exam that still has answers to grade first
  const first = exams.value.find((e) => e.done < e.total) ?? exams.value[0]
  if (first) await open(first.examId)
  else loading.value = false
})

async function submit() {
  const item = current.value
  // Over full marks already shows a red hint. The button stays enabled: the score field updates on blur,
  // so a disabled button would only light up on the first click and need a second one to submit
  const value = score.value
  if (!item || examId.value == null || sending.value) return
  if (value === null) {
    missing.value = true
    return
  }
  if (value > item.points) return
  sending.value = true
  try {
    await sendScore(examId.value, item, value, comment.value)
    item.myScore = value
    item.myComment = comment.value
    toast.success(`已送出 ${item.anonId} 第 ${item.number} 題：${value} 分`)
    load()
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-[760px]">
    <div class="mb-5 px-4 md:px-0">
      <h1 class="text-[26px] font-bold sm:text-[30px]">閱卷</h1>
      <template v-if="exams.length">
        <select
          v-if="exams.length > 1"
          class="field mt-3 max-w-md bg-paper"
          aria-label="選擇考試"
          :value="examId ?? ''"
          @change="open(Number(($event.target as HTMLSelectElement).value))"
        >
          <option v-for="e in exams" :key="e.examId" :value="e.examId">{{ e.name }}（{{ e.done }} / {{ e.total }}）</option>
        </select>
        <p class="mt-1 text-[15px] text-ink-2 tabular-nums">
          <template v-if="exams.length === 1">{{ exam?.name }}<template v-if="total">，</template></template>
          <template v-if="total">已完成 {{ done }} / {{ total }} 份</template>
        </p>
        <div v-if="total" class="mt-2 h-1.5 overflow-hidden rounded-full bg-paper">
          <div class="h-full rounded-full bg-brand transition-[width] duration-300" :style="{ width: `${total ? (done / total) * 100 : 0}%` }" />
        </div>
      </template>
    </div>

    <p v-if="loading" class="px-4 text-[15px] text-ink-2 md:px-0">載入中…</p>

    <div v-else-if="error" class="card px-5 py-10 sm:px-10">
      <h2 class="text-xl font-bold">現在無法評分</h2>
      <p class="mt-2 text-[15px] text-ink-2">{{ error }}</p>
    </div>

    <div v-else-if="!exams.length" class="card px-5 py-12 sm:px-10">
      <h2 class="text-2xl font-bold">目前沒有要評的考試</h2>
      <p class="mt-2 text-[15px] text-ink-2">承辦人把你指派為某場考試的評分者後，考生的申論答案會出現在這裡。</p>
    </div>

    <div v-else-if="!current" class="card px-5 py-12 text-center sm:px-10">
      <span class="mx-auto flex size-14 items-center justify-center rounded-full bg-brand-soft text-brand-strong">
        <Check class="size-7" />
      </span>
      <h2 class="mt-4 text-2xl font-bold">都批完了</h2>
      <p class="mt-2 text-[15px] text-ink-2">這場考試目前沒有等待你評分的申論答案。有新的交卷時會出現在這裡。</p>
    </div>

    <div v-else class="space-y-3">
      <section class="card px-5 py-6 sm:px-10">
        <p class="flex flex-wrap items-center justify-between gap-2 text-sm text-ink-2">
          <span class="font-medium text-ink">答卷 {{ current.anonId }}</span>
          <span>第 {{ current.number }} 題</span>
        </p>
        <MathText :text="current.stem" tag="div" class="exam-text mt-4" />

        <h2 class="mt-6 mb-2 text-sm font-medium text-ink-2">考生的作答</h2>
        <p class="exam-text rounded-control bg-sunken px-4 py-3 break-words whitespace-pre-line">{{ current.answer }}</p>

        <template v-if="current.rubric">
          <h2 class="mt-6 mb-2 text-sm font-medium text-ink-2">評分規準</h2>
          <p class="text-[15px] leading-relaxed whitespace-pre-line text-ink-2">{{ current.rubric }}</p>
        </template>
      </section>

      <section class="card px-5 py-6 sm:px-10">
        <div class="flex flex-wrap items-center gap-3">
          <span class="text-[15px] font-medium">給分</span>
          <PointsStepper v-model="score" label="給分" allow-empty />
          <span class="text-sm text-ink-3">滿分 {{ current.points }}</span>
        </div>
        <!-- Fixed height: the score updates on blur, and a line appearing or disappearing would move the button under the click -->
        <p class="mt-2 min-h-5 text-sm text-grade" role="alert">
          <template v-if="missing">請先給分，0 分也要填 0。</template>
          <template v-else-if="score !== null && score > current.points">給分不能超過滿分 {{ current.points }} 分。</template>
        </p>
        <label class="mt-3 block">
          <span class="mb-1.5 block text-sm font-medium">評語（可以不寫）</span>
          <textarea v-model="comment" rows="2" maxlength="2000" class="field h-auto py-2.5" />
        </label>
        <div class="mt-5 flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
          <p class="min-w-0 flex-1 basis-60 text-[13px] text-ink-3">送出後不能自己修改。真的需要更正，請承辦人重開這份答卷的評分。</p>
          <Button variant="primary" size="md" class="rounded-full" :disabled="sending" @click="submit"><Check />送出，看下一份</Button>
        </div>
      </section>
    </div>
  </div>
</template>
