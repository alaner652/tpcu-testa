<script setup lang="ts">
import { computed, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { BookOpen, ChevronDown, CircleHelp, ClipboardPenLine, FileText, GraduationCap, Info, LibraryBig, Printer, Settings } from 'lucide-vue-next'
import PublicHeader from '@/components/app/PublicHeader.vue'
import BottomBar from '@/components/ui/BottomBar.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import Button from '@/components/ui/Button.vue'
import { useAuth } from '@/composables/useAuth'
import { CHAPTERS } from '@/data/manual'

/** The operation manual: one chapter per role, written in Markdown (src/manual), each section a card. Open to everyone. */
const { isStudent } = useAuth()
const route = useRoute()
const chapter = computed(
  () => CHAPTERS.find((c) => c.id === route.params.chapter) ?? CHAPTERS.find((c) => c.id === (isStudent.value ? 'candidate' : 'start'))!,
)

const content = ref<HTMLElement>()
/** FAQ: one answer open at a time */
const open = ref<string | null>(null)
watch(chapter, () => {
  open.value = null
  content.value?.scrollTo({ top: 0 })
})
/** "In this chapter" links on wide screens, for chapters long enough to need them */
const toc = computed(() => (!chapter.value.accordion && chapter.value.sections.length >= 3 ? chapter.value.sections : []))
const jump = (id: string) => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
const print = () => window.print()

/** Phone: chapters in a bottom bar; the ones most readers need are cells, the rest and "about" sit under "更多" */
const ICONS: Record<string, Component> = {
  start: BookOpen,
  admin: Settings,
  author: LibraryBig,
  officer: FileText,
  grader: ClipboardPenLine,
  candidate: GraduationCap,
  faq: CircleHelp,
}
const PHONE_CHAPTERS = ['start', 'candidate', 'faq']
const phoneItems = [...CHAPTERS.map((c) => ({ value: c.id, label: c.title, icon: ICONS[c.id] ?? BookOpen })), { value: 'about', label: '關於 Testa', icon: Info }]
const router = useRouter()
const goChapter = (id: string) => router.push(id === 'about' ? '/about' : `/help/${id}`)
</script>

<template>
  <div class="flex h-dvh flex-col">
    <PublicHeader title="操作手冊" frame="max-w-[1080px] xl:max-w-[1240px]" />

    <main ref="content" class="thin-scroll min-h-0 flex-1 overflow-y-auto">
      <div class="mx-auto flex max-w-[1080px] gap-8 py-4 md:px-6 md:py-8 xl:max-w-[1240px]">
        <!-- Chapters: a side list on wide screens, the bottom bar on phones -->
        <nav aria-label="章節" class="no-print shrink-0 max-md:hidden md:w-44">
          <ul class="sticky top-0 space-y-0.5">
            <li v-for="c in CHAPTERS" :key="c.id">
              <RouterLink
                :to="`/help/${c.id}`"
                class="pill-tab w-full"
                :aria-current="c.id === chapter.id ? 'page' : undefined"
              >
                {{ c.title }}
              </RouterLink>
            </li>
            <li class="mt-3 border-t border-line pt-3">
              <RouterLink to="/about" class="pill-tab w-full">
                <Info class="size-4" />關於 Testa
              </RouterLink>
            </li>
          </ul>
        </nav>

        <article class="min-w-0 flex-1">
          <PageHeader :title="chapter.title" :hint="`給${chapter.who}`">
            <template #actions>
              <Button v-if="chapter.id === 'candidate'" variant="outline" size="md" class="no-print" @click="print"><Printer />列印</Button>
            </template>
          </PageHeader>

          <!-- FAQ: questions fold open one at a time -->
          <div v-if="chapter.accordion" class="card divide-y divide-line">
            <section v-for="s in chapter.sections" :key="s.id">
              <h2>
                <button
                  type="button"
                  class="flex w-full items-center gap-3 px-5 py-4 text-left text-[16px] font-semibold hover:bg-ink/[0.03] sm:px-6"
                  :aria-expanded="open === s.id"
                  :aria-controls="`${s.id}-answer`"
                  @click="open = open === s.id ? null : s.id"
                >
                  <span class="min-w-0 flex-1">{{ s.title }}</span>
                  <ChevronDown class="size-5 shrink-0 text-ink-3 transition-transform" :class="open === s.id && 'rotate-180 text-brand'" />
                </button>
              </h2>
              <div v-show="open === s.id" :id="`${s.id}-answer`" class="manual-prose px-5 pb-5 sm:px-6" v-html="s.html" />
            </section>
          </div>

          <div v-else class="space-y-4">
            <section v-for="s in chapter.sections" :id="s.id" :key="s.id" class="card scroll-mt-4 px-5 py-5 sm:px-6">
              <h2 class="text-[18px] font-semibold">{{ s.title }}</h2>
              <div class="manual-prose mt-3" v-html="s.html" />
            </section>
          </div>
        </article>

        <nav v-if="toc.length" aria-label="本章內容" class="no-print shrink-0 max-xl:hidden xl:w-44">
          <div class="sticky top-0">
            <p class="px-3 pb-1.5 text-[13px] font-semibold text-ink-3">本章內容</p>
            <ul class="space-y-0.5 border-l border-line">
              <li v-for="s in toc" :key="s.id">
                <a
                  :href="`#${s.id}`"
                  class="-ml-px block border-l-2 border-transparent px-3 py-1.5 text-sm text-ink-2 hover:border-brand hover:text-ink"
                  @click.prevent="jump(s.id)"
                >
                  {{ s.title }}
                </a>
              </li>
            </ul>
          </div>
        </nav>
      </div>
    </main>

    <BottomBar class="no-print shrink-0 md:hidden" label="章節" :items="phoneItems" :main="PHONE_CHAPTERS" :current="chapter.id" @select="goChapter" />
  </div>
</template>
