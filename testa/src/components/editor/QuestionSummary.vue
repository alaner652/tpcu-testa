<script setup lang="ts">
import type { Question } from '@/types/question'
import { LETTERS } from '@/lib/utils'
import AnswerBubble from './AnswerBubble.vue'
import MathText from './MathText.vue'

defineProps<{ question: Question }>()
</script>

<template>
  <div>
    <MathText v-if="question.stem.trim()" :text="question.stem" tag="div" class="exam-text" />
    <p v-else class="exam-text text-ink-3">尚未輸入題目</p>

    <ul v-if="question.type === 'single' || question.type === 'multiple'" class="mt-3 space-y-2">
      <li
        v-for="(choice, index) in question.choices"
        :key="choice.id"
        class="flex items-start gap-3 rounded-control px-3 py-2.5"
        :class="question.correct.includes(choice.id) ? 'bg-brand-soft' : 'bg-sunken'"
      >
        <AnswerBubble
          :letter="LETTERS[index]"
          :filled="question.correct.includes(choice.id)"
          :square="question.type === 'multiple'"
          tone="brand"
          :class="question.correct.includes(choice.id) ? 'mt-px' : 'mt-px bg-paper'"
        />
        <span class="exam-text min-w-0 flex-1">
          <MathText v-if="choice.text.trim()" :text="choice.text" />
          <span v-else class="text-ink-3">選項 {{ LETTERS[index] }}</span>
        </span>
        <span v-if="question.correct.includes(choice.id)" class="mt-1 shrink-0 text-[13px] font-medium text-brand-strong">正確答案</span>
      </li>
    </ul>

    <ul v-else-if="question.type === 'blank'" class="mt-3 flex flex-wrap gap-x-5 gap-y-1.5 text-sm text-ink-2">
      <li v-for="(blank, index) in question.blanks" :key="blank.id" class="flex items-center gap-1.5">
        空格 {{ index + 1 }}
        <span
          v-for="answer in blank.answers"
          :key="answer"
          class="rounded-[4px] bg-brand px-1.5 py-0.5 font-mono text-xs text-brand-fg"
          >{{ answer }}</span
        >
        <span v-if="!blank.answers.length" class="text-ink-3">未設定</span>
      </li>
    </ul>

    <p v-else class="mt-2.5 text-sm text-ink-2">
      人工閱卷<template v-if="question.aiGrading">，AI 提供建議分數</template
      ><template v-if="question.wordLimit">，上限 {{ question.wordLimit }} 字</template>
    </p>
  </div>
</template>
