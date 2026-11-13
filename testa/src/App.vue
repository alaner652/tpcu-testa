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
// 登入、登出、換人登入時重新載入題庫
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
    <Toaster position="bottom-center" :theme="dark ? 'dark' : 'light'" :duration="4000" :offset="{ bottom: '5rem' }" :mobile-offset="{ bottom: '5rem' }" />
  </TooltipProvider>
</template>
