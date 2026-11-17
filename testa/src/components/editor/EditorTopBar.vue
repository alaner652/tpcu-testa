<script setup lang="ts">
import { DropdownMenuRoot, DropdownMenuTrigger } from 'reka-ui'
import { ArrowLeft, Ellipsis, Keyboard, LayoutGrid, Moon, RotateCcw, Sparkles, Sun } from 'lucide-vue-next'
import Button from '@/components/ui/Button.vue'
import MenuContent from '@/components/ui/MenuContent.vue'
import MenuItem from '@/components/ui/MenuItem.vue'
import NavCapsule from '@/components/ui/NavCapsule.vue'
import Tip from '@/components/ui/Tip.vue'
import { useAi } from '@/composables/useAi'
import { useQuestionActions } from '@/composables/useQuestionActions'
import { helpOpen } from '@/composables/useShortcuts'
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
</script>

<template>
  <NavCapsule frame="" class="gap-1 px-2">
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
      class="absolute top-1/2 left-1/2 hidden -translate-x-1/2 -translate-y-1/2 items-center gap-1 md:flex"
    >
      <button
        v-for="m in MODES"
        :key="m.value"
        type="button"
        role="tab"
        :aria-selected="mode === m.value"
        class="pill-tab h-9"
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
          <MenuItem class="max-md:hidden" @select="helpOpen = true">
            <Keyboard />
            鍵盤快捷鍵
          </MenuItem>
        </MenuContent>
      </DropdownMenuRoot>
    </div>

    <template #below>
      <div role="tablist" aria-label="模式" class="mt-2 flex justify-center gap-1 md:hidden">
        <button
          v-for="m in MODES"
          :key="m.value"
          type="button"
          role="tab"
          :aria-selected="mode === m.value"
          class="pill-tab"
          @click="$emit('update:mode', m.value)"
        >
          {{ m.label }}
        </button>
      </div>
    </template>
  </NavCapsule>
</template>
