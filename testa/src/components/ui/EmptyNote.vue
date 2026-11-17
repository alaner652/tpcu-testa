<script setup lang="ts">
/** Grey text for an empty list or a loading state. line: a divider above, for use under a card header. */
withDefaults(defineProps<{ line?: boolean; as?: 'p' | 'li' }>(), { as: 'p' })
</script>

<template>
  <component :is="as" class="px-5 py-6 text-[15px] text-ink-2 sm:px-6" :class="line && 'border-t border-line'"><slot /></component>
</template>
