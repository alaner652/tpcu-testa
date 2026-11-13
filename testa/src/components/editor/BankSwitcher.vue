<script setup lang="ts">
import { DropdownMenuRoot, DropdownMenuSeparator, DropdownMenuTrigger } from 'reka-ui'
import { Check, ChevronDown, Plus } from 'lucide-vue-next'
import MenuContent from '@/components/ui/MenuContent.vue'
import MenuItem from '@/components/ui/MenuItem.vue'
import { useBanks } from '@/composables/useBanks'
import { useQuestionActions } from '@/composables/useQuestionActions'

const { banks, bank, bankId, selectBank } = useBanks()
const { createBank } = useQuestionActions()
</script>

<template>
  <DropdownMenuRoot>
    <DropdownMenuTrigger
      class="flex h-10 max-w-full min-w-0 items-center gap-1.5 rounded-control px-2.5 text-left transition-colors hover:bg-ink/[0.06] data-[state=open]:bg-ink/[0.06]"
      aria-label="切換題庫"
    >
      <span class="truncate text-[15px] font-medium">{{ bank?.name || '未命名題庫' }}</span>
      <ChevronDown class="size-4 shrink-0 text-ink-3" />
    </DropdownMenuTrigger>
    <MenuContent class="w-[min(20rem,calc(100vw-2rem))]">
      <p class="px-2 pt-1.5 pb-1 text-[13px] text-ink-3">我的題庫</p>
      <MenuItem v-for="b in banks" :key="b.id" class="h-11" @select="selectBank(b.id)">
        <span class="min-w-0 flex-1">
          <span class="block truncate text-[15px]">{{ b.name || '未命名題庫' }}</span>
        </span>
        <span class="text-[13px] text-ink-3 tabular-nums">{{ b.questions.length }} 題</span>
        <Check class="!text-brand" :class="b.id === bankId ? '' : 'invisible'" />
      </MenuItem>
      <DropdownMenuSeparator class="my-1.5 h-px bg-line" />
      <MenuItem class="h-10 text-[15px] text-brand" @select="createBank">
        <Plus class="!text-brand" />
        新增題庫
      </MenuItem>
    </MenuContent>
  </DropdownMenuRoot>
</template>
