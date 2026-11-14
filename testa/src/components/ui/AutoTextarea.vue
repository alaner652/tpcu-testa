<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { cn } from '@/lib/utils'

const model = defineModel<string>({ required: true })
const props = defineProps<{ class?: string; rows?: number }>()

const el = ref<HTMLTextAreaElement | null>(null)

function resize() {
  const node = el.value
  if (!node) return
  node.style.height = 'auto'
  node.style.height = `${node.scrollHeight}px`
}

/** Inserts text at the cursor; cursorOffset is where the cursor ends up, relative to the start of the snippet. */
async function insertAtCursor(snippet: string, cursorOffset = snippet.length) {
  const node = el.value
  if (!node) return
  const start = node.selectionStart ?? model.value.length
  const end = node.selectionEnd ?? start
  model.value = model.value.slice(0, start) + snippet + model.value.slice(end)
  await nextTick()
  node.focus()
  node.setSelectionRange(start + cursorOffset, start + cursorOffset)
}

onMounted(resize)
watch(model, () => nextTick(resize))

defineExpose({ el, insertAtCursor, resize, focus: () => el.value?.focus() })
</script>

<template>
  <textarea
    ref="el"
    v-model="model"
    :rows="rows ?? 1"
    :class="cn('block w-full resize-none overflow-hidden bg-transparent outline-none', props.class)"
  />
</template>
