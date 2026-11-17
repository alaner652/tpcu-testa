<script setup lang="ts">
import { watch } from 'vue'
import { TooltipProvider } from 'reka-ui'
import { Toaster } from 'vue-sonner'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import { useAuth } from '@/composables/useAuth'
import { useBanks } from '@/composables/useBanks'
import { useExams } from '@/composables/useExams'
import { useGrading } from '@/composables/useGrading'
import { useTheme } from '@/composables/useTheme'

const { dark } = useTheme()
// Reload banks on sign-in, sign-out and switching user
watch(
  () => useAuth().user.value?.email,
  () => {
    useBanks().load()
    useExams().load()
    useGrading().load()
  },
  { immediate: true },
)
</script>

<template>
  <TooltipProvider :delay-duration="350">
    <RouterView />
    <ConfirmDialog />
    <!-- Bottom right on computers; on phones full width above the bottom navigation -->
    <Toaster
      position="bottom-right"
      :theme="dark ? 'dark' : 'light'"
      :duration="4000"
      :offset="{ bottom: '1.5rem', right: '1.5rem' }"
      :mobile-offset="{ bottom: '5rem' }"
    />
  </TooltipProvider>
</template>
