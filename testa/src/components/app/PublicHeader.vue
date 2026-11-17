<script setup lang="ts">
import { computed } from 'vue'
import { ArrowLeft } from 'lucide-vue-next'
import Logo from '@/components/app/Logo.vue'
import { useAuth } from '@/composables/useAuth'

/** Top bar of the pages anyone can open (manual, about): the logo, the page name and the way back into the app. */
defineProps<{ title: string }>()
const { user, isStudent } = useAuth()
const back = computed(() => (!user.value ? { label: '回到登入', to: '/login' } : { label: '回到 Testa', to: isStudent.value ? '/student' : '/' }))
</script>

<template>
  <header class="relative z-20 shrink-0 bg-paper shadow-card">
    <div class="mx-auto flex h-14 max-w-[1080px] items-center gap-3 px-4 md:px-6">
      <RouterLink to="/about" class="rounded-control" aria-label="關於 Testa"><Logo /></RouterLink>
      <span class="text-[15px] text-ink-2">{{ title }}</span>
      <RouterLink
        :to="back.to"
        class="ml-auto inline-flex h-9 items-center gap-1.5 rounded-full px-3.5 text-sm font-medium text-ink-2 hover:bg-ink/[0.06] hover:text-ink"
      >
        <ArrowLeft class="size-4" />{{ back.label }}
      </RouterLink>
    </div>
  </header>
</template>
