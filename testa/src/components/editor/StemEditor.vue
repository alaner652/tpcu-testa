<script setup lang="ts">
import { ref } from 'vue'
import { DropdownMenuRoot, DropdownMenuTrigger } from 'reka-ui'
import { Sigma } from 'lucide-vue-next'
import MenuContent from '@/components/ui/MenuContent.vue'
import MenuItem from '@/components/ui/MenuItem.vue'
import InlineMathField from './InlineMathField.vue'
import MathText from './MathText.vue'

const model = defineModel<string>({ required: true })
const input = ref<InstanceType<typeof InlineMathField> | null>(null)

const SNIPPETS = [
  { label: '分數', tex: '\\frac{a}{b}' },
  { label: '根號', tex: '\\sqrt{x}' },
  { label: '次方', tex: 'x^{2}' },
  { label: '下標', tex: 'a_{n}' },
  { label: '總和', tex: '\\sum_{k=1}^{n} k' },
  { label: '積分', tex: '\\int_{a}^{b} f(x)\\,dx' },
  { label: '極限', tex: '\\lim_{x\\to 0} f(x)' },
  { label: '矩陣', tex: '\\begin{bmatrix}a&b\\\\c&d\\end{bmatrix}' },
]

defineExpose({ focus: () => input.value?.focus() })
</script>

<template>
  <div class="min-w-0">
    <InlineMathField ref="input" v-model="model" placeholder="輸入題目" aria-label="題目" />
    <div class="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1">
      <DropdownMenuRoot>
        <DropdownMenuTrigger
          class="-ml-2 inline-flex h-8 items-center gap-1 rounded-full px-2.5 text-sm text-brand hover:bg-brand-soft max-md:h-9"
        >
          <Sigma class="size-4" />
          插入公式
        </DropdownMenuTrigger>
        <MenuContent class="grid w-64 grid-cols-2" @close-auto-focus.prevent>
          <MenuItem v-for="s in SNIPPETS" :key="s.label" class="justify-between" @select="input?.insertAtCursor(`$${s.tex}$`)">
            <span class="text-ink-2">{{ s.label }}</span>
            <MathText :text="`$${s.tex}$`" class="text-xs" />
          </MenuItem>
        </MenuContent>
      </DropdownMenuRoot>
      <p class="text-[13px] text-ink-3">公式用 <code class="font-mono">$…$</code> 包住，點到別處就會顯示排版結果</p>
    </div>
  </div>
</template>
