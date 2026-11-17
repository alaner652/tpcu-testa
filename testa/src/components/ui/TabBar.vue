<script setup lang="ts" generic="T extends string">
import type { Component } from 'vue'

/** A row of tabs; the current one is a dashed capsule (pill-tab). Scrolls sideways when it does not fit. */
defineProps<{ tabs: readonly { value: T; label: string; icon: Component }[]; current: T; label?: string }>()
const emit = defineEmits<{ select: [value: T] }>()
</script>

<template>
  <div role="tablist" :aria-label="label" class="thin-scroll mb-4 flex gap-1 overflow-x-auto px-4 md:px-0">
    <button
      v-for="t in tabs"
      :key="t.value"
      type="button"
      role="tab"
      :aria-selected="current === t.value"
      class="pill-tab max-sm:px-3"
      @click="emit('select', t.value)"
    >
      <component :is="t.icon" class="size-4" aria-hidden="true" />{{ t.label }}
    </button>
  </div>
</template>
