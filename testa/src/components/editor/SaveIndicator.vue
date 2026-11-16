<script setup lang="ts">
import { computed } from 'vue'
import { Cloud, LoaderCircle, PencilLine, TriangleAlert } from 'lucide-vue-next'
import { useBanks } from '@/composables/useBanks'

const { saveState, savedAt, loading } = useBanks()

const time = computed(() =>
  new Intl.DateTimeFormat('zh-TW', { hour: '2-digit', minute: '2-digit', hour12: false }).format(savedAt.value),
)
const label = computed(() => {
  if (saveState.value === 'saving') return '儲存中…'
  if (saveState.value === 'dirty') return '編輯中'
  if (saveState.value === 'error') return '尚未儲存'
  return `已儲存 ${time.value}`
})
</script>

<template>
  <p v-if="!loading" class="flex items-center gap-1.5 text-sm text-ink-2" role="status" aria-live="polite" :title="label">
    <LoaderCircle v-if="saveState === 'saving'" class="size-4 animate-spin" />
    <PencilLine v-else-if="saveState === 'dirty'" class="size-4" />
    <TriangleAlert v-else-if="saveState === 'error'" class="size-4 text-grade" />
    <Cloud v-else class="size-4 text-brand" />
    <span :class="saveState === 'error' ? 'font-medium text-grade' : 'max-sm:sr-only'">{{ label }}</span>
  </p>
</template>
