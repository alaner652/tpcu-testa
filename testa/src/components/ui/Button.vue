<script setup lang="ts">
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

/**
 * Rules (see testa/README.md, "UI rules"):
 * - sm sits in rows and card headers, md beside form fields (same height as `.field`), lg is a full-width form submit.
 * - primary: at most one per row or form. destructive: confirms deleting or ending something.
 * - In a pair, cancel (ghost) comes first and the confirming button last.
 */
const button = cva(
  'inline-flex shrink-0 items-center justify-center gap-1.5 rounded-full text-sm font-medium whitespace-nowrap select-none transition-colors duration-100 disabled:pointer-events-none disabled:opacity-45 [&_svg]:size-4 [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        primary: 'bg-brand text-brand-fg hover:bg-brand-strong active:bg-brand-strong',
        outline: 'border border-line-strong bg-paper text-ink hover:border-brand hover:text-brand active:bg-brand-soft',
        ghost: 'text-ink-2 hover:bg-ink/[0.06] hover:text-ink active:bg-ink/10',
        danger: 'text-ink-2 hover:bg-grade-soft hover:text-grade',
        destructive: 'bg-grade text-white hover:brightness-90 dark:text-desk',
      },
      size: {
        sm: 'h-9 px-3.5',
        md: 'h-11 px-5',
        lg: 'h-12 w-full px-6 text-base font-semibold [&_svg]:size-5',
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
