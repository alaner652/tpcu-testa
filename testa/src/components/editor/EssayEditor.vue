<script setup lang="ts">
import type { Question } from '@/types/question'
import AutoTextarea from '@/components/ui/AutoTextarea.vue'
import Switch from '@/components/ui/Switch.vue'

const props = defineProps<{ question: Question }>()

function onLimit(event: Event) {
  const value = Number((event.target as HTMLInputElement).value)
  props.question.wordLimit = value > 0 ? Math.round(value) : null
}
</script>

<template>
  <div class="space-y-3">
    <div>
      <label :for="`rubric-${question.id}`" class="mb-1 block text-sm text-ink-2">評分規準與參考答案</label>
      <AutoTextarea
        :id="`rubric-${question.id}`"
        v-model="question.rubric"
        :rows="3"
        placeholder="每行一項給分重點，例如：寫出歸納假設（3 分）"
        class="rounded-control border-[1.5px] border-transparent bg-sunken px-3.5 py-3 text-base leading-relaxed transition-colors hover:border-line-strong focus:border-brand focus:outline-none"
      />
    </div>
    <div class="flex flex-wrap items-start gap-x-8 gap-y-3">
      <label class="inline-flex items-center gap-2 text-sm text-ink-2">
        字數上限
        <input
          :value="question.wordLimit ?? ''"
          type="number"
          inputmode="numeric"
          min="0"
          step="50"
          placeholder="不限"
          class="h-9 w-20 [appearance:textfield] rounded-control border-[1.5px] border-transparent bg-sunken px-2 text-sm text-ink tabular-nums transition-colors outline-none hover:border-ink-3 focus:border-brand [&::-webkit-inner-spin-button]:appearance-none"
          @change="onLimit"
        />
        字
      </label>
      <label class="flex max-w-sm items-start gap-2.5">
        <Switch v-model="question.aiGrading" class="mt-0.5" />
        <span>
          <span class="block text-sm text-ink">AI 輔助評分</span>
          <span class="block text-xs text-ink-3">閱卷時依評分規準提供建議分數，最終分數由教師決定。</span>
        </span>
      </label>
    </div>
  </div>
</template>
