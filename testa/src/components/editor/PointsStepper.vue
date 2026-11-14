<script setup lang="ts">
import { Minus, Plus } from 'lucide-vue-next'

const model = defineModel<number>({ required: true })
/** 這個數字是什麼（給螢幕報讀用）：出題時是配分，閱卷時是給分 */
const { label = '配分' } = defineProps<{ label?: string }>()

function clamp(value: number) {
  return Math.min(100, Math.max(0, Math.round(value * 2) / 2))
}
function onInput(event: Event) {
  const value = Number((event.target as HTMLInputElement).value)
  model.value = Number.isFinite(value) ? clamp(value) : 0
}
</script>

<template>
  <div class="inline-flex items-center gap-1.5">
    <div class="inline-flex h-9 items-center rounded-full border border-line-strong max-md:h-10">
      <button
        type="button"
        :aria-label="`減少${label}`"
        class="flex h-full w-9 items-center justify-center rounded-l-full text-ink-2 hover:bg-ink/[0.06] hover:text-ink disabled:opacity-40"
        :disabled="model <= 0"
        @click="model = clamp(model - 1)"
      >
        <Minus class="size-3.5" />
      </button>
      <input
        :value="model"
        type="number"
        inputmode="decimal"
        min="0"
        max="100"
        step="1"
        :aria-label="label"
        class="h-full w-8 [appearance:textfield] bg-transparent text-center text-[15px] font-semibold text-grade tabular-nums outline-none [&::-webkit-inner-spin-button]:appearance-none"
        @change="onInput"
      />
      <button
        type="button"
        :aria-label="`增加${label}`"
        class="flex h-full w-9 items-center justify-center rounded-r-full text-ink-2 hover:bg-ink/[0.06] hover:text-ink"
        @click="model = clamp(model + 1)"
      >
        <Plus class="size-3.5" />
      </button>
    </div>
    <span class="text-sm text-ink-2">分</span>
  </div>
</template>
