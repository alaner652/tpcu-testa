<script setup lang="ts">
import { computed } from 'vue'
import { ChevronRight, ClipboardCheck, FileText, LibraryBig } from 'lucide-vue-next'
import { useAuth, type UnitRole } from '@/composables/useAuth'
import { useBanks } from '@/composables/useBanks'
import { useExams } from '@/composables/useExams'
import { useGrading } from '@/composables/useGrading'

const { user } = useAuth()
const { banks } = useBanks()
const { pending } = useGrading()
const { exams } = useExams()

const live = computed(() => exams.value.filter((e) => e.status === 'live'))
/** 只列出這個人的角色用得到的動作 */
const can = (...roles: UnitRole[]) => !!user.value?.units?.some((u) => u.roles.some((r) => roles.includes(r)))
const actions = computed(() => [
  ...(can('AUTHOR', 'OWNER') ? [{ to: '/banks', icon: LibraryBig, title: can('AUTHOR') ? '出題' : '題庫', text: `${banks.value.length} 份題庫`, urgent: false }] : []),
  ...(can('OWNER', 'PROCTOR') ? [{ to: '/exams', icon: FileText, title: can('OWNER') ? '安排考試' : '監考', text: `${live.value.length} 場進行中`, urgent: false }] : []),
  ...(can('GRADER')
    ? [
        {
          to: '/grading',
          icon: ClipboardCheck,
          title: '閱卷',
          text: pending.value ? `${pending.value} 份等你批改` : '都批完了',
          urgent: pending.value > 0,
        },
      ]
    : []),
])
</script>

<template>
  <div class="space-y-8">
    <div class="px-4 md:px-0">
      <h1 class="text-[26px] font-bold sm:text-[30px]">{{ user?.name }}，你好</h1>
      <p v-if="!actions.length" class="mt-1 text-[15px] text-ink-2">你還沒有被加入任何單位。請單位負責人或系統管理員把你加進單位並指派角色。</p>
    </div>

    <div class="grid gap-3 px-4 sm:grid-cols-3 md:px-0">
      <RouterLink
        v-for="action in actions"
        :key="action.to"
        :to="action.to"
        class="group flex items-center gap-4 rounded-card bg-paper p-5 shadow-card transition-shadow hover:shadow-lift"
      >
        <span class="flex size-12 shrink-0 items-center justify-center rounded-full bg-brand-soft text-brand-strong">
          <component :is="action.icon" class="size-6" />
        </span>
        <span class="min-w-0 flex-1">
          <span class="block text-lg font-semibold">{{ action.title }}</span>
          <span class="block text-sm" :class="action.urgent ? 'font-medium text-grade' : 'text-ink-2'">{{ action.text }}</span>
        </span>
        <ChevronRight class="size-5 text-ink-3 transition-transform group-hover:translate-x-0.5" />
      </RouterLink>
    </div>

    <section v-if="live.length">
      <h2 class="mb-3 px-4 text-[17px] font-semibold md:px-0">進行中的考試</h2>
      <div class="card divide-y divide-line">
        <RouterLink v-for="exam in live" :key="exam.id" :to="`/exams/${exam.id}`" class="block px-5 py-4 transition-colors hover:bg-sunken sm:px-6">
          <div class="flex items-baseline justify-between gap-3">
            <p class="truncate text-base font-medium">{{ exam.name }}</p>
            <p class="shrink-0 text-sm text-ink-2 tabular-nums">已交卷 {{ exam.submitted }} / {{ exam.takers }}</p>
          </div>
          <div class="mt-2.5 h-1.5 overflow-hidden rounded-full bg-sunken">
            <div class="h-full rounded-full bg-brand" :style="{ width: `${exam.takers ? (exam.submitted / exam.takers) * 100 : 0}%` }" />
          </div>
        </RouterLink>
      </div>
    </section>
  </div>
</template>
