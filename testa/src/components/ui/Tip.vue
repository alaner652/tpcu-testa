<script setup lang="ts">
import { TooltipContent, TooltipPortal, TooltipRoot, TooltipTrigger } from 'reka-ui'

defineProps<{
  label: string
  shortcut?: string
  side?: 'top' | 'right' | 'bottom' | 'left'
}>()
</script>

<template>
  <TooltipRoot>
    <TooltipTrigger as-child>
      <slot />
    </TooltipTrigger>
    <TooltipPortal>
      <TooltipContent
        :side="side ?? 'bottom'"
        :side-offset="6"
        class="z-[60] flex items-center gap-2 rounded-control bg-ink px-2 py-1 text-xs text-paper"
      >
        {{ label }}
        <kbd v-if="shortcut" class="font-sans text-paper/60">{{ shortcut }}</kbd>
      </TooltipContent>
    </TooltipPortal>
  </TooltipRoot>
</template>
