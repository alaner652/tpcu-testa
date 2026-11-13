<script setup lang="ts">
import { cn } from '@/lib/utils'

const props = defineProps<{ class?: string }>()
</script>

<template>
  <div :class="cn('animate-pulse rounded-control bg-ink/[0.07]', props.class)" />
</template>
