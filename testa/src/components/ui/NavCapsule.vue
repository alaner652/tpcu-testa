<script setup lang="ts">
/**
 * The top bar of every full page: a glass capsule floating at the top. Classes given to it (gap, padding) go on the
 * capsule itself. `frame` is the width class of the page's content column (empty spans the window). The `below` slot
 * sits under the capsule.
 */
defineOptions({ inheritAttrs: false })
withDefaults(defineProps<{ frame?: string }>(), { frame: 'max-w-[1080px]' })
</script>

<template>
  <header class="relative z-20 mx-auto w-full shrink-0 px-3 pt-3 md:px-6" :class="frame">
    <div v-bind="$attrs" class="nav-glass relative flex h-14 items-center rounded-full">
      <slot />
    </div>
    <slot name="below" />
  </header>
</template>
