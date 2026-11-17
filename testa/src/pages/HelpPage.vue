<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Info, Printer } from 'lucide-vue-next'
import PublicHeader from '@/components/app/PublicHeader.vue'
import Button from '@/components/ui/Button.vue'
import { useAuth } from '@/composables/useAuth'
import { CHAPTERS } from '@/data/manual'

/** The operation manual: one chapter per role, each section a short silent clip with numbered steps. Open to everyone. */
const { isStudent } = useAuth()
const route = useRoute()
const chapter = computed(
  () => CHAPTERS.find((c) => c.id === route.params.chapter) ?? CHAPTERS.find((c) => c.id === (isStudent.value ? 'candidate' : 'start'))!,
)
const videoSrc = (name: string) => `${import.meta.env.BASE_URL}manual/${name}.mp4#t=0.1`

const content = ref<HTMLElement>()
watch(chapter, () => content.value?.scrollTo({ top: 0 }))
const print = () => window.print()
</script>

<template>
  <div class="flex h-dvh flex-col">
    <PublicHeader title="操作手冊" />

    <main ref="content" class="thin-scroll min-h-0 flex-1 overflow-y-auto">
      <div class="mx-auto flex max-w-[1080px] gap-8 py-4 md:px-6 md:py-8">
        <!-- Chapters: a side list on wide screens, a scrolling row on phones -->
        <nav aria-label="章節" class="no-print shrink-0 max-md:hidden md:w-44">
          <ul class="sticky top-0 space-y-0.5">
            <li v-for="c in CHAPTERS" :key="c.id">
              <RouterLink
                :to="`/help/${c.id}`"
                class="block rounded-control px-3 py-2 text-[15px] transition-colors"
                :class="c.id === chapter.id ? 'bg-brand-soft font-medium text-brand' : 'text-ink-2 hover:bg-ink/[0.06] hover:text-ink'"
                :aria-current="c.id === chapter.id ? 'page' : undefined"
              >
                {{ c.title }}
              </RouterLink>
            </li>
            <li class="mt-3 border-t border-line pt-3">
              <RouterLink to="/about" class="flex items-center gap-2 rounded-control px-3 py-2 text-[15px] text-ink-2 hover:bg-ink/[0.06] hover:text-ink">
                <Info class="size-4" />關於 Testa
              </RouterLink>
            </li>
          </ul>
        </nav>

        <article class="min-w-0 flex-1">
          <nav aria-label="章節" class="no-print thin-scroll -mt-1 mb-4 flex gap-1.5 overflow-x-auto px-4 pb-1 md:hidden">
            <RouterLink
              v-for="c in CHAPTERS"
              :key="c.id"
              :to="`/help/${c.id}`"
              class="shrink-0 rounded-full px-3.5 py-1.5 text-sm"
              :class="c.id === chapter.id ? 'bg-brand font-medium text-brand-fg' : 'bg-sunken text-ink-2'"
            >
              {{ c.title }}
            </RouterLink>
          </nav>

          <div class="mb-5 flex flex-wrap items-end gap-3 px-4 md:px-0">
            <div class="min-w-0 flex-1">
              <h1 class="text-[26px] font-bold sm:text-[30px]">{{ chapter.title }}</h1>
              <p class="mt-1 text-[15px] text-ink-2">給{{ chapter.who }}</p>
            </div>
            <Button v-if="chapter.id === 'candidate'" variant="outline" size="md" class="no-print" @click="print">
              <Printer />列印
            </Button>
          </div>

          <div class="space-y-3">
            <section v-for="s in chapter.sections" :key="s.title" class="card px-5 py-5 sm:px-6">
              <h2 class="text-[17px] font-semibold">{{ s.title }}</h2>
              <video
                v-if="s.video"
                :key="s.video"
                :src="videoSrc(s.video)"
                class="no-print mt-3 aspect-[16/10] w-full rounded-control bg-sunken"
                controls
                muted
                playsinline
                preload="metadata"
                :aria-label="`${s.title}的示範影片`"
              />
              <ol v-if="s.steps" class="mt-3 space-y-2">
                <li v-for="(step, i) in s.steps" :key="i" class="flex gap-3 text-[15px] leading-relaxed">
                  <span
                    class="mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full bg-brand-soft text-[13px] font-semibold text-brand tabular-nums"
                  >
                    {{ i + 1 }}
                  </span>
                  <span>{{ step }}</span>
                </li>
              </ol>
              <ul v-if="s.notes" class="mt-3 space-y-1.5 text-[15px] leading-relaxed text-ink-2">
                <li v-for="note in s.notes" :key="note" class="border-l-2 border-line pl-3">{{ note }}</li>
              </ul>
            </section>
          </div>
        </article>
      </div>
    </main>
  </div>
</template>
