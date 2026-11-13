<script setup lang="ts">
import type { Question, QuestionType } from '@/types/question'
import { QUESTION_TYPES } from '@/lib/question'

const props = defineProps<{ question: Question }>()

function setType(type: QuestionType) {
  const q = props.question
  if (q.type === type) return
  // 多選改單選時只保留第一個正確答案
  if (type === 'single' && q.correct.length > 1) q.correct = q.correct.slice(0, 1)
  q.type = type
}
</script>

<template>
  <div role="radiogroup" aria-label="題型" class="inline-flex flex-wrap gap-1">
    <button
      v-for="t in QUESTION_TYPES"
      :key="t.value"
      type="button"
      role="radio"
      :aria-checked="question.type === t.value"
      class="inline-flex h-9 items-center gap-1.5 rounded-full px-3 text-sm transition-colors max-md:h-10"
      :class="
        question.type === t.value
          ? 'bg-brand font-medium text-brand-fg'
          : 'text-ink-2 hover:bg-ink/[0.05] hover:text-ink'
      "
      @click="setType(t.value)"
    >
      <component :is="t.icon" class="size-4" />
      {{ t.label }}
    </button>
  </div>
</template>
