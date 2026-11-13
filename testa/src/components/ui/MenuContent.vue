<script setup lang="ts">
import { DropdownMenuContent, DropdownMenuPortal } from 'reka-ui'
import { cn } from '@/lib/utils'

defineOptions({ inheritAttrs: false })

const props = defineProps<{
  align?: 'start' | 'center' | 'end'
  side?: 'top' | 'right' | 'bottom' | 'left'
  class?: string
}>()
</script>

<template>
  <DropdownMenuPortal>
    <DropdownMenuContent
      :align="align ?? 'start'"
      :side="side ?? 'bottom'"
      :side-offset="8"
      v-bind="$attrs"
      :class="
        cn(
          'z-[60] min-w-48 rounded-xl bg-paper p-1.5 shadow-lift',
          props.class,
        )
      "
    >
      <slot />
    </DropdownMenuContent>
  </DropdownMenuPortal>
</template>
