<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from '@/components/ui/Button.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import { useCandidate } from '@/composables/useCandidate'
import { when } from '@/composables/useExams'

/** 考生首頁：考生只屬於一場考試，一眼看出現在能做什麼。 */
const { state, load } = useCandidate()
const router = useRouter()
const error = ref('')

async function refresh() {
  try {
    await load()
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message
  }
}
onMounted(refresh)

const view = computed(() => {
  const s = state.value
  if (!s) return null
  if (s.attempt?.submittedAt) return 'done'
  if (s.attempt) return 'resume'
  if (s.exam.status === 'live') return 'open'
  if (s.exam.status === 'scheduled') return 'upcoming'
  return 'missed'
})
const STATE = {
  open: { label: '可以作答', class: 'bg-brand text-brand-fg' },
  resume: { label: '作答中', class: 'bg-brand text-brand-fg' },
  upcoming: { label: '即將開始', class: 'bg-marker text-marker-ink' },
  done: { label: '已交卷', class: 'bg-brand-soft text-brand-strong' },
  missed: { label: '已結束', class: 'bg-sunken text-ink-2' },
} as const
const enter = () => router.push({ name: 'student-exam', params: { id: 'now' } })
</script>

<template>
  <div class="mx-auto max-w-[760px]">
    <div class="mb-5 px-4 md:px-0">
      <h1 class="text-[26px] font-bold sm:text-[30px]">{{ state?.name }}，你好</h1>
      <p class="mt-1 text-[15px] text-ink-2">這裡是你的考試。</p>
    </div>

    <div v-if="error" class="card flex items-center gap-3 px-5 py-4 text-[15px] text-grade sm:px-6" role="alert">
      <span class="min-w-0 flex-1">{{ error }}</span>
      <Button variant="outline" class="rounded-full" @click="refresh">重試</Button>
    </div>
    <Skeleton v-else-if="!state || !view" class="mx-4 h-28 rounded-card md:mx-0" />

    <div v-else class="card px-5 py-5 sm:px-6">
      <div class="flex flex-wrap items-center gap-x-4 gap-y-3">
        <div class="min-w-0 flex-1 basis-56">
          <span class="rounded-full px-2.5 py-0.5 text-[13px] font-medium" :class="STATE[view].class">{{ STATE[view].label }}</span>
          <p class="mt-2 text-lg leading-snug font-semibold">{{ state.exam.name }}</p>
          <p class="mt-1 text-sm text-ink-2">
            {{ when(state.exam.startAt) }}，作答 {{ state.exam.duration }} 分鐘，共 {{ state.exam.questionCount }} 題
          </p>
          <p class="mt-1 text-sm text-ink-2 tabular-nums">准考證號 {{ state.ticket }}，座位第 {{ state.seat }} 號</p>
        </div>

        <Button v-if="view === 'open'" variant="primary" size="md" class="rounded-full" @click="enter">進入考試</Button>
        <Button v-else-if="view === 'resume'" variant="primary" size="md" class="rounded-full" @click="enter">繼續作答</Button>
        <p v-else-if="view === 'done'" class="max-w-52 text-sm text-ink-2">你的作答已經送出。成績會在評分完成後由承辦單位公布。</p>
        <div v-else-if="view === 'upcoming'" class="text-right">
          <p class="text-sm text-ink-2">開始時間到了才能進入</p>
          <Button variant="outline" class="mt-2 rounded-full" @click="refresh">重新整理</Button>
        </div>
        <p v-else class="text-sm text-ink-2">考試已經結束，你沒有作答紀錄。</p>
      </div>
    </div>
  </div>
</template>
