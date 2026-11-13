<script setup lang="ts">
import { SwitchRoot, SwitchThumb } from 'reka-ui'

const model = defineModel<boolean>({ default: false })
defineProps<{ id?: string }>()
</script>

<template>
  <SwitchRoot
    :id="id"
    v-model="model"
    class="inline-flex h-5 w-9 shrink-0 items-center rounded-full border border-transparent bg-line-strong transition-colors data-[state=checked]:bg-brand"
  >
    <SwitchThumb
      class="block size-4 translate-x-0.5 rounded-full bg-paper transition-transform data-[state=checked]:translate-x-[18px] dark:data-[state=checked]:bg-brand-fg"
    />
  </SwitchRoot>
</template>
