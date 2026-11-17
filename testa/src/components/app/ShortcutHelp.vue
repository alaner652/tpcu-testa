<script setup lang="ts">
import { computed } from 'vue'
import { DialogClose, DialogContent, DialogDescription, DialogOverlay, DialogPortal, DialogRoot, DialogTitle } from 'reka-ui'
import { X } from 'lucide-vue-next'
import { helpOpen, shortcutHelp, useShortcuts } from '@/composables/useShortcuts'

/** The "?" list: shortcuts the current page offers. Mounted once per staff layout, never on the candidate side. */
useShortcuts('一般', () => [{ combo: '?', label: '顯示這份清單', run: () => (helpOpen.value = true) }])
const groups = computed(() => (helpOpen.value ? shortcutHelp() : []))
const hasPageShortcuts = computed(() => groups.value.some((group) => group.title !== '一般'))
</script>

<template>
  <DialogRoot v-model:open="helpOpen">
    <DialogPortal>
      <DialogOverlay class="fixed inset-0 z-[60] bg-black/35" />
      <DialogContent
        @open-auto-focus.prevent
        class="thin-scroll fixed top-1/2 left-1/2 z-[60] max-h-[85dvh] w-[min(calc(100vw-2rem),28rem)] -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-card bg-paper p-6 shadow-lift outline-none"
      >
        <div class="flex items-start justify-between gap-4">
          <DialogTitle class="text-lg font-bold">鍵盤快捷鍵</DialogTitle>
          <DialogClose class="-mt-1 -mr-2 flex size-9 items-center justify-center rounded-full text-ink-2 hover:bg-ink/[0.06]" aria-label="關閉">
            <X class="size-4" />
          </DialogClose>
        </div>
        <DialogDescription class="mt-1 text-sm text-ink-2">每個動作也都有按鈕可以按，快捷鍵只是讓常用的動作更快。</DialogDescription>
        <p v-if="!hasPageShortcuts" class="mt-5 rounded-control bg-sunken px-4 py-3 text-sm text-ink-2">這個頁面沒有其他快捷鍵。</p>
        <section v-for="g in groups" :key="g.title" class="mt-5">
          <h3 class="text-sm font-semibold text-ink-2">{{ g.title }}</h3>
          <dl class="mt-1.5 divide-y divide-line">
            <div v-for="item in g.items" :key="item.label" class="flex items-center justify-between gap-4 py-2 text-[15px]">
              <dt>{{ item.label }}</dt>
              <dd><kbd class="rounded-md bg-sunken px-2 py-0.5 font-sans text-sm whitespace-nowrap text-ink-2">{{ item.keys }}</kbd></dd>
            </div>
          </dl>
        </section>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
