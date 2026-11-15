<script setup lang="ts">
import { DropdownMenuLabel, DropdownMenuRoot, DropdownMenuSeparator, DropdownMenuTrigger } from 'reka-ui'
import { ArrowLeft, Ellipsis, LayoutGrid, Moon, RotateCcw, Sparkles, Sun } from 'lucide-vue-next'
import Button from '@/components/ui/Button.vue'
import MenuContent from '@/components/ui/MenuContent.vue'
import MenuItem from '@/components/ui/MenuItem.vue'
import Tip from '@/components/ui/Tip.vue'
import { useAi } from '@/composables/useAi'
import { useQuestionActions } from '@/composables/useQuestionActions'
import { useTheme } from '@/composables/useTheme'
import BankSwitcher from './BankSwitcher.vue'
import SaveIndicator from './SaveIndicator.vue'

defineProps<{ mode: 'edit' | 'preview' }>()
defineEmits<{ openOverview: []; import: []; 'update:mode': [mode: 'edit' | 'preview'] }>()

const { importDemo } = useQuestionActions()
const dev = import.meta.env.DEV
const { importEnabled } = useAi()
const { dark, toggle } = useTheme()

const MODES = [
  { value: 'edit', label: '編輯' },
  { value: 'preview', label: '模擬作答' },
] as const

const SHORTCUTS = [
  ['儲存', 'Ctrl S'],
  ['複製題目', 'Ctrl D'],
  ['在下方新增題目', 'Ctrl Enter'],
  ['上移、下移', 'Alt ↑ ↓'],
]
</script>

<template>
  <header class="relative z-20 shrink-0 bg-paper shadow-card">
    <div class="flex h-14 items-center gap-1 px-3 sm:px-5">
      <!-- Focus mode: back to the bank list at top left -->
      <Tip label="回到題庫列表">
        <RouterLink
          to="/banks"
          aria-label="回到題庫列表"
          class="flex size-10 shrink-0 items-center justify-center rounded-full text-ink-2 transition-colors hover:bg-ink/[0.06] hover:text-ink"
        >
          <ArrowLeft class="size-5" />
        </RouterLink>
      </Tip>

      <div class="min-w-0 flex-1 sm:flex-none"><BankSwitcher /></div>

      <!-- Mode switch: centred on desktop, second row on phones -->
      <div
        role="tablist"
        aria-label="模式"
        class="absolute top-2 left-1/2 hidden h-10 -translate-x-1/2 items-center rounded-full bg-sunken p-1 md:flex"
      >
        <button
          v-for="m in MODES"
          :key="m.value"
          type="button"
          role="tab"
          :aria-selected="mode === m.value"
          class="h-8 rounded-full px-5 text-[15px] transition-colors"
          :class="mode === m.value ? 'bg-brand font-medium text-brand-fg' : 'text-ink-2 hover:text-ink'"
          @click="$emit('update:mode', m.value)"
        >
          {{ m.label }}
        </button>
      </div>

      <div class="ml-auto flex shrink-0 items-center gap-0.5 sm:gap-1">
        <SaveIndicator class="mr-1 sm:mr-2" />
        <Button v-if="importEnabled && mode === 'edit'" variant="outline" class="max-md:hidden" @click="$emit('import')">
          <Sparkles />AI 匯入
        </Button>
        <Tip v-if="mode === 'edit'" label="題號卡">
          <Button size="icon" class="xl:hidden" aria-label="開啟題號卡" @click="$emit('openOverview')"><LayoutGrid /></Button>
        </Tip>
        <DropdownMenuRoot>
          <DropdownMenuTrigger as-child>
            <Button size="icon" aria-label="更多"><Ellipsis /></Button>
          </DropdownMenuTrigger>
          <MenuContent align="end" class="w-56">
            <MenuItem @select="toggle">
              <component :is="dark ? Sun : Moon" />
              {{ dark ? '切換為淺色' : '切換為深色' }}
            </MenuItem>
            <MenuItem v-if="dev" @select="importDemo">
              <RotateCcw />
              匯入示範題庫（開發用）
            </MenuItem>
            <DropdownMenuSeparator class="my-1 h-px bg-line max-md:hidden" />
            <DropdownMenuLabel class="px-2 pt-1.5 pb-1 text-xs text-ink-3 max-md:hidden">鍵盤快捷鍵</DropdownMenuLabel>
            <div
              v-for="[name, keys] in SHORTCUTS"
              :key="name"
              class="flex items-center justify-between px-2 py-1 text-sm text-ink-2 max-md:hidden"
            >
              {{ name }}
              <kbd class="font-sans text-xs text-ink-3">{{ keys }}</kbd>
            </div>
          </MenuContent>
        </DropdownMenuRoot>
      </div>
    </div>

    <div role="tablist" aria-label="模式" class="flex border-t border-line md:hidden">
      <button
        v-for="m in MODES"
        :key="m.value"
        type="button"
        role="tab"
        :aria-selected="mode === m.value"
        class="relative h-11 flex-1 text-[15px] transition-colors"
        :class="mode === m.value ? 'font-medium text-brand' : 'text-ink-2'"
        @click="$emit('update:mode', m.value)"
      >
        {{ m.label }}
        <span v-if="mode === m.value" class="absolute inset-x-6 bottom-0 h-[3px] rounded-t-full bg-brand" />
      </button>
    </div>
  </header>
</template>
