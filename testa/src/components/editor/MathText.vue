<script setup lang="ts">
import { computed } from 'vue'
import { renderMathText } from '@/lib/math'

const props = defineProps<{ text: string; tag?: string }>()
const html = computed(() => renderMathText(props.text))
</script>

<template>
  <!-- renderMathText escapes all text outside formulas -->
  <component :is="tag ?? 'span'" v-html="html" />
</template>
