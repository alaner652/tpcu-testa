<script setup lang="ts">
import { computed, type Component } from 'vue'
import { ChartColumn, ChevronRight, Circle, CircleCheck, CircleStop, ClipboardPenLine, Clock, Inbox, LibraryBig, Plus, Send, Upload, Users } from 'lucide-vue-next'
import Badge from '@/components/ui/Badge.vue'
import { useAuth, type UnitRole } from '@/composables/useAuth'
import { useBanks } from '@/composables/useBanks'
import { EXAM_STATUS, useExams, when, type Exam } from '@/composables/useExams'
import { useGrading } from '@/composables/useGrading'

/**
 * Home is a to-do list: right after sign-in, see what to do now, one button per item.
 * With nothing to do it says so in one line instead of showing a pile of cards.
 */
const { user } = useAuth()
const { banks } = useBanks()
const { exams: gradingExams } = useGrading()
const { exams } = useExams()

const can = (...roles: UnitRole[]) => !!user.value?.units?.some((u) => u.roles.some((r) => roles.includes(r)))

interface Todo {
  key: string
  icon: Component
  text: string
  sub?: string
  to: string
  action: string
  urgent?: boolean
  progress?: number
}

const todos = computed<Todo[]>(() => {
  const list: Todo[] = []
  // Grading: the most important thing for graders
  for (const g of gradingExams.value.filter((g) => g.total > g.done)) {
    list.push({ key: `g${g.examId}`, icon: ClipboardPenLine, text: `「${g.name}」有 ${g.total - g.done} 份申論等你批改`, to: '/grading', action: '開始批改', urgent: true })
  }
  for (const e of exams.value.filter((e) => !e.archivedAt)) {
    const to = `/exams/${e.id}`
    if (e.status === 'live' && e.canManage && e.takers && e.submitted === e.takers) {
      list.push({ key: `e${e.id}`, icon: CircleStop, text: `「${e.name}」全部考生都交卷了`, sub: '可以結束考試', to: '/exams', action: '去結束考試' })
    } else if (e.status === 'live') {
      list.push({ key: `e${e.id}`, icon: Clock, text: `「${e.name}」考試進行中`, sub: `已交卷 ${e.submitted} / ${e.takers}`, to, action: '監考', progress: e.takers ? e.submitted / e.takers : 0 })
    } else if (!e.canManage) {
      continue
    } else if (e.status === 'draft') {
      list.push(
        !e.takers
          ? { key: `e${e.id}`, icon: Upload, text: `「${e.name}」還沒有考生名單`, to, action: '匯入名單' }
          : { key: `e${e.id}`, icon: Send, text: `「${e.name}」還沒發布`, sub: `${e.takers} 位考生`, to: '/exams', action: '去發布' },
      )
    } else if (e.status === 'scheduled') {
      list.push({ key: `e${e.id}`, icon: Clock, text: `「${e.name}」${when(e.startAt)} 開始`, sub: '考前記得印登入單', to, action: '看名單' })
    } else if (e.status === 'ended' && !e.resultsPublishedAt) {
      list.push({ key: `e${e.id}`, icon: ChartColumn, text: `「${e.name}」已結束，還沒公布成績`, to, action: '評分與成績' })
    }
  }
  return list
})

/** Exams coming up or running, for owners and proctors: what is ready at a glance (live first, then by start time) */
const ORDER: Record<string, number> = { live: 0, scheduled: 1, draft: 2 }
const upcoming = computed(() =>
  exams.value
    .filter((e) => !e.archivedAt && e.status in ORDER)
    .sort((a, b) => ORDER[a.status]! - ORDER[b.status]! || (a.startAt ?? '9').localeCompare(b.startAt ?? '9'))
    .slice(0, 3),
)
function countdown(e: Exam) {
  if (e.status === 'live' || !e.startAt) return ''
  const minutes = Math.round((new Date(e.startAt).getTime() - Date.now()) / 60000)
  if (minutes <= 0) return '時間已到'
  if (minutes < 60) return `還有 ${minutes} 分鐘`
  if (minutes < 48 * 60) return `還有 ${Math.round(minutes / 60)} 小時`
  return `還有 ${Math.round(minutes / 1440)} 天`
}
/** What an owner still has to prepare */
const checklist = (e: Exam) => [
  { label: '題庫', done: e.bankId !== null },
  { label: '開始時間', done: !!e.startAt },
  { label: '考生名單', done: e.takers > 0 },
  { label: '發布', done: e.status !== 'draft' },
]

/** With nothing to do, offer a starting point */
const starters = computed(() => [
  ...(can('AUTHOR') ? [{ to: '/banks', icon: LibraryBig, text: banks.value.length ? `題庫（${banks.value.length} 份）` : '建立第一份題庫' }] : []),
  ...(can('OWNER') ? [{ to: '/exams', icon: Plus, text: '安排一場考試' }] : []),
])
</script>

