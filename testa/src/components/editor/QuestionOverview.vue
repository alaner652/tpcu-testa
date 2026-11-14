<script setup lang="ts">
import { DEMO_FEATURES } from '@/lib/flags'
import { computed, ref } from 'vue'
import { VueDraggable } from 'vue-draggable-plus'
import { Search, X } from 'lucide-vue-next'
import Skeleton from '@/components/ui/Skeleton.vue'
import { useBanks } from '@/composables/useBanks'
import { oneLine } from '@/lib/math'
import { issuesOf, typeMeta } from '@/lib/question'
import type { Question } from '@/types/question'
import MathText from './MathText.vue'

/**
 * 題號卡：像答案卡一樣用圓圈呈現每一題的狀態，取代文字目錄。
 * 塗滿＝完成、紅圈＝未完成、黃色＝AI 匯入待確認。
 */
const emit = defineEmits<{ navigate: [] }>()
const { questions, activeId, loading, select } = useBanks()
/** 圖例只在真的有「未完成」「待確認」的題目時才需要 */
const needsLegend = computed(() => questions.value.some((q) => statusOf(q) !== 'done'))

const query = ref('')
const keyword = computed(() => query.value.trim().toLowerCase())
const matches = computed(() =>
  questions.value
    .map((question, index) => ({ question, index }))
    .filter(({ question }) => question.stem.toLowerCase().includes(keyword.value)),
)

function statusOf(question: Question) {
  if (question.review) return 'review'
  return issuesOf(question).length ? 'incomplete' : 'done'
}
const STATUS_LABEL = { review: 'AI 匯入待確認', incomplete: '未完成', done: '已完成' } as const
const STATUS_CLASS = {
  done: 'bg-brand-soft text-brand-strong hover:bg-brand hover:text-brand-fg',
  incomplete: 'text-grade shadow-[inset_0_0_0_1.5px_var(--grade)] hover:bg-grade-soft',
  review: 'bg-marker text-marker-ink hover:brightness-95 dark:hover:brightness-125',
} as const

function go(id: string) {
  select(id)
  emit('navigate')
}
</script>

<template>
  <div class="flex min-h-0 flex-col">
    <!-- 題數與總分在試卷標題下面就有，這裡不重複 -->
    <h2 class="px-1 text-[15px] font-semibold">題號卡</h2>

    <label
      class="mt-3 flex h-9 items-center gap-2 rounded-full bg-paper px-3 shadow-card transition-shadow focus-within:shadow-[0_0_0_2px_var(--brand)] max-md:h-10"
    >
      <Search class="size-4 shrink-0 text-ink-3" />
      <input
        v-model="query"
        type="search"
        placeholder="搜尋題目"
        aria-label="搜尋題目"
        class="min-w-0 flex-1 bg-transparent text-sm outline-none [&::-webkit-search-cancel-button]:hidden"
      />
      <button v-if="query" type="button" aria-label="清除搜尋" class="rounded-full text-ink-3 hover:text-ink" @click="query = ''">
        <X class="size-4" />
      </button>
    </label>

    <nav class="thin-scroll mt-3 min-h-0 flex-1 overflow-y-auto p-1" aria-label="題號卡">
      <div v-if="loading" class="grid grid-cols-5 gap-2">
        <Skeleton v-for="n in 10" :key="n" class="aspect-square rounded-full" />
      </div>

      <p v-else-if="questions.length === 0" class="text-sm text-ink-2">新增題目後，題號會出現在這裡。</p>

      <!-- 搜尋結果：以文字列出符合的題目 -->
      <template v-else-if="keyword">
        <div v-if="matches.length === 0" class="text-sm text-ink-2">
          找不到包含「{{ query.trim() }}」的題目。
          <button type="button" class="mt-1 block rounded-sm text-brand underline-offset-2 hover:underline" @click="query = ''">
            清除搜尋
          </button>
        </div>
        <ol v-else class="space-y-1">
          <li v-for="{ question, index } in matches" :key="question.id">
            <button
              type="button"
              class="flex h-10 w-full items-center gap-2.5 rounded-control px-2 text-left text-sm text-ink-2 transition-colors hover:bg-paper hover:text-ink"
              @click="go(question.id)"
            >
              <span class="w-5 shrink-0 text-right font-medium text-brand tabular-nums">{{ index + 1 }}</span>
              <span class="h-6 min-w-0 flex-1 truncate leading-6"><MathText :text="oneLine(question.stem)" /></span>
            </button>
          </li>
        </ol>
      </template>

      <VueDraggable
        v-else
        v-model="questions"
        tag="ol"
        :animation="160"
        :delay="180"
        :delay-on-touch-only="true"
        ghost-class="opacity-30"
        class="grid grid-cols-5 gap-2"
      >
        <li v-for="(question, index) in questions" :key="question.id">
          <button
            type="button"
            class="flex aspect-square w-full items-center justify-center rounded-full text-sm font-medium tabular-nums transition-all duration-100"
            :class="[
              STATUS_CLASS[statusOf(question)],
              question.id === activeId && 'ring-2 ring-brand ring-offset-2 ring-offset-desk',
            ]"
            :aria-current="question.id === activeId ? 'true' : undefined"
            :aria-label="`第 ${index + 1} 題，${typeMeta(question.type).label}，${STATUS_LABEL[statusOf(question)]}`"
            :title="`${typeMeta(question.type).label}題，${question.points} 分，${STATUS_LABEL[statusOf(question)]}`"
            @click="go(question.id)"
          >
            {{ index + 1 }}
          </button>
        </li>
      </VueDraggable>
    </nav>

    <ul v-if="!loading && questions.length && !keyword && needsLegend" class="mt-4 space-y-1.5 px-1 text-[13px] text-ink-2">
      <li class="flex items-center gap-2"><span class="size-3 rounded-full bg-brand-soft shadow-[inset_0_0_0_1px_var(--brand)]" />已完成</li>
      <li class="flex items-center gap-2"><span class="size-3 rounded-full shadow-[inset_0_0_0_1.5px_var(--grade)]" />未完成</li>
      <li v-if="DEMO_FEATURES" class="flex items-center gap-2"><span class="size-3 rounded-full bg-marker shadow-[inset_0_0_0_1px_var(--marker-ink)]" />AI 匯入待確認</li>
    </ul>
    <p v-if="!loading && questions.length > 1 && !keyword" class="mt-3 px-1 text-[13px] text-ink-3">拖曳圓圈可以調整順序。</p>
  </div>
</template>
