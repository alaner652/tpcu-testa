<script setup lang="ts">
import { computed } from 'vue'
import { renderMathText } from '@/lib/math'

const props = defineProps<{ text: string; tag?: string }>()
const html = computed(() => renderMathText(props.text))
</script>

<template>
  <!-- renderMathText 已跳脫公式以外的所有文字 -->
  <component :is="tag ?? 'span'" v-html="html" />
</template>
