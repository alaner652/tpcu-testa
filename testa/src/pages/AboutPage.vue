<script setup lang="ts">
import { computed } from 'vue'
import {
  ArrowRight,
  BookOpen,
  CalendarClock,
  ChartColumn,
  ClipboardPenLine,
  Code,
  DatabaseBackup,
  ExternalLink,
  LibraryBig,
  LockKeyhole,
  MonitorCheck,
  Scale,
  ServerCog,
  ShieldCheck,
  Wrench,
} from 'lucide-vue-next'
import { version } from '../../package.json'
import PublicHeader from '@/components/app/PublicHeader.vue'
import { useAuth } from '@/composables/useAuth'

/** About Testa: what it is, how an exam runs (each step links to its manual chapter), how it is built, and who made it. Open to everyone. */
const { user, isStudent } = useAuth()

/** Change these to credit someone else or point at another repository. */
const AUTHOR = { name: 'alaner652', role: '設計、開發與維護', url: 'https://github.com/alaner652' }
const SOURCE = 'https://github.com/alaner652/tpcu-testa'

const start = computed(() => (!user.value ? { label: '登入', to: '/login' } : { label: '回到 Testa', to: isStudent.value ? '/student' : '/' }))

const FACTS = [
  { value: '4', unit: '種題型', note: '單選、多選、填空、申論' },
  { value: '300', unit: '人同時作答', note: '用正式環境的映像檔實測' },
  { value: '1', unit: '台伺服器', note: '放在校內，用 Docker 部署' },
  { value: '0', unit: '個外部服務', note: '考試當天不需要網際網路' },
]

const FLOW = [
  { icon: LibraryBig, title: '出題', who: '出題老師', text: '寫題庫，用考生看到的畫面試考一次。', chapter: 'author' },
  { icon: CalendarClock, title: '安排考試', who: '承辦人', text: '選題庫、匯入名單、列印登入單，然後發布。', chapter: 'officer' },
  { icon: MonitorCheck, title: '作答', who: '考生', text: '用准考證號和登入碼登入，時間由伺服器控制。', chapter: 'candidate' },
  { icon: ClipboardPenLine, title: '閱卷', who: '閱卷老師', text: '選擇與填空自動批改，申論題匿名評分。', chapter: 'grader' },
  { icon: ChartColumn, title: '公布成績', who: '承辦人', text: '確認分數後公布，匯出 Excel 放榜。', chapter: 'officer' },
]

const PRINCIPLES = [
  { icon: ServerCog, title: '以伺服器為準', text: '計時、交卷、批改都在伺服器上進行。考生的電腦當機，換一台登入就能接著寫。' },
  { icon: ShieldCheck, title: '答案不外流', text: '送給考生的試卷不含答案、解析與評分規準，每次改版都有測試把關。' },
  { icon: LockKeyhole, title: '權限最小', text: '要在單位裡有對應角色才看得到考試；系統管理員要經負責人授權才能限時檢視。' },
  { icon: Wrench, title: '好維運', text: '每天自動備份，在管理頁就能還原；可以調整的設定都在畫面上，不用改伺服器上的檔案。' },
]

const STACK = [
  { group: '前端', items: ['Vue 3', 'TypeScript', 'Vite', 'Tailwind CSS', 'reka-ui', 'Lucide', 'KaTeX'] },
  { group: '後端', items: ['Spring Boot', 'Java 21', 'PostgreSQL', 'Flyway', 'Spring Session'] },
  { group: '部署', items: ['Docker Compose', 'Nginx'] },
]

const ARCH = [
  { name: '瀏覽器', note: '考生與教職員' },
  { name: 'Nginx', note: '網頁檔案、流量限制' },
  { name: 'Spring Boot', note: '計時、批改、權限' },
  { name: 'PostgreSQL', note: '題庫、作答、登入狀態' },
]
</script>

