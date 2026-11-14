<script setup lang="ts">
import { computed } from 'vue'
import { ClipboardCheck, FileText, House, LibraryBig, Settings } from 'lucide-vue-next'
import Logo from '@/components/app/Logo.vue'
import UserMenu from '@/components/app/UserMenu.vue'
import { useAuth, type UnitRole } from '@/composables/useAuth'
import { useGrading } from '@/composables/useGrading'

const { user, isAdmin, isStudent } = useAuth()
const { pending } = useGrading()

const links = computed(() =>
  isStudent.value ? [{ to: '/student', label: '我的考試', icon: FileText, exact: false, badge: 0 }] : staffLinks.value,
)
/** Menu items depend on unit roles: each role sees only the two or three it uses. The backend checks real permissions. */
const can = (...roles: UnitRole[]) => !!user.value?.units?.some((u) => u.roles.some((r) => roles.includes(r)))
const staffLinks = computed(() => [
  // For administrators who only manage accounts and units, home is the admin page, so do not list both
  ...(isAdmin.value && !user.value?.units?.length ? [] : [{ to: '/', label: '首頁', icon: House, exact: true, badge: 0 }]),
  ...(can('AUTHOR', 'OWNER') ? [{ to: '/banks', label: '題庫', icon: LibraryBig, exact: false, badge: 0 }] : []),
  ...(can('OWNER', 'PROCTOR') ? [{ to: '/exams', label: '考試', icon: FileText, exact: false, badge: 0 }] : []),
  ...(can('GRADER') ? [{ to: '/grading', label: '閱卷', icon: ClipboardCheck, exact: false, badge: pending.value }] : []),
  ...(isAdmin.value ? [{ to: '/admin', label: '管理', icon: Settings, exact: false, badge: 0 }] : []),
])
</script>

<template>
  <div class="flex h-dvh flex-col">
    <!-- Nav -->
    <header class="relative z-20 shrink-0 bg-paper shadow-card">
      <div class="mx-auto flex h-14 max-w-[1080px] items-center gap-6 px-4 md:px-6">
        <RouterLink to="/" class="rounded-control" aria-label="Testa 首頁"><Logo /></RouterLink>
        <nav class="flex h-full items-stretch gap-1 max-md:hidden" aria-label="主要導覽">
          <RouterLink
            v-for="link in links"
            :key="link.to"
            v-slot="{ isActive, isExactActive, href, navigate }"
            :to="link.to"
            custom
          >
            <a
              :href="href"
              class="relative flex items-center gap-1.5 px-3 text-[15px] transition-colors"
              :class="(link.exact ? isExactActive : isActive) ? 'font-medium text-brand' : 'text-ink-2 hover:text-ink'"
              :aria-current="(link.exact ? isExactActive : isActive) ? 'page' : undefined"
              @click="navigate"
            >
              <component :is="link.icon" class="size-4" aria-hidden="true" />{{ link.label }}
              <span v-if="link.badge" class="rounded-full bg-grade px-1.5 text-xs leading-5 font-semibold text-white dark:text-desk">
                {{ link.badge }}
              </span>
              <span
                v-if="link.exact ? isExactActive : isActive"
                class="absolute inset-x-3 bottom-0 h-[3px] rounded-t-full bg-brand"
              />
            </a>
          </RouterLink>
        </nav>
        <div class="ml-auto"><UserMenu /></div>
      </div>
    </header>

    <main class="thin-scroll min-h-0 flex-1 overflow-y-auto">
      <div class="mx-auto max-w-[1080px] py-4 md:px-6 md:py-8">
        <RouterView />
      </div>
    </main>

    <!-- Phone: bottom tabs -->
    <nav v-if="links.length > 1" class="flex shrink-0 border-t border-line bg-paper pb-[env(safe-area-inset-bottom)] md:hidden" aria-label="主要導覽">
      <RouterLink v-for="link in links" :key="link.to" v-slot="{ isActive, isExactActive, href, navigate }" :to="link.to" custom>
        <a
          :href="href"
          class="relative flex h-14 flex-1 flex-col items-center justify-center gap-0.5 text-[11px]"
          :class="(link.exact ? isExactActive : isActive) ? 'font-medium text-brand' : 'text-ink-2'"
          :aria-current="(link.exact ? isExactActive : isActive) ? 'page' : undefined"
          @click="navigate"
        >
          <component :is="link.icon" class="size-5" />
          {{ link.label }}
          <span
            v-if="link.badge"
            class="absolute top-1.5 left-1/2 ml-2 rounded-full bg-grade px-1.5 text-[10px] leading-4 font-semibold text-white dark:text-desk"
          >
            {{ link.badge }}
          </span>
        </a>
      </RouterLink>
    </nav>
  </div>
</template>
