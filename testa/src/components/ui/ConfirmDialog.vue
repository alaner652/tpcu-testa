<script setup lang="ts">
import {
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogOverlay,
  AlertDialogPortal,
  AlertDialogRoot,
  AlertDialogTitle,
} from 'reka-ui'
import { useConfirm } from '@/composables/useConfirm'

const { state, settle } = useConfirm()
</script>

<template>
  <!-- Only the buttons and Esc decide the result; update:open is not used, so OK is never taken as cancel first -->
  <AlertDialogRoot :open="state.open">
    <AlertDialogPortal>
      <AlertDialogOverlay class="confirm-overlay fixed inset-0 z-[70] bg-black/40" />
      <AlertDialogContent
        class="confirm-content fixed top-1/2 left-1/2 z-[70] w-[min(calc(100vw-2rem),26rem)] -translate-x-1/2 -translate-y-1/2 rounded-card bg-paper p-6 shadow-lift outline-none"
        @escape-key-down="settle(false)"
      >
        <AlertDialogTitle class="text-lg font-bold">{{ state.options.title }}</AlertDialogTitle>
        <AlertDialogDescription class="mt-2 text-[15px] leading-relaxed text-ink-2">
          {{ state.options.description }}
        </AlertDialogDescription>
        <div class="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <AlertDialogCancel
            class="h-10 rounded-full border border-line-strong px-5 text-sm font-medium transition-colors hover:bg-sunken max-sm:h-11"
            @click="settle(false)"
          >
            {{ state.options.cancelLabel ?? '取消' }}
          </AlertDialogCancel>
          <AlertDialogAction
            class="h-10 rounded-full px-5 text-sm font-semibold transition-colors max-sm:h-11"
            :class="
              state.options.tone === 'danger'
                ? 'bg-grade text-white hover:brightness-90 dark:text-desk'
                : 'bg-brand text-brand-fg hover:bg-brand-strong'
            "
            @click="settle(true)"
          >
            {{ state.options.confirmLabel ?? '確定' }}
          </AlertDialogAction>
        </div>
      </AlertDialogContent>
    </AlertDialogPortal>
  </AlertDialogRoot>
</template>

<style>
.confirm-overlay[data-state='open'] {
  animation: confirm-fade 140ms ease-out;
}
.confirm-content[data-state='open'] {
  animation: confirm-pop 160ms cubic-bezier(0.2, 0.8, 0.2, 1);
}
@keyframes confirm-fade {
  from {
    opacity: 0;
  }
}
@keyframes confirm-pop {
  from {
    opacity: 0;
    transform: translate(-50%, -48%) scale(0.97);
  }
}
</style>
