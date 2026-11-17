<script setup lang="ts">
import { DropdownMenuRoot, DropdownMenuSeparator, DropdownMenuTrigger } from 'reka-ui'
import { Keyboard, KeyRound, LogOut, Moon, Sun, Users } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import MenuContent from '@/components/ui/MenuContent.vue'
import MenuItem from '@/components/ui/MenuItem.vue'
import { ROLE_LABEL, useAuth } from '@/composables/useAuth'
import { useConfirm } from '@/composables/useConfirm'
import { helpOpen } from '@/composables/useShortcuts'
import { useTheme } from '@/composables/useTheme'

const { user, logout } = useAuth()
const { confirm } = useConfirm()
const { dark, toggle } = useTheme()
const router = useRouter()

async function signOut() {
  const ok = await confirm({ title: '確定要登出嗎？', description: '題庫的修改都已自動儲存。', confirmLabel: '登出' })
  if (!ok) return
  await logout()
  await router.push({ name: 'login' })
  toast.success('已登出')
}
</script>

<template>
  <DropdownMenuRoot v-if="user">
    <DropdownMenuTrigger
      class="flex h-10 items-center gap-2 rounded-full pr-3 pl-1 transition-colors hover:bg-ink/[0.06] data-[state=open]:bg-ink/[0.06] max-sm:pr-1"
      aria-label="帳號選單"
    >
      <span class="flex size-8 items-center justify-center rounded-full bg-brand text-sm font-semibold text-brand-fg">
        {{ user.name.slice(0, 1) }}
      </span>
      <span class="text-sm font-medium max-sm:hidden">{{ user.name }}</span>
    </DropdownMenuTrigger>
    <MenuContent align="end" class="w-60">
      <div class="px-2 py-2">
        <p class="text-[15px] font-medium">{{ user.name }}</p>
        <p class="text-[13px] text-ink-2">{{ user.unit }}，{{ ROLE_LABEL[user.role] }}</p>
        <p class="truncate text-[13px] text-ink-3">{{ user.ticket ? `准考證號 ${user.ticket}` : user.email }}</p>
      </div>
      <DropdownMenuSeparator class="my-1 h-px bg-line" />
      <MenuItem class="h-10" @select="toggle">
        <component :is="dark ? Sun : Moon" />
        {{ dark ? '切換為淺色' : '切換為深色' }}
      </MenuItem>
      <MenuItem v-if="user.units?.some((u) => u.roles.includes('OWNER'))" class="h-10" @select="router.push({ name: 'members' })">
        <Users />
        單位成員
      </MenuItem>
      <MenuItem v-if="user.role !== 'student'" class="h-10" @select="router.push({ name: 'password' })">
        <KeyRound />
        變更密碼
      </MenuItem>
      <!-- Phones have no keyboard -->
      <MenuItem v-if="user.role !== 'student'" class="h-10 max-md:hidden" @select="helpOpen = true">
        <Keyboard />
        鍵盤快捷鍵
        <kbd class="ml-auto font-sans text-xs text-ink-3">?</kbd>
      </MenuItem>
      <MenuItem class="h-10" @select="signOut">
        <LogOut />
        登出
      </MenuItem>
    </MenuContent>
  </DropdownMenuRoot>
</template>