<template>
  <div class="flex h-dvh flex-col">
    <PublicHeader title="關於" />

    <main class="thin-scroll min-h-0 flex-1 overflow-y-auto">
      <!-- What Testa is -->
      <section class="border-b border-line bg-gradient-to-b from-brand-soft/70 to-desk">
        <div class="mx-auto grid max-w-[1080px] items-center gap-10 px-4 py-12 md:grid-cols-[1fr_auto] md:px-6 md:py-16">
          <div>
            <p class="text-sm font-medium tracking-wide text-brand">開源・自架・校內一台伺服器</p>
            <h1 class="mt-3 text-[32px] leading-tight font-bold sm:text-[40px]">學校自己的線上考試系統</h1>
            <p class="mt-4 max-w-[36rem] text-[17px] leading-relaxed text-ink-2">
              教師出題，承辦人安排考試，考生在電腦教室作答，申論題匿名閱卷，最後公布成績。從出題到放榜都在同一個地方完成。
            </p>
            <div class="mt-7 flex flex-wrap gap-2">
              <RouterLink
                to="/help"
                class="inline-flex h-11 items-center gap-1.5 rounded-full bg-brand px-5 text-sm font-medium text-brand-fg hover:bg-brand-strong"
              >
                <BookOpen class="size-4" />看操作手冊
              </RouterLink>
              <RouterLink
                :to="start.to"
                class="inline-flex h-11 items-center gap-1.5 rounded-full border border-line-strong bg-paper px-5 text-sm font-medium hover:border-brand hover:text-brand"
              >
                {{ start.label }}<ArrowRight class="size-4" />
              </RouterLink>
            </div>
          </div>
          <!-- An answer sheet, the same bubbles as the logo -->
          <svg viewBox="0 0 220 180" class="w-56 text-brand max-md:hidden" aria-hidden="true">
            <rect x="1" y="1" width="218" height="178" rx="14" class="fill-paper stroke-line" stroke-width="1.5" />
            <g v-for="(row, r) in [1, 3, 0, 2, 1]" :key="r">
              <text :x="22" :y="38 + r * 30" class="fill-ink-3 text-[12px] tabular-nums">{{ r + 1 }}</text>
              <circle
                v-for="c in 4"
                :key="c"
                :cx="34 + c * 34"
                :cy="34 + r * 30"
                :r="c - 1 === row ? 10 : 8.5"
                :fill="c - 1 === row ? 'currentColor' : 'none'"
                stroke="currentColor"
                :stroke-width="c - 1 === row ? 0 : 1.75"
                :opacity="c - 1 === row ? 1 : 0.45"
              />
            </g>
          </svg>
        </div>
      </section>

      <div class="mx-auto max-w-[1080px] space-y-14 py-10 md:px-6 md:py-14">
        <!-- Numbers -->
        <section aria-label="數字" class="grid grid-cols-2 gap-3 px-4 md:grid-cols-4 md:px-0">
          <div v-for="f in FACTS" :key="f.unit" class="rounded-card bg-paper px-5 py-5 shadow-card">
            <p class="flex flex-wrap items-baseline gap-x-1.5 gap-y-1">
              <span class="text-[34px] leading-none font-bold text-brand tabular-nums">{{ f.value }}</span>
              <span class="text-[15px] font-medium whitespace-nowrap">{{ f.unit }}</span>
            </p>
            <p class="mt-2 text-[13px] text-ink-2">{{ f.note }}</p>
          </div>
        </section>

        <!-- How an exam runs; each step opens its chapter of the manual -->
        <section>
          <div class="px-4 md:px-0">
            <h2 class="text-[22px] font-bold">一場考試怎麼進行</h2>
            <p class="mt-1 text-[15px] text-ink-2">五個步驟，分別由不同角色負責。點一個步驟看詳細操作。</p>
          </div>
          <ol class="relative mt-6 grid gap-3 px-4 md:grid-cols-5 md:px-0">
            <!-- The line joining the steps -->
            <span class="absolute top-7 right-[10%] left-[10%] h-0.5 bg-brand/25 max-md:hidden" aria-hidden="true" />
            <li v-for="(step, i) in FLOW" :key="step.title" class="relative">
              <RouterLink
                :to="`/help/${step.chapter}`"
                class="group flex h-full gap-4 rounded-card bg-paper p-4 shadow-card transition-shadow hover:shadow-lift md:flex-col md:items-center md:bg-transparent md:p-3 md:text-center md:shadow-none md:hover:bg-paper"
              >
                <span class="relative flex size-11 shrink-0 items-center justify-center rounded-full bg-brand text-brand-fg ring-4 ring-desk">
                  <component :is="step.icon" class="size-5" />
                  <span
                    class="absolute -top-1 -right-1 flex size-5 items-center justify-center rounded-full bg-paper text-[11px] font-bold text-brand shadow-card tabular-nums"
                  >
                    {{ i + 1 }}
                  </span>
                </span>
                <span class="min-w-0">
                  <span class="block text-[17px] font-semibold">{{ step.title }}</span>
                  <span class="mt-0.5 block text-[13px] font-medium text-brand">{{ step.who }}</span>
                  <span class="mt-1.5 block text-sm leading-relaxed text-ink-2">{{ step.text }}</span>
                  <span class="mt-2 inline-flex items-center gap-1 text-[13px] font-medium text-ink-3 group-hover:text-brand">
                    看說明<ArrowRight class="size-3.5" />
                  </span>
                </span>
              </RouterLink>
            </li>
          </ol>
        </section>

        <!-- Principles -->
        <section>
          <div class="px-4 md:px-0">
            <h2 class="text-[22px] font-bold">設計原則</h2>
            <p class="mt-1 text-[15px] text-ink-2">考試要公平、資料要安全，而且要讓不是工程師的人也能維運。</p>
          </div>
          <div class="mt-6 grid gap-3 sm:grid-cols-2">
            <article v-for="p in PRINCIPLES" :key="p.title" class="card flex gap-4 px-5 py-5 sm:px-6">
              <span class="flex size-10 shrink-0 items-center justify-center rounded-control bg-brand-soft text-brand">
                <component :is="p.icon" class="size-5" />
              </span>
              <div>
                <h3 class="text-[17px] font-semibold">{{ p.title }}</h3>
                <p class="mt-1 text-[15px] leading-relaxed text-ink-2">{{ p.text }}</p>
              </div>
            </article>
          </div>
        </section>

        <!-- Architecture -->
        <section>
          <div class="px-4 md:px-0">
            <h2 class="text-[22px] font-bold">系統架構</h2>
            <p class="mt-1 text-[15px] text-ink-2">全部在校內一台虛擬機上，用 Docker Compose 一次啟動。</p>
          </div>
          <div class="card mt-6 px-5 py-6 sm:px-6">
            <div class="flex items-stretch gap-2 max-md:flex-col">
              <template v-for="(box, i) in ARCH" :key="box.name">
                <div class="flex flex-1 flex-col justify-center rounded-control border border-line px-4 py-3" :class="i === 0 ? 'bg-sunken' : 'bg-paper'">
                  <p class="text-[15px] font-semibold">{{ box.name }}</p>
                  <p class="mt-0.5 text-[13px] text-ink-2">{{ box.note }}</p>
                </div>
                <ArrowRight v-if="i < ARCH.length - 1" class="size-5 shrink-0 self-center text-ink-3 max-md:rotate-90" aria-hidden="true" />
              </template>
            </div>
            <p class="mt-4 flex items-center gap-2 text-sm text-ink-2">
              <DatabaseBackup class="size-4 shrink-0 text-brand" />每天自動備份資料庫，保留天數可以在系統設定調整。
            </p>
            <div class="mt-6 grid gap-4 border-t border-line pt-5 sm:grid-cols-3">
              <div v-for="s in STACK" :key="s.group">
                <p class="text-[13px] font-medium text-ink-3">{{ s.group }}</p>
                <ul class="mt-2 flex flex-wrap gap-1.5">
                  <li v-for="item in s.items" :key="item" class="rounded-full bg-sunken px-2.5 py-1 text-[13px]">{{ item }}</li>
                </ul>
              </div>
            </div>
          </div>
        </section>

        <!-- Credits -->
        <section>
          <div class="px-4 md:px-0">
            <h2 class="text-[22px] font-bold">製作</h2>
          </div>
          <div class="mt-6 grid gap-3 md:grid-cols-[3fr_2fr]">
            <article class="card flex gap-4 px-5 py-6 sm:px-6">
              <span class="flex size-14 shrink-0 items-center justify-center rounded-full bg-brand text-[22px] font-bold text-brand-fg uppercase">
                {{ AUTHOR.name.slice(0, 1) }}
              </span>
              <div class="min-w-0">
                <p class="text-[13px] font-medium text-brand">{{ AUTHOR.role }}</p>
                <p class="mt-0.5 text-[20px] font-bold">{{ AUTHOR.name }}</p>
                <p class="mt-2 text-[15px] leading-relaxed text-ink-2">
                  從介面設計、前後端開發、自動測試到部署，由一人獨立完成。有問題或建議，歡迎在 GitHub 上提出。
                </p>
                <div class="mt-4 flex flex-wrap gap-2">
                  <a
                    :href="AUTHOR.url"
                    target="_blank"
                    rel="noopener"
                    class="inline-flex h-9 items-center gap-1.5 rounded-full border border-line-strong px-3.5 text-sm font-medium hover:border-brand hover:text-brand"
                  >
                    <ExternalLink class="size-4" />GitHub
                  </a>
                  <a
                    :href="SOURCE"
                    target="_blank"
                    rel="noopener"
                    class="inline-flex h-9 items-center gap-1.5 rounded-full border border-line-strong px-3.5 text-sm font-medium hover:border-brand hover:text-brand"
                  >
                    <Code class="size-4" />原始碼
                  </a>
                </div>
              </div>
            </article>
            <article class="card px-5 py-6 sm:px-6">
              <h3 class="flex items-center gap-2 text-[17px] font-semibold"><Scale class="size-4 text-brand" />授權</h3>
              <p class="mt-2 text-[15px] leading-relaxed text-ink-2">
                Testa 以 GNU AGPL v3.0 釋出，是自由軟體：可以使用、修改與散布；把修改過的版本架成服務給別人用時，也要提供原始碼。
              </p>
              <p class="mt-3 text-sm text-ink-3">感謝上方列出的開源專案，以及使用 Testa 的每一位老師與考生。</p>
            </article>
          </div>
        </section>

        <p class="px-4 text-center text-[13px] text-ink-3 md:px-0">Testa v{{ version }}・AGPL-3.0</p>
      </div>
    </main>
  </div>
</template>
