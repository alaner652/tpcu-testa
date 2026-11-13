<script setup lang="ts">
import { cn } from '@/lib/utils'

/**
 * 答案卡圓圈：單選為圓形、多選為方形；塗黑代表選取。
 * 整套系統中「正確答案」與「考生作答」都用這個元件表示。
 */
const props = defineProps<{
  letter: string
  filled: boolean
  square?: boolean
  tone?: 'ink' | 'brand'
  size?: 'sm' | 'md'
  class?: string
}>()
</script>

<template>
  <span
    aria-hidden="true"
    :class="
      cn(
        'inline-flex shrink-0 items-center justify-center border font-sans font-medium tabular-nums transition-colors duration-100',
        size === 'sm' ? 'size-5 text-[10px]' : 'size-7 text-xs',
        square ? 'rounded-[5px]' : 'rounded-full',
        filled
          ? tone === 'brand'
            ? 'border-brand bg-brand text-brand-fg'
            : 'border-ink bg-ink text-paper'
          : 'border-line-strong text-ink-2',
        props.class,
      )
    "
  >
    {{ letter }}
  </span>
</template>
