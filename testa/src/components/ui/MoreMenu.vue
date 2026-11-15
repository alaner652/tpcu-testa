<script setup lang="ts">
import type { Component } from 'vue'
import { DropdownMenuRoot, DropdownMenuTrigger } from 'reka-ui'
import { Ellipsis } from 'lucide-vue-next'
import MenuContent from './MenuContent.vue'
import MenuItem from './MenuItem.vue'

/** The "..." menu: rarely used actions live here so each row keeps one primary button. */
export interface MoreAction {
  label: string
  icon: Component
  run: () => void
  danger?: boolean
}

defineProps<{ actions: MoreAction[]; label?: string }>()
</script>

<template>
  <DropdownMenuRoot v-if="actions.length">
    <DropdownMenuTrigger
      class="relative z-10 flex size-9 items-center justify-center rounded-full text-ink-2 transition-colors hover:bg-ink/[0.06] hover:text-ink data-[state=open]:bg-ink/[0.06] max-md:size-10"
      :aria-label="label ?? '更多動作'"
      :title="label ?? '更多動作'"
    >
      <Ellipsis class="size-5" />
    </DropdownMenuTrigger>
    <MenuContent align="end">
      <MenuItem
        v-for="a in actions"
        :key="a.label"
        class="h-10"
        :class="a.danger ? 'text-grade data-[highlighted]:bg-grade-soft [&_svg]:text-grade' : ''"
        @select="a.run"
      >
        <component :is="a.icon" />{{ a.label }}
      </MenuItem>
    </MenuContent>
  </DropdownMenuRoot>
  <!-- Rows without extra actions keep the slot, so buttons and labels line up down the list -->
  <span v-else class="size-9 shrink-0 max-md:size-10" aria-hidden="true" />
</template>
