<script setup lang="ts">
/**
 * One setting: name and one line on the left, the control on the right.
 * Every control sits in the same fixed-width column, so the boxes line up down the page.
 */
defineProps<{ title: string; hint?: string; for?: string }>()
</script>

<template>
  <div class="flex flex-wrap items-center gap-x-6 gap-y-2 px-5 py-4 sm:px-6">
    <label :for="$props.for" class="min-w-0 flex-1 basis-64">
      <span class="block text-[15px] font-medium">{{ title }}</span>
      <span v-if="hint" class="mt-0.5 block text-sm text-ink-2">{{ hint }}</span>
    </label>
    <div class="flex w-full justify-end sm:w-48"><slot /></div>
  </div>
</template>
