<script setup lang="ts">
import { DropdownMenuItem } from 'reka-ui'
import { cn } from '@/lib/utils'

const props = defineProps<{ class?: string; disabled?: boolean }>()
</script>

<template>
  <DropdownMenuItem
    :disabled="disabled"
    :class="
      cn(
        'flex min-h-8 cursor-pointer items-center gap-2 rounded-control px-2 py-1.5 text-sm text-ink outline-none select-none data-[highlighted]:bg-ink/[0.06] data-[disabled]:pointer-events-none data-[disabled]:opacity-45 max-md:min-h-10 [&_svg]:size-4 [&_svg]:shrink-0 [&_svg]:text-ink-3',
        props.class,
      )
    "
  >
    <slot />
  </DropdownMenuItem>
</template>
