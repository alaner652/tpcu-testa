<script setup lang="ts">
import { DialogContent, DialogDescription, DialogOverlay, DialogPortal, DialogRoot, DialogTitle } from 'reka-ui'
import { cn } from '@/lib/utils'

/**
 * 寬螢幕時停靠在版面內，窄螢幕時變成抽屜。
 * 內容只寫一次，由 docked 決定呈現方式。
 */
const open = defineModel<boolean>('open', { required: true })
const props = defineProps<{
  docked: boolean
  side: 'left' | 'right'
  title: string
  class?: string
}>()
</script>

<template>
  <aside
    v-if="docked"
    v-show="open"
    :aria-label="title"
    :class="cn('flex shrink-0 flex-col bg-paper', side === 'left' ? 'border-r border-line' : 'border-l border-line', props.class)"
  >
    <slot />
  </aside>

  <DialogRoot v-else v-model:open="open">
    <DialogPortal>
      <DialogOverlay class="panel-overlay fixed inset-0 z-40 bg-black/35" />
      <DialogContent
        :data-side="side"
        @open-auto-focus.prevent
        :class="
          cn(
            'panel-content fixed inset-y-0 z-50 flex flex-col bg-paper outline-none',
            props.class,
            side === 'left'
              ? 'left-0 w-[min(calc(100vw-3.5rem),19rem)]' : 'right-0 w-[min(100vw,27rem)]',
          )
        "
      >
        <DialogTitle class="sr-only">{{ title }}</DialogTitle>
        <DialogDescription class="sr-only">{{ title }}</DialogDescription>
        <slot />
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>

<style>
.panel-overlay[data-state='open'] {
  animation: panel-fade 160ms ease-out;
}
.panel-content[data-state='open'][data-side='left'] {
  animation: panel-in-left 200ms cubic-bezier(0.2, 0.8, 0.2, 1);
}
.panel-content[data-state='open'][data-side='right'] {
  animation: panel-in-right 200ms cubic-bezier(0.2, 0.8, 0.2, 1);
}
@keyframes panel-fade {
  from {
    opacity: 0;
  }
}
@keyframes panel-in-left {
  from {
    transform: translateX(-100%);
  }
}
@keyframes panel-in-right {
  from {
    transform: translateX(100%);
  }
}
</style>
