<script setup lang="ts">
import { cn } from '@/lib/utils'

/**
 * Answer sheet bubble: round for single choice, square for multiple; filled means selected.
 * Used everywhere for both correct answers and candidate answers.
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
