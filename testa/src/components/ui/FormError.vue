<script setup lang="ts">
import { CircleAlert } from 'lucide-vue-next'

/** A short red message under a form: what went wrong and what to do. */
</script>

<template>
  <p role="alert" class="flex items-start gap-1.5 text-sm text-grade">
    <CircleAlert class="mt-0.5 size-4 shrink-0" aria-hidden="true" /><span class="min-w-0"><slot /></span>
  </p>
</template>
