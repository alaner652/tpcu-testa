<script setup lang="ts">
/**
 * Status label. One size everywhere; the tone carries the meaning:
 * live = happening now, done = finished or positive, warn = waiting or worth a look,
 * danger = a problem someone has to act on, neutral = inactive or plain information.
 */
export type BadgeTone = 'live' | 'done' | 'warn' | 'danger' | 'neutral'

defineProps<{ tone?: BadgeTone }>()

const TONE: Record<BadgeTone, string> = {
  live: 'bg-brand text-brand-fg',
  done: 'bg-brand-soft text-brand-strong',
  warn: 'bg-marker text-marker-ink',
  danger: 'bg-grade-soft text-grade',
  neutral: 'bg-sunken text-ink-2',
}
</script>

<template>
  <span
    class="inline-flex h-6 shrink-0 items-center gap-1 rounded-full px-2.5 text-[13px] leading-none font-medium whitespace-nowrap [&_svg]:size-3.5 [&_svg]:shrink-0"
    :class="TONE[tone ?? 'neutral']"
  >
    <slot />
  </span>
</template>
