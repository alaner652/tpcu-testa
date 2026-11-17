<script lang="ts">
/** Class for the buttons in the bar (it is dark in both themes) */
export const barButton =
  'inline-flex h-9 items-center gap-1.5 rounded-full px-3 text-sm font-medium transition-colors hover:bg-paper/15 disabled:opacity-40 [&_svg]:size-4'
</script>

<script setup lang="ts">
import { X } from 'lucide-vue-next'

/**
 * Floats at the bottom while rows are picked: how many, pick all, and the actions for them.
 * The actions go in the default slot; a form (minutes and reason for extra time) can take their place.
 */
defineProps<{ count: number; total: number; unit: string }>()
defineEmits<{ all: []; clear: [] }>()
</script>

<template>
  <!-- Room at the end of the page so the bar never covers the last rows -->
  <div v-if="count > 0" class="h-20" aria-hidden="true" />
  <Teleport to="body">
    <Transition
      enter-from-class="translate-y-4 opacity-0"
      leave-to-class="translate-y-4 opacity-0"
      enter-active-class="transition duration-150 ease-out"
      leave-active-class="transition duration-100 ease-in"
    >
      <div
        v-if="count > 0"
        role="toolbar"
        :aria-label="`已選 ${count} ${unit}的動作`"
        class="fixed inset-x-3 bottom-[calc(4.25rem+env(safe-area-inset-bottom))] z-30 mx-auto flex max-w-fit flex-wrap items-center gap-x-1 gap-y-2 rounded-card bg-ink py-2 pr-2 pl-1.5 text-paper shadow-lift md:bottom-5"
      >
        <button
          type="button"
          class="flex size-9 shrink-0 items-center justify-center rounded-full text-paper/70 hover:bg-paper/10 hover:text-paper"
          aria-label="取消選取"
          title="取消選取（Esc）"
          @click="$emit('clear')"
        >
          <X class="size-4" />
        </button>
        <span class="mr-1 text-[15px] font-medium whitespace-nowrap tabular-nums">已選 {{ count }} {{ unit }}</span>
        <button
          v-if="count < total"
          type="button"
          class="mr-1 h-9 rounded-full px-3 text-sm whitespace-nowrap text-paper/80 underline-offset-2 hover:text-paper hover:underline"
          @click="$emit('all')"
        >
          全選 {{ total }} {{ unit }}
        </button>
        <div class="flex flex-wrap items-center gap-1 [&_button]:whitespace-nowrap">
          <slot />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
