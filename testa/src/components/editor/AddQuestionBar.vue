<script setup lang="ts">
import { Sparkles } from 'lucide-vue-next'
import Button from '@/components/ui/Button.vue'
import { useQuestionActions } from '@/composables/useQuestionActions'
import { DEMO_FEATURES } from '@/lib/flags'
import { QUESTION_TYPES } from '@/lib/question'

defineEmits<{ import: [] }>()
const { add } = useQuestionActions()
</script>

<template>
  <div class="flex flex-wrap items-center gap-2">
    <Button v-for="t in QUESTION_TYPES" :key="t.value" variant="outline" size="md" @click="add(t.value)">
      <component :is="t.icon" />
      {{ t.label }}題
    </Button>
    <Button v-if="DEMO_FEATURES" size="md" class="text-brand hover:bg-brand-soft hover:text-brand-strong" @click="$emit('import')">
      <Sparkles />
      從 Word 或 PDF 匯入
    </Button>
  </div>
</template>
