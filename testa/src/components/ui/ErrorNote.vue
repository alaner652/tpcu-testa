<script setup lang="ts">
import { RotateCw } from 'lucide-vue-next'
import Button from './Button.vue'

/** A failed load: the message in red and a retry button. plain: no padding, for use inside an already padded block. */
defineProps<{ message: string; retry?: () => unknown; plain?: boolean }>()
</script>

<template>
  <div role="alert" class="flex items-center gap-3 text-[15px] text-grade" :class="!plain && 'px-5 py-4 sm:px-6'">
    <span class="min-w-0 flex-1">{{ message }}</span>
    <Button v-if="retry" variant="outline" @click="retry"><RotateCw />重試</Button>
  </div>
</template>
