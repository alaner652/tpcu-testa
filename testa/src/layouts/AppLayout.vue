<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ClipboardPenLine, FileText, House, LibraryBig, Settings } from 'lucide-vue-next'
import Logo from '@/components/app/Logo.vue'
import ShortcutHelp from '@/components/app/ShortcutHelp.vue'
import UserMenu from '@/components/app/UserMenu.vue'
import { useAuth, type UnitRole } from '@/composables/useAuth'
import { useGrading } from '@/composables/useGrading'
import NavCapsule from '@/components/ui/NavCapsule.vue'

const { user, isAdmin, isStudent } = useAuth()
const { pending } = useGrading()
const route = useRoute()
/** Routes are flat (/exams/5 is not a child of /exams), so match on the path prefix */
const active = (link: { to: string; exact: boolean }) =>
  link.exact ? route.path === link.to : route.path === link.to || route.path.startsWith(link.to + '/')

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
  ...(can('GRADER') ? [{ to: '/grading', label: '閱卷', icon: ClipboardPenLine, exact: false, badge: pending.value }] : []),
  ...(isAdmin.value ? [{ to: '/admin', label: '管理', icon: Settings, exact: false, badge: 0 }] : []),
])
</script>

<template>
  <div class="flex h-dvh flex-col">
    <!-- Nav -->
    <NavCapsule class="gap-6 pr-2 pl-5">
      <RouterLink to="/" class="rounded-control" aria-label="Testa 首頁"><Logo /></RouterLink>
      <nav class="flex items-center gap-1 max-md:hidden" aria-label="主要導覽">
        <RouterLink v-for="link in links" :key="link.to" v-slot="{ href, navigate }" :to="link.to" custom>
          <a :href="href" class="pill-tab h-9 px-3.5" :aria-current="active(link) ? 'page' : undefined" @click="navigate">
            <component :is="link.icon" class="size-4" aria-hidden="true" />{{ link.label }}
            <span v-if="link.badge" class="rounded-full bg-grade px-1.5 text-xs leading-5 font-semibold text-white dark:text-desk">
              {{ link.badge }}
            </span>
          </a>
        </RouterLink>
      </nav>
      <div class="ml-auto"><UserMenu /></div>
    </NavCapsule>

    <main class="thin-scroll min-h-0 flex-1 overflow-y-auto">
      <div class="mx-auto max-w-[1080px] py-4 md:px-6 md:py-8">
        <RouterView />
      </div>
    </main>

    <!-- Phone: bottom tabs. A page with its own tabs (the admin page) puts them here instead. -->
    <div id="page-bar" class="shrink-0 md:hidden" />
    <nav
      v-if="links.length > 1 && !route.meta.admin"
      class="flex shrink-0 border-t border-line bg-paper pb-[env(safe-area-inset-bottom)] md:hidden"
      aria-label="主要導覽"
    >
      <RouterLink v-for="link in links" :key="link.to" v-slot="{ href, navigate }" :to="link.to" custom>
        <a
          :href="href"
          class="relative flex h-14 flex-1 flex-col items-center justify-center gap-0.5 text-[11px]"
          :class="active(link) ? 'font-medium text-brand' : 'text-ink-2'"
          :aria-current="active(link) ? 'page' : undefined"
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
    <ShortcutHelp v-if="!isStudent" />
  </div>
</template>
