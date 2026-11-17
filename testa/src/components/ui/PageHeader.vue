<script setup lang="ts">
/** The page title, with an optional line under it and actions on the right. Content in the default slot goes below. */
defineProps<{ title: string; hint?: string }>()
</script>

<template>
  <div class="mb-5 px-4 md:px-0">
    <div class="flex flex-wrap items-center gap-3">
      <div class="min-w-0 flex-1">
        <h1 class="text-[26px] font-bold sm:text-[30px]">{{ title }}</h1>
        <p v-if="hint || $slots.hint" class="mt-1 text-[15px] text-ink-2"><slot name="hint">{{ hint }}</slot></p>
      </div>
      <slot name="actions" />
    </div>
    <slot />
  </div>
</template>
