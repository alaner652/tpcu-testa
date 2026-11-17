<script setup lang="ts">
/**
 * The title row of a card: title, an optional count beside it, one line under it, actions on the right (default slot).
 * for: the id of a control the title labels, such as the switch on the right.
 */
defineProps<{ title: string; meta?: string; hint?: string; for?: string }>()
</script>

<template>
  <div class="flex flex-wrap items-center gap-3 px-5 py-4 sm:px-6">
    <div class="min-w-0 flex-1">
      <h2 class="flex flex-wrap items-baseline gap-x-3 text-[17px] font-semibold">
        <label v-if="$props.for" :for="$props.for">{{ title }}</label><template v-else>{{ title }}</template>
        <span v-if="meta" class="text-sm font-normal text-ink-2 tabular-nums">{{ meta }}</span>
      </h2>
      <p v-if="hint || $slots.hint" class="mt-0.5 text-sm text-ink-2"><slot name="hint">{{ hint }}</slot></p>
    </div>
    <slot />
  </div>
</template>
