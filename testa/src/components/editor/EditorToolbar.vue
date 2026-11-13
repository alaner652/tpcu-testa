<script setup lang="ts">
import { DropdownMenuRoot, DropdownMenuTrigger } from 'reka-ui'
import { Eye, Plus, Sparkles } from 'lucide-vue-next'
import MenuContent from '@/components/ui/MenuContent.vue'
import MenuItem from '@/components/ui/MenuItem.vue'
import Tip from '@/components/ui/Tip.vue'
import { useBanks } from '@/composables/useBanks'
import { useQuestionActions } from '@/composables/useQuestionActions'
import { QUESTION_TYPES } from '@/lib/question'

/**
 * 跟著題卡走的工具列：桌面在題卡右側直排，手機在畫面底部橫排。
 * 新題目會插在目前選取的題目下方。
 */
const props = defineProps<{ orientation: 'vertical' | 'horizontal' }>()
defineEmits<{ import: []; preview: [] }>()
const store = useBanks()
const { add } = useQuestionActions()

const vertical = props.orientation === 'vertical'
const item = vertical
  ? 'flex size-11 items-center justify-center rounded-full text-ink-2 transition-colors hover:bg-brand-soft hover:text-brand-strong'
  : 'flex h-12 flex-1 flex-col items-center justify-center gap-0.5 rounded-control text-[11px] text-ink-2 active:bg-brand-soft'
</script>

<template>
  <div
    role="toolbar"
    aria-label="題目工具列"
    :class="
      vertical
        ? 'flex w-14 flex-col items-center gap-1 rounded-full bg-paper py-1.5 shadow-card'
        : 'flex items-center gap-1 border-t border-line bg-paper px-2 pt-1 pb-[max(0.25rem,env(safe-area-inset-bottom))]'
    "
  >
    <DropdownMenuRoot>
      <DropdownMenuTrigger
        aria-label="新增題目"
        title="新增題目（插在目前這題下方）"
        :class="
          vertical
            ? 'flex size-11 items-center justify-center rounded-full bg-brand text-brand-fg transition-colors hover:bg-brand-strong'
            : 'flex h-12 flex-1 flex-col items-center justify-center gap-0.5 rounded-control text-[11px] font-medium text-brand active:bg-brand-soft'
        "
      >
        <Plus class="size-5" />
        <span v-if="!vertical">新增題目</span>
      </DropdownMenuTrigger>
      <MenuContent :align="vertical ? 'start' : 'center'" :side="vertical ? 'left' : 'top'" class="w-44" @close-auto-focus.prevent>
        <MenuItem
          v-for="t in QUESTION_TYPES"
          :key="t.value"
          class="h-10 text-[15px]"
          @select="add(t.value, store.activeId.value)"
        >
          <component :is="t.icon" />
          {{ t.label }}題
        </MenuItem>
      </MenuContent>
    </DropdownMenuRoot>

    <Tip label="AI 匯入 Word 或 PDF" :side="vertical ? 'left' : 'top'">
      <button type="button" :class="item" aria-label="AI 匯入題目" @click="$emit('import')">
        <Sparkles class="size-5" />
        <span v-if="!vertical">AI 匯入</span>
      </button>
    </Tip>
    <Tip label="以考生身分模擬作答" :side="vertical ? 'left' : 'top'">
      <button type="button" :class="item" aria-label="以考生身分模擬作答" @click="$emit('preview')">
        <Eye class="size-5" />
        <span v-if="!vertical">模擬作答</span>
      </button>
    </Tip>
  </div>
</template>
