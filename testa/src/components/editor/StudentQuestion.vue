<script setup lang="ts">
import type { Question } from '@/types/question'
import type { Attempt } from '@/lib/grading'
import { LETTERS } from '@/lib/utils'
import AnswerBubble from './AnswerBubble.vue'
import MathText from './MathText.vue'

/** One question as a candidate sees it. locked: read-only (review after submitting). */
const props = defineProps<{
  question: Question
  index: number
  attempt: Attempt
  showAnswers: boolean
  locked?: boolean
}>()

function pick(id: string) {
  const a = props.attempt
  if (props.locked) return
  if (props.question.type === 'single') a.choices = [id]
  else a.choices = a.choices.includes(id) ? a.choices.filter((c) => c !== id) : [...a.choices, id]
}
</script>

<template>
  <div>
    <div class="flex items-start gap-3 sm:gap-4">
      <span class="w-6 shrink-0 text-[17px] leading-[1.7] font-semibold text-brand tabular-nums">{{ index + 1 }}</span>
      <MathText v-if="question.stem.trim()" :text="question.stem" tag="div" class="exam-text min-w-0 flex-1" />
      <p v-else class="exam-text min-w-0 flex-1 text-ink-3">尚未輸入題目</p>
      <span class="shrink-0 pt-1 text-sm text-ink-2 tabular-nums"><slot name="score">{{ question.points }} 分</slot></span>
    </div>

    <div class="mt-4 pl-9 sm:pl-10">
      <!-- Single, multiple -->
      <ul
        v-if="question.type === 'single' || question.type === 'multiple'"
        class="space-y-2"
        :role="question.type === 'single' ? 'radiogroup' : 'group'"
        :aria-label="`第 ${index + 1} 題作答`"
      >
        <li v-for="(choice, i) in question.choices" :key="choice.id">
          <button
            type="button"
            :role="question.type === 'single' ? 'radio' : 'checkbox'"
            :aria-checked="attempt.choices.includes(choice.id)"
            :disabled="locked"
            class="flex w-full items-start gap-3 rounded-control px-3 py-3 text-left transition-colors disabled:cursor-default"
            :class="
              showAnswers && question.correct.includes(choice.id)
                ? 'bg-brand-soft'
                : attempt.choices.includes(choice.id)
                  ? 'bg-sunken shadow-[inset_0_0_0_1.5px_var(--ink)]'
                  : 'bg-sunken hover:shadow-[inset_0_0_0_1.5px_var(--line-strong)]'
            "
            @click="pick(choice.id)"
          >
            <AnswerBubble
              :letter="LETTERS[i]"
              :filled="attempt.choices.includes(choice.id)"
              :square="question.type === 'multiple'"
              :class="attempt.choices.includes(choice.id) ? 'mt-px' : 'mt-px bg-paper'"
            />
            <span class="exam-text min-w-0 flex-1">
              <MathText v-if="choice.text.trim()" :text="choice.text" />
              <span v-else class="text-ink-3">選項 {{ LETTERS[i] }}</span>
            </span>
            <span
              v-if="showAnswers && question.correct.includes(choice.id)"
              class="mt-1 shrink-0 text-[13px] font-medium text-brand-strong"
            >
              正確答案
            </span>
          </button>
        </li>
      </ul>

      <!-- Blank -->
      <ul v-else-if="question.type === 'blank'" class="space-y-3">
        <li v-for="(blank, i) in question.blanks" :key="blank.id">
          <label class="flex items-center gap-3 text-sm text-ink-2">
            <span class="w-12 shrink-0">空格 {{ i + 1 }}</span>
            <input
              v-model="attempt.blanks[blank.id]"
              :readonly="locked"
              class="h-11 max-w-xs min-w-0 flex-1 rounded-control border-[1.5px] border-transparent bg-sunken px-3 text-base text-ink transition-colors outline-none hover:border-line-strong focus:border-brand"
            />
          </label>
          <p v-if="showAnswers" class="mt-1 pl-15 text-[13px] font-medium text-brand-strong">
            正確答案：{{ blank.answers.length ? blank.answers.join(' 或 ') : '未設定' }}
          </p>
        </li>
      </ul>

      <!-- Essay -->
      <div v-else>
        <textarea
          v-model="attempt.essay"
          :readonly="locked"
          rows="7"
          :aria-label="`第 ${index + 1} 題作答`"
          placeholder="在這裡作答"
          class="block w-full resize-y rounded-control border-[1.5px] border-transparent bg-sunken px-3.5 py-3 text-base leading-relaxed transition-colors outline-none hover:border-line-strong focus:border-brand"
        />
        <p
          class="mt-1 text-right text-[13px] tabular-nums"
          :class="question.wordLimit && attempt.essay.length > question.wordLimit ? 'text-grade' : 'text-ink-3'"
        >
          {{ attempt.essay.length }}<template v-if="question.wordLimit"> / {{ question.wordLimit }}</template> 字
        </p>
        <div v-if="showAnswers && question.rubric.trim()" class="mt-2 border-l-2 border-brand pl-3.5">
          <p class="text-[13px] font-medium text-brand-strong">評分規準</p>
          <p class="mt-1 text-sm leading-relaxed whitespace-pre-line text-ink-2">{{ question.rubric }}</p>
        </div>
      </div>

      <div v-if="showAnswers && question.explanation.trim()" class="mt-4 border-l-2 border-brand pl-3.5">
        <p class="text-[13px] font-medium text-brand-strong">解析</p>
        <MathText :text="question.explanation" tag="div" class="exam-text mt-0.5 text-base text-ink-2" />
      </div>
    </div>
  </div>
</template>
