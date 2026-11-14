<script setup lang="ts">
import { nextTick, ref } from 'vue'
import AutoTextarea from '@/components/ui/AutoTextarea.vue'
import { hasMath } from '@/lib/math'
import MathText from './MathText.vue'

/**
 * Click-to-edit text. With formulas it shows the typeset result and the LaTeX source only while focused,
 * so the editor shows exactly what candidates see and needs no separate preview.
 */
defineOptions({ inheritAttrs: false })
const model = defineModel<string>({ required: true })
const props = defineProps<{ rows?: number; class?: string }>()

const field = ref<InstanceType<typeof AutoTextarea> | null>(null)
const focused = ref(false)

async function onFocus() {
  focused.value = true
  await nextTick()
  field.value?.resize()
}

defineExpose({
  focus: () => field.value?.focus(),
  insertAtCursor: (snippet: string) => field.value?.insertAtCursor(snippet),
})
</script>

<template>
  <div :class="['min-w-0', props.class]">
    <AutoTextarea
      ref="field"
      v-model="model"
      v-bind="$attrs"
      :rows="rows"
      :class="!focused && hasMath(model) ? 'sr-only' : 'exam-text focus:outline-none'"
      @focus="onFocus"
      @blur="focused = false"
    />
    <MathText
      v-if="!focused && hasMath(model)"
      :text="model"
      tag="div"
      class="exam-text cursor-text"
      @click="field?.focus()"
    />
  </div>
</template>
