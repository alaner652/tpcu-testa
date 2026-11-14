<script setup lang="ts">
import { computed } from 'vue'
import { VueDraggable } from 'vue-draggable-plus'
import Skeleton from '@/components/ui/Skeleton.vue'
import { useBanks } from '@/composables/useBanks'
import { DEMO_FEATURES } from '@/lib/flags'
import { issuesOf } from '@/lib/question'
import AddQuestionBar from './AddQuestionBar.vue'
import AnswerBubble from './AnswerBubble.vue'
import QuestionRow from './QuestionRow.vue'

defineEmits<{ import: [] }>()
const { bank, questions, activeId, totalPoints, incompleteCount, reviewCount, loading, select } = useBanks()

const firstIncomplete = computed(() => questions.value.find((q) => issuesOf(q).length > 0))
const firstReview = computed(() => questions.value.find((q) => q.review))

function onDuration(event: Event) {
  if (!bank.value) return
  const value = Math.round(Number((event.target as HTMLInputElement).value))
  bank.value.duration = Math.min(600, Math.max(1, value || 60))
}
</script>

<template>
  <div class="space-y-3 pb-24 md:pb-10">
    <!-- 載入中 -->
    <template v-if="loading">
      <div class="bg-paper px-5 py-8 shadow-card sm:px-10 md:rounded-card" aria-busy="true" aria-label="載入題庫中">
        <Skeleton class="h-9 w-2/3" />
        <Skeleton class="mt-4 h-4 w-56" />
      </div>
      <div v-for="n in 3" :key="n" class="space-y-3 bg-paper px-5 py-8 shadow-card sm:px-10 md:rounded-card">
        <Skeleton class="h-5 w-4/5" />
        <Skeleton class="h-11 w-full" />
        <Skeleton class="h-11 w-full" />
      </div>
    </template>

    <template v-else-if="bank">
      <!-- 試卷資訊 -->
      <header class="bg-paper px-5 pt-6 pb-6 shadow-card sm:px-10 sm:pt-8 md:rounded-card">
        <input
          v-model="bank.name"
          aria-label="題庫名稱"
          placeholder="未命名題庫"
          class="-mx-2 w-[calc(100%+1rem)] rounded-control bg-transparent px-2 py-1 text-[24px] leading-tight font-bold transition-colors outline-none hover:bg-sunken focus:bg-sunken sm:text-[30px]"
        />
        <div class="mt-3 flex flex-wrap items-center gap-x-6 gap-y-2 text-[15px] text-ink-2">
          <label class="inline-flex items-center gap-1.5">
            科目
            <input
              v-model="bank.subject"
              aria-label="科目"
              class="h-8 w-16 rounded-control bg-sunken px-2 text-center font-medium text-ink outline-none focus:shadow-[inset_0_0_0_1.5px_var(--brand)]"
            />
          </label>
          <label class="inline-flex items-center gap-1.5">
            作答時間
            <input
              :value="bank.duration"
              type="number"
              inputmode="numeric"
              min="1"
              max="600"
              aria-label="作答時間（分鐘）"
              class="h-8 w-14 [appearance:textfield] rounded-control bg-sunken px-2 text-center font-medium text-ink tabular-nums outline-none focus:shadow-[inset_0_0_0_1.5px_var(--brand)] [&::-webkit-inner-spin-button]:appearance-none"
              @change="onDuration"
            />
            分鐘
          </label>
          <span><strong class="font-semibold text-ink tabular-nums">{{ questions.length }}</strong> 題</span>
          <span>總分 <strong class="font-semibold text-ink tabular-nums">{{ totalPoints }}</strong> 分</span>
        </div>
        <div v-if="incompleteCount || reviewCount" class="mt-4 flex flex-wrap gap-2">
          <button
            v-if="firstIncomplete"
            type="button"
            class="inline-flex h-8 items-center rounded-full bg-grade-soft px-3 text-sm text-grade hover:brightness-95 dark:hover:brightness-125"
            @click="select(firstIncomplete.id)"
          >
            {{ incompleteCount }} 題未完成
          </button>
          <button
            v-if="firstReview"
            type="button"
            class="inline-flex h-8 items-center rounded-full bg-marker px-3 text-sm text-marker-ink hover:brightness-95 dark:hover:brightness-125"
            @click="select(firstReview.id)"
          >
            {{ reviewCount }} 題 AI 匯入待確認
          </button>
        </div>
      </header>

      <!-- 空白題庫 -->
      <div v-if="questions.length === 0" class="bg-paper px-5 py-12 shadow-card sm:px-10 md:rounded-card">
        <div class="flex gap-2" aria-hidden="true">
          <AnswerBubble letter="A" :filled="false" />
          <AnswerBubble letter="B" :filled="true" tone="brand" />
          <AnswerBubble letter="C" :filled="false" />
          <AnswerBubble letter="D" :filled="false" />
        </div>
        <h2 class="mt-5 text-2xl font-bold">這份題庫還沒有題目</h2>
        <p class="mt-2 mb-6 max-w-md text-[15px] text-ink-2">
          選一種題型開始出題<template v-if="DEMO_FEATURES">，或上傳現有的 Word、PDF 考卷，讓 AI 幫你拆成一題一題</template>。
        </p>
        <AddQuestionBar @import="$emit('import')" />
      </div>

      <template v-else>
        <VueDraggable v-model="questions" handle=".drag-handle" :animation="180" ghost-class="drag-ghost" class="space-y-3">
          <QuestionRow
            v-for="(question, index) in questions"
            :key="question.id"
            :question="question"
            :index="index"
            :total="questions.length"
            :active="question.id === activeId"
          />
        </VueDraggable>
        <div class="rounded-card border-[1.5px] border-dashed border-line-strong px-5 py-5 max-md:mx-3 sm:px-10">
          <p class="mb-3 text-sm text-ink-2">在最後新增題目</p>
          <AddQuestionBar @import="$emit('import')" />
        </div>
      </template>
    </template>
  </div>
</template>
