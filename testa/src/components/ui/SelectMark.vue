<script setup lang="ts">
import { Check } from 'lucide-vue-next'

/**
 * The spot at the start of a row (avatar, seat number) that turns into a check box:
 * while picking, or when the pointer is over the row (the row needs the `group/row` class).
 */
defineProps<{ checked: boolean; picking: boolean; label: string }>()
defineEmits<{ toggle: [event: MouseEvent] }>()
</script>

<template>
  <span class="relative flex size-10 shrink-0 items-center justify-center">
    <span
      class="flex items-center justify-center transition-opacity"
      :class="picking ? 'opacity-0' : 'pointer-fine:group-hover/row:opacity-0'"
      aria-hidden="true"
    >
      <slot />
    </span>
    <button
      type="button"
      role="checkbox"
      :aria-checked="checked"
      :aria-label="label"
      class="absolute inset-0 flex items-center justify-center rounded-full transition-opacity focus-visible:opacity-100"
      :class="picking ? 'opacity-100' : 'opacity-0 pointer-fine:group-hover/row:opacity-100'"
      tabindex="-1"
      @click.stop="$emit('toggle', $event)"
    >
      <span
        class="flex size-6 items-center justify-center rounded-md border-2 transition-colors"
        :class="checked ? 'border-brand bg-brand text-brand-fg' : 'border-line-strong bg-paper'"
      >
        <Check v-if="checked" class="size-4" :stroke-width="3" />
      </span>
    </button>
  </span>
</template>