<template>
  <div class="space-y-6">
    <div class="px-4 md:px-0">
      <h1 class="text-[26px] font-bold sm:text-[30px]">{{ user?.name }}，你好</h1>
      <p v-if="!user?.units?.length" class="mt-1 text-[15px] text-ink-2">你還沒有被加入任何單位。請單位負責人或系統管理員把你加進單位。</p>
    </div>

    <section v-if="upcoming.length">
      <h2 class="mb-3 px-4 text-[17px] font-semibold md:px-0">考試概況</h2>
      <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        <RouterLink
          v-for="e in upcoming"
          :key="e.id"
          :to="`/exams/${e.id}`"
          class="card flex flex-col gap-3 px-5 py-4 transition-shadow hover:shadow-lift sm:px-6"
        >
          <div class="flex items-start gap-2">
            <div class="min-w-0 flex-1">
              <p class="truncate text-base font-semibold">{{ e.name }}</p>
              <p class="truncate text-sm text-ink-2">{{ e.unitName }}・{{ when(e.startAt) }}</p>
            </div>
            <Badge :tone="EXAM_STATUS[e.status].tone">{{ EXAM_STATUS[e.status].label }}</Badge>
          </div>

          <p class="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-ink-2">
            <span class="inline-flex items-center gap-1"><Users class="size-4 text-ink-3" />{{ e.takers }} 位考生</span>
            <span v-if="e.questionCount" class="tabular-nums">{{ e.questionCount }} 題・{{ e.totalPoints }} 分</span>
            <span v-if="countdown(e)" class="ml-auto font-medium" :class="e.status === 'live' ? 'text-brand' : 'text-ink'">{{ countdown(e) }}</span>
          </p>

          <!-- Running: how many have handed in -->
          <div v-if="e.status === 'live'">
            <span class="block h-1.5 overflow-hidden rounded-full bg-sunken">
              <span class="block h-full rounded-full bg-brand" :style="{ width: `${e.takers ? (e.submitted / e.takers) * 100 : 0}%` }" />
            </span>
            <span class="mt-1.5 block text-[13px] text-ink-2 tabular-nums">作答中 {{ e.inProgress }}・已交卷 {{ e.submitted }} / {{ e.takers }}</span>
          </div>
          <!-- Not started: what is still missing -->
          <ul v-else-if="e.canManage" class="flex flex-wrap gap-x-3 gap-y-1 text-[13px]">
            <li v-for="c in checklist(e)" :key="c.label" class="inline-flex items-center gap-1" :class="c.done ? 'text-ink-2' : 'font-medium text-ink'">
              <CircleCheck v-if="c.done" class="size-4 text-brand" /><Circle v-else class="size-4 text-ink-3" />{{ c.label }}
            </li>
          </ul>
        </RouterLink>
      </div>
    </section>

    <section v-if="todos.length">
      <h2 class="mb-3 px-4 text-[17px] font-semibold md:px-0">接下來要做的事</h2>
      <ul class="card divide-y divide-line">
        <li v-for="t in todos" :key="t.key">
          <RouterLink :to="t.to" class="group flex items-center gap-4 px-5 py-4 transition-colors hover:bg-sunken sm:px-6">
            <span
              class="flex size-10 shrink-0 items-center justify-center rounded-full"
              :class="t.urgent ? 'bg-grade-soft text-grade' : 'bg-brand-soft text-brand-strong'"
            >
              <component :is="t.icon" class="size-5" />
            </span>
            <span class="min-w-0 flex-1">
              <span class="block text-base font-medium">{{ t.text }}</span>
              <span v-if="t.sub" class="block text-sm text-ink-2 tabular-nums">{{ t.sub }}</span>
              <span v-if="t.progress != null" class="mt-2 block h-1.5 overflow-hidden rounded-full bg-sunken">
                <span class="block h-full rounded-full bg-brand" :style="{ width: `${t.progress * 100}%` }" />
              </span>
            </span>
            <span class="flex shrink-0 items-center gap-1 text-sm font-medium text-brand max-sm:hidden">
              {{ t.action }}<ChevronRight class="size-4 transition-transform group-hover:translate-x-0.5" />
            </span>
            <ChevronRight class="size-5 shrink-0 text-ink-3 sm:hidden" />
          </RouterLink>
        </li>
      </ul>
    </section>

    <section v-else-if="user?.units?.length" class="card flex items-center gap-4 px-5 py-6 sm:px-6">
      <span class="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand-soft text-brand-strong"><Inbox class="size-5" /></span>
      <p class="text-base">目前沒有待辦事項。</p>
    </section>

    <div v-if="starters.length" class="flex flex-wrap gap-2 px-4 md:px-0">
      <RouterLink
        v-for="s in starters"
        :key="s.to"
        :to="s.to"
        class="inline-flex h-10 items-center gap-1.5 rounded-full border border-line-strong bg-paper px-4 text-sm font-medium transition-colors hover:border-brand hover:text-brand"
      >
        <component :is="s.icon" class="size-4" />{{ s.text }}
      </RouterLink>
    </div>
  </div>
</template>
