<script setup lang="ts">
import { reactive } from 'vue'
import { Plus, X } from 'lucide-vue-next'
import type { Question } from '@/types/question'
import Switch from '@/components/ui/Switch.vue'
import { uid } from '@/lib/utils'

const props = defineProps<{ question: Question }>()

const drafts = reactive<Record<string, string>>({})

function commit(blankId: string) {
  const blank = props.question.blanks.find((b) => b.id === blankId)
  const value = (drafts[blankId] ?? '').trim()
  if (!blank || !value) return
  if (!blank.answers.includes(value)) blank.answers.push(value)
  drafts[blankId] = ''
}

function onKeydown(event: KeyboardEvent, blankId: string) {
  if (event.isComposing) return
  const blank = props.question.blanks.find((b) => b.id === blankId)!
  if (event.key === 'Enter') {
    event.preventDefault()
    commit(blankId)
  } else if (event.key === 'Backspace' && !drafts[blankId] && blank.answers.length) {
    blank.answers.pop()
  }
}

function perBlank() {
  const n = props.question.blanks.length
  return Math.round((props.question.points / n) * 100) / 100
}
</script>

<template>
  <div>
    <ul class="space-y-2">
      <li v-for="(blank, index) in question.blanks" :key="blank.id" class="flex items-start gap-2">
        <span class="mt-2 w-12 shrink-0 text-sm text-ink-2">空格 {{ index + 1 }}</span>
        <label
          class="flex min-h-11 min-w-0 flex-1 cursor-text flex-wrap items-center gap-1.5 rounded-control border-[1.5px] border-transparent bg-sunken px-3 py-1 transition-colors hover:border-line-strong focus-within:!border-brand"
        >
          <span
            v-for="(answer, i) in blank.answers"
            :key="answer"
            class="inline-flex items-center gap-1 rounded-[4px] bg-brand py-0.5 pr-1 pl-2 font-mono text-xs text-brand-fg"
          >
            {{ answer }}
            <button
              type="button"
              :aria-label="`移除答案 ${answer}`"
              class="rounded-sm opacity-60 hover:opacity-100"
              @click="blank.answers.splice(i, 1)"
            >
              <X class="size-3" />
            </button>
          </span>
          <input
            v-model="drafts[blank.id]"
            :aria-label="`空格 ${index + 1} 的答案`"
            :placeholder="blank.answers.length ? '新增其他可接受的答案' : '輸入答案後按 Enter'"
            class="h-6 min-w-32 flex-1 bg-transparent text-sm outline-none"
            @keydown="onKeydown($event, blank.id)"
            @blur="commit(blank.id)"
          />
        </label>
        <button
          type="button"
          :aria-label="`移除空格 ${index + 1}`"
          :disabled="question.blanks.length <= 1"
          class="flex size-9 shrink-0 items-center justify-center rounded-control text-ink-3 hover:bg-ink/[0.06] hover:text-ink disabled:invisible"
          @click="question.blanks.splice(index, 1)"
        >
          <X class="size-4" />
        </button>
      </li>
    </ul>
    <div class="mt-2 flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
      <button
        type="button"
        class="inline-flex h-8 items-center gap-1.5 rounded-control px-2 text-sm text-ink-2 hover:bg-ink/[0.06] hover:text-ink"
        @click="question.blanks.push({ id: uid('b'), answers: [] })"
      >
        <Plus class="size-3.5" />
        新增空格
      </button>
      <label class="inline-flex items-center gap-2 text-sm text-ink-2">
        <Switch v-model="question.caseSensitive" />
        區分英文大小寫
      </label>
    </div>
    <p class="mt-1.5 text-xs text-ink-3">
      在題目中用「＿＿」標示空格位置。每個空格可設定多個可接受的答案<template v-if="question.blanks.length > 1"
        >，每格 {{ perBlank() }} 分</template
      >。
    </p>
  </div>
</template>
