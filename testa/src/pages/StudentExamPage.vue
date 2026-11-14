<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { LoaderCircle, X } from 'lucide-vue-next'
import Logo from '@/components/app/Logo.vue'
import ExamPreview from '@/components/editor/ExamPreview.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import { useAuth } from '@/composables/useAuth'
import { useCandidate } from '@/composables/useCandidate'
import { useConfirm } from '@/composables/useConfirm'
import type { RemoteExam } from '@/lib/grading'
import type { Bank } from '@/types/question'

/** Candidate exam page: focus mode with no nav, only the exam name and leave. */
const router = useRouter()
const { state, load, open, reportLeave } = useCandidate()
const { confirm } = useConfirm()
const { user } = useAuth()

const loading = ref(true)
const error = ref('')
const paper = ref<InstanceType<typeof ExamPreview> | null>(null)
const finished = ref(false)

// Paper (without answers) and how to save, fetched from the server once answering starts
const bank = ref<Bank | null>(null)
const remote = ref<RemoteExam | null>(null)
const started = computed(() => !!bank.value && !!remote.value)
const exam = computed(() => state.value?.exam)
/** Can enter: exam is live and not yet submitted */
const available = computed(() => exam.value?.status === 'live' && !state.value?.attempt?.submittedAt)

// Confirm identity and read the instructions before entering
const agreed = ref(false)
const beginning = ref(false)
const RULES = [
  '按下「開始作答」後開始計時，計時不會暫停，時間到會自動交卷。',
  '作答內容會自動儲存。斷線或換電腦時重新登入，可以從原進度繼續。',
  '交卷後無法再修改答案。',
  '考試期間請不要離開考試畫面，離開會被記錄。',
]

async function begin() {
  if (beginning.value) return
  beginning.value = true
  try {
    const opened = await open()
    const info = state.value!.exam
    bank.value = { id: 'exam', name: info.name, subject: '', duration: info.duration, questions: opened.questions }
    remote.value = opened.remote
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message
    await load().catch(() => {})
  } finally {
    beginning.value = false
  }
}

onMounted(async () => {
  try {
    await load()
    // Already answering (reload or back after a disconnect): go straight to the paper
    if (state.value?.attempt && !state.value.attempt.submittedAt) await begin()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
})

// Switching to another tab or window while answering: record it once
const onVisibility = () => {
  if (document.hidden && started.value && !finished.value) reportLeave()
}
document.addEventListener('visibilitychange', onVisibility)
onBeforeUnmount(() => document.removeEventListener('visibilitychange', onVisibility))

// Confirm before leaving without submitting (including the browser back button)
onBeforeRouteLeave(async () => {
  // Session gone (signed in elsewhere, backend restarted): answers cannot be saved here anyway, so go to login without asking
  if (!user.value) return true
  if (finished.value || !started.value || !paper.value) return true
  return confirm({
    title: '還沒交卷，確定要離開？',
    description: '已經作答的內容會保留，但計時不會暫停。在時間內重新登入可以繼續作答。',
    confirmLabel: '離開考試',
    cancelLabel: '繼續作答',
    tone: 'danger',
  })
})
</script>

<template>
  <div class="flex h-dvh flex-col">
    <header class="relative z-20 flex h-14 shrink-0 items-center gap-3 bg-paper px-3 shadow-card sm:px-5">
      <Logo class="max-sm:[&>span]:hidden" />
      <p class="min-w-0 flex-1 truncate text-[15px] font-medium">{{ exam?.name }}</p>
      <button
        type="button"
        class="flex h-10 shrink-0 items-center gap-1.5 rounded-full px-3 text-sm text-ink-2 transition-colors hover:bg-ink/[0.06] hover:text-ink"
        @click="router.push({ name: 'student' })"
      >
        <X class="size-4" />離開
      </button>
    </header>

    <main class="thin-scroll min-h-0 flex-1 overflow-y-auto">
      <div v-if="loading" class="mx-auto max-w-[760px] space-y-3 py-7 md:px-6">
        <Skeleton class="h-72 rounded-card" />
      </div>
      <div v-else-if="!started && (!state || !exam || !available)" class="mx-auto max-w-[760px] py-7 md:px-6">
        <div class="card px-5 py-12 sm:px-10">
          <h1 class="text-2xl font-bold">{{ state?.attempt?.submittedAt ? '你已經交卷了' : '這場考試現在無法作答' }}</h1>
          <p class="mt-2 text-[15px] text-ink-2">
            {{ error || (state?.attempt?.submittedAt ? '作答已經送出，不能再修改。' : '考試可能還沒開始或已經結束。請回到「我的考試」確認，或洽監考人員。') }}
          </p>
        </div>
      </div>
      <!-- Identity check and instructions -->
      <div v-else-if="!started && state && exam" class="mx-auto max-w-[560px] px-4 py-6 md:py-10">
        <section class="rounded-card bg-paper p-6 shadow-card sm:p-8">
          <h1 class="text-2xl font-bold">請確認這是你本人</h1>
          <dl class="mt-5 grid grid-cols-[auto_1fr] gap-x-5 gap-y-2.5 rounded-control bg-sunken px-4 py-4 text-base">
            <dt class="text-ink-2">姓名</dt>
            <dd class="font-semibold">{{ state.name }}</dd>
            <dt class="text-ink-2">准考證號</dt>
            <dd class="font-semibold tabular-nums">{{ state.ticket }}</dd>
            <dt class="text-ink-2">報考科系</dt>
            <dd class="font-semibold">{{ state.dept || '（未填）' }}</dd>
            <dt class="text-ink-2">考試</dt>
            <dd class="font-semibold">{{ exam.name }}</dd>
            <dt class="text-ink-2">題數與時間</dt>
            <dd class="font-semibold">{{ exam.questionCount }} 題，{{ exam.duration }} 分鐘</dd>
          </dl>
          <h2 class="mt-6 text-[17px] font-semibold">考試須知</h2>
          <ol class="mt-2 list-decimal space-y-1 pl-5 text-[15px] leading-relaxed text-ink-2">
            <li v-for="rule in RULES" :key="rule">{{ rule }}</li>
          </ol>
          <label class="mt-5 flex min-h-11 cursor-pointer items-start gap-3 py-2 text-[15px]">
            <input v-model="agreed" type="checkbox" class="mt-0.5 size-5 shrink-0 accent-(--brand)" />
            以上資料是我本人，我已閱讀考試須知。
          </label>
          <p v-if="error" class="mt-2 text-sm text-grade" role="alert">{{ error }}</p>
          <div class="mt-3 flex flex-wrap-reverse gap-2">
            <button
              type="button"
              class="h-12 flex-1 basis-32 rounded-full border border-line-strong px-5 text-[15px] font-medium transition-colors hover:bg-sunken"
              @click="router.push({ name: 'student' })"
            >
              這不是我
            </button>
            <button
              type="button"
              class="h-12 flex-[2] basis-48 rounded-full bg-brand px-5 text-base font-semibold text-brand-fg transition-colors hover:bg-brand-strong disabled:opacity-45"
              :disabled="!agreed || beginning"
              @click="begin"
            >
              <LoaderCircle v-if="beginning" class="mr-1.5 inline size-4 animate-spin" />開始作答
            </button>
          </div>
        </section>
      </div>
      <ExamPreview
        v-else-if="bank && remote"
        ref="paper"
        real
        :bank="bank"
        :remote="remote"
        :title="exam?.name"
        @submitted="finished = true"
        @exit="router.push({ name: 'student' })"
      />
    </main>
  </div>
</template>
