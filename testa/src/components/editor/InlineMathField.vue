<script setup lang="ts">
import { nextTick, ref } from 'vue'
import AutoTextarea from '@/components/ui/AutoTextarea.vue'
import { hasMath } from '@/lib/math'
import MathText from './MathText.vue'

/**
 * 可直接點進去改的文字。含公式時平常顯示排版後的結果，取得焦點才顯示 LaTeX 原始碼，
 * 所以編輯畫面看到的內容與考生看到的一致，不需要另外的預覽區。
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
