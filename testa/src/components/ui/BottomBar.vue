<script setup lang="ts" generic="T extends string">
import { computed, type Component } from 'vue'
import { DropdownMenuRoot, DropdownMenuTrigger } from 'reka-ui'
import { Check, Ellipsis } from 'lucide-vue-next'
import MenuContent from '@/components/ui/MenuContent.vue'
import MenuItem from '@/components/ui/MenuItem.vue'

/** Phone navigation at the bottom of a page: the `main` items as cells, the rest under "更多". */
const props = defineProps<{ items: readonly { value: T; label: string; icon: Component }[]; main: readonly T[]; current?: T; label: string }>()
defineEmits<{ select: [value: T] }>()

const cells = computed(() => props.items.filter((t) => props.main.includes(t.value)))
const more = computed(() => props.items.filter((t) => !props.main.includes(t.value)))
const inMore = computed(() => more.value.find((t) => t.value === props.current))
const cell = 'flex h-14 flex-1 flex-col items-center justify-center gap-0.5 text-[11px]'
</script>

<template>
  <nav class="flex border-t border-line bg-paper pb-[env(safe-area-inset-bottom)]" :aria-label="label">
    <button
      v-for="t in cells"
      :key="t.value"
      type="button"
      :class="[cell, current === t.value ? 'font-medium text-brand' : 'text-ink-2']"
      :aria-current="current === t.value ? 'page' : undefined"
      @click="$emit('select', t.value)"
    >
      <component :is="t.icon" class="size-5" />{{ t.label }}
    </button>
    <DropdownMenuRoot v-if="more.length">
      <DropdownMenuTrigger :class="[cell, inMore ? 'font-medium text-brand' : 'text-ink-2']">
        <component :is="inMore?.icon ?? Ellipsis" class="size-5" />{{ inMore?.label ?? '更多' }}
      </DropdownMenuTrigger>
      <MenuContent side="top" align="end" :collision-padding="12">
        <MenuItem
          v-for="t in more"
          :key="t.value"
          :class="current === t.value ? 'h-11 text-[15px] font-medium' : 'h-11 text-[15px]'"
          @select="$emit('select', t.value)"
        >
          <component :is="t.icon" />{{ t.label }}<Check v-if="current === t.value" class="ml-auto !text-brand" />
        </MenuItem>
      </MenuContent>
    </DropdownMenuRoot>
  </nav>
</template>
