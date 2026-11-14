<script setup lang="ts">
import { nextTick } from 'vue'
import { Plus, X } from 'lucide-vue-next'
import type { Question } from '@/types/question'
import { LETTERS, uid } from '@/lib/utils'
import AnswerBubble from './AnswerBubble.vue'
import InlineMathField from './InlineMathField.vue'

const props = defineProps<{ question: Question }>()

type Field = InstanceType<typeof InlineMathField>
const fields = new Map<string, Field>()

function register(id: string, el: unknown) {
  if (el) fields.set(id, el as Field)
  else fields.delete(id)
}

async function focusChoice(id: string) {
  await nextTick()
  fields.get(id)?.focus()
}

function toggleCorrect(id: string) {
  const q = props.question
  if (q.type === 'single') q.correct = [id]
  else q.correct = q.correct.includes(id) ? q.correct.filter((c) => c !== id) : [...q.correct, id]
}

function addChoice(afterIndex = props.question.choices.length - 1, text = '') {
  const choice = { id: uid('c'), text }
  props.question.choices.splice(afterIndex + 1, 0, choice)
  return choice
}

function removeChoice(index: number) {
  const q = props.question
  if (q.choices.length <= 2) return
  const [removed] = q.choices.splice(index, 1)
  q.correct = q.correct.filter((id) => id !== removed.id)
  focusChoice(q.choices[Math.max(0, index - 1)].id)
}

function onKeydown(event: KeyboardEvent, index: number) {
  // Enter while an IME (Zhuyin, Cangjie...) is composing must not add a choice
  if (event.isComposing) return
  const choice = props.question.choices[index]
  if (event.key === 'Enter' && !event.shiftKey && !event.metaKey && !event.ctrlKey) {
    event.preventDefault()
    focusChoice(addChoice(index).id)
  } else if (event.key === 'Backspace' && choice.text === '' && props.question.choices.length > 2) {
    event.preventDefault()
    removeChoice(index)
  }
}

/** Pasting several lines splits them into choices and strips labels such as (A) or B. */
function onPaste(event: ClipboardEvent, index: number) {
  const text = event.clipboardData?.getData('text') ?? ''
  const lines = text
    .split(/\r?\n/)
    .map((line) => line.replace(/^\s*[(（]?[A-Za-z0-9][)）.．、]\s*/, '').trim())
    .filter(Boolean)
  if (lines.length < 2) return
  event.preventDefault()
  const choices = props.question.choices
  let at = index
  if (choices[index].text.trim() === '') choices[index].text = lines.shift()!
  for (const line of lines) {
    const next = choices[at + 1]
    if (next && next.text.trim() === '') next.text = line
    else addChoice(at, line)
    at += 1
  }
  focusChoice(choices[at].id)
}
</script>

<template>
  <div>
    <ul class="space-y-2" :role="question.type === 'single' ? 'radiogroup' : 'group'" aria-label="選項與正確答案">
      <li
        v-for="(choice, index) in question.choices"
        :key="choice.id"
        class="group/c flex items-start gap-3 rounded-control py-1.5 pr-1.5 pl-3 transition-colors focus-within:shadow-[inset_0_0_0_1.5px_var(--brand)]"
        :class="question.correct.includes(choice.id) ? 'bg-brand-soft' : 'bg-sunken'"
      >
        <button
          type="button"
          :role="question.type === 'single' ? 'radio' : 'checkbox'"
          :aria-checked="question.correct.includes(choice.id)"
          :aria-label="`將選項 ${LETTERS[index]} 設為正確答案`"
          :title="question.correct.includes(choice.id) ? '正確答案' : '設為正確答案'"
          class="mt-1 rounded-full"
          @click="toggleCorrect(choice.id)"
        >
          <AnswerBubble
            :letter="LETTERS[index]"
            :filled="question.correct.includes(choice.id)"
            :square="question.type === 'multiple'"
            tone="brand"
            :class="question.correct.includes(choice.id) ? '' : 'bg-paper hover:border-brand'"
          />
        </button>
        <InlineMathField
          :ref="(el) => register(choice.id, el)"
          v-model="choice.text"
          class="flex-1 py-1"
          :placeholder="`選項 ${LETTERS[index]}`"
          :aria-label="`選項 ${LETTERS[index]}`"
          @keydown="onKeydown($event, index)"
          @paste="onPaste($event, index)"
        />
        <span v-if="question.correct.includes(choice.id)" class="mt-2 shrink-0 text-[13px] font-medium text-brand-strong max-sm:hidden">
          正確答案
        </span>
        <button
          type="button"
          :aria-label="`移除選項 ${LETTERS[index]}`"
          :disabled="question.choices.length <= 2"
          class="flex size-9 shrink-0 items-center justify-center rounded-full text-ink-3 hover:bg-ink/[0.08] hover:text-ink disabled:invisible"
          @click="removeChoice(index)"
        >
          <X class="size-4" />
        </button>
      </li>
    </ul>
    <button
      type="button"
      class="mt-2 flex h-11 w-full items-center gap-3 rounded-control border-[1.5px] border-dashed border-line-strong pl-3 text-[15px] text-ink-2 transition-colors hover:border-brand hover:text-brand"
      @click="focusChoice(addChoice().id)"
    >
      <span class="flex size-7 items-center justify-center"><Plus class="size-4" /></span>
      新增選項
    </button>
    <p class="mt-2 text-[13px] text-ink-3">
      點{{ question.type === 'multiple' ? '方框' : '圓圈' }}設定正確答案。可一次貼上多行文字，會自動拆成多個選項。
    </p>
  </div>
</template>
