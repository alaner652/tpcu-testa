<script setup lang="ts">
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

const button = cva(
  'inline-flex shrink-0 items-center justify-center gap-1.5 rounded-control text-sm font-medium whitespace-nowrap select-none transition-colors duration-100 disabled:pointer-events-none disabled:opacity-45 [&_svg]:size-4 [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        primary: 'bg-brand text-brand-fg hover:bg-brand-strong active:bg-brand-strong',
        outline: 'border border-line-strong bg-paper text-ink hover:border-brand hover:text-brand active:bg-brand-soft',
        ghost: 'text-ink-2 hover:bg-ink/[0.06] hover:text-ink active:bg-ink/10',
        danger: 'text-ink-2 hover:bg-grade-soft hover:text-grade',
      },
      size: {
        sm: 'h-9 px-3',
        md: 'h-10 px-4',
        icon: 'size-9 max-md:size-10',
      },
    },
    defaultVariants: { variant: 'ghost', size: 'sm' },
  },
)

type Variants = VariantProps<typeof button>

const props = defineProps<{
  variant?: Variants['variant']
  size?: Variants['size']
  class?: string
}>()
</script>

<template>
  <button type="button" :class="cn(button({ variant, size }), props.class)">
    <slot />
  </button>
</template>
