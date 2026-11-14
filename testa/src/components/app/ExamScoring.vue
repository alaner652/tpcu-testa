<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Download } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import Button from '@/components/ui/Button.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useExams, when, type Exam } from '@/composables/useExams'
import { api } from '@/lib/api'

/** 承辦人：指派評分者、看評分進度與差距過大的答案、看成績、公布與匯出。 */
const props = defineProps<{ exam: Exam; view: 'grading' | 'results' }>()

interface Grader {
  staffId: number
  name: string
  email: string
  assigned: boolean
  done: number
  total: number
}
interface ItemSummary {
  anonId: string
  questionId: string
  number: number
  points: number
  graded: number
  graders: number
  locked: boolean
  scores: number[]
  average: number | null
  flagged: boolean
}
interface Row {
  ticket: string
  name: string
  dept: string
  seat: number
  status: 'IDLE' | 'IN' | 'DONE'
  autoScore: number | null
  essayScore: number | null
  total: number | null
  pending: number
}
interface Results {
  fullMarks: number
  graders: number
  itemsTotal: number
  itemsLocked: number
  complete: boolean
  publishedAt: string | null
  rows: Row[]
  items: ItemSummary[]
}

const { confirm } = useConfirm()
const store = useExams()
const graders = ref<Grader[]>([])
const results = ref<Results | null>(null)
const error = ref('')
const fail = (e: unknown) => toast.error((e as Error).message)
const base = computed(() => `/exams/${props.exam.id}`)

async function load() {
  try {
    ;[graders.value, results.value] = await Promise.all([api<Grader[]>(`${base.value}/graders`), api<Results>(`${base.value}/results`)])
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message
  }
}
onMounted(load)

async function toggle(g: Grader) {
  const ids = graders.value.filter((x) => (x.staffId === g.staffId ? !x.assigned : x.assigned)).map((x) => x.staffId)
  try {
    graders.value = await api<Grader[]>(`${base.value}/graders`, { method: 'PUT', body: { staffIds: ids } })
    results.value = await api<Results>(`${base.value}/results`)
    toast.success(g.assigned ? `已取消指派 ${g.name}` : `已指派 ${g.name} 評分`)
  } catch (e) {
    fail(e)
  }
}

// 重開評分：必須填理由
const reopening = ref<ItemSummary | null>(null)
const reason = ref('')
async function reopen() {
  const item = reopening.value
  if (!item || !reason.value.trim()) return
  try {
    await api(`${base.value}/grading/reopen`, { method: 'POST', body: { anonId: item.anonId, questionId: item.questionId, reason: reason.value } })
    reopening.value = null
    reason.value = ''
    toast.success('已重開評分，請通知評分者重新評分')
    await load()
  } catch (e) {
    fail(e)
  }
}

async function publish() {
  const ok = await confirm({
    title: '公布成績？',
    description: '公布後考生用准考證號和登入碼登入，就能看到自己的分數。評分會關閉，不能再重開評分或更換評分者。這個動作無法復原。',
    confirmLabel: '公布成績',
    tone: 'danger',
  })
  if (!ok) return
  try {
    results.value = await api<Results>(`${base.value}/results/publish`, { method: 'POST' })
    await store.refresh(props.exam.id)
    toast.success('已公布成績')
  } catch (e) {
    fail(e)
  }
}

const n = (v: number | null) => (v == null ? '—' : String(v))
const flagged = computed(() => results.value?.items.filter((i) => i.flagged) ?? [])
// 考試還沒結束時，沒登入的人只是「還沒來」，結束後才算缺考
const STATUS = computed(() => ({ IDLE: props.exam.status === 'ended' ? '缺考' : '尚未作答', IN: '作答中', DONE: '已交卷' }) as const)
</script>

<template>
  <p v-if="error" class="card flex items-center gap-3 px-5 py-4 text-[15px] text-grade sm:px-6" role="alert">
    <span class="min-w-0 flex-1">{{ error }}</span>
    <Button variant="outline" class="rounded-full" @click="load">重試</Button>
  </p>

  <!-- 評分 -->
  <div v-else-if="view === 'grading' && results" class="space-y-3">
    <section class="card">
      <div class="px-5 py-4 sm:px-6">
        <h2 class="text-[17px] font-semibold">評分者</h2>
        <p class="mt-1 text-[15px] text-ink-2">每個申論答案由所有被指派的人各自評分，取平均。評分者看不到考生身分，也看不到彼此的分數。</p>
      </div>
      <p v-if="!graders.length" class="border-t border-line px-5 py-5 text-[15px] text-ink-2 sm:px-6">
        這個單位還沒有「評分」角色的教職員。請系統管理員或單位負責人先到單位成員裡加入。
      </p>
      <ul v-else class="divide-y divide-line border-t border-line">
        <li v-for="g in graders" :key="g.staffId" class="flex flex-wrap items-center gap-x-4 gap-y-1.5 px-5 py-3 sm:px-6">
          <div class="min-w-0 flex-1 basis-48">
            <p class="text-base font-medium">{{ g.name }}</p>
            <p class="truncate text-sm text-ink-2">{{ g.email }}</p>
          </div>
          <span v-if="g.assigned" class="text-sm text-ink-2 tabular-nums">已評 {{ g.done }} / {{ g.total }}</span>
          <span v-else-if="results.publishedAt" class="text-sm text-ink-3">未指派</span>
          <!-- 成績公布後不能再改，按鈕直接不顯示 -->
          <Button
            v-if="!results.publishedAt"
            variant="outline"
            class="w-24 rounded-full"
            :disabled="g.assigned && g.done > 0"
            :title="g.assigned && g.done > 0 ? '已經送出分數的評分者不能移除' : undefined"
            @click="toggle(g)"
          >
            {{ g.assigned ? '取消指派' : '指派' }}
          </Button>
        </li>
      </ul>
    </section>

    <section class="card">
      <div class="px-5 py-4 sm:px-6">
        <h2 class="text-[17px] font-semibold">申論答案</h2>
        <p class="mt-1 text-[15px] text-ink-2 tabular-nums">
          {{ results.itemsLocked }} / {{ results.itemsTotal }} 份已評完<template v-if="flagged.length"
            >，其中 {{ flagged.length }} 份評分者之間差距較大，請確認是否需要重評</template
          >。
        </p>
      </div>
      <p v-if="!results.itemsTotal" class="border-t border-line px-5 py-5 text-[15px] text-ink-2 sm:px-6">
        還沒有需要評分的申論答案（考生交卷後才會出現；空白的申論答案直接 0 分）。
      </p>
      <ul v-else class="divide-y divide-line border-t border-line">
        <li v-for="i in results.items" :key="i.anonId + i.questionId" class="px-5 py-3 sm:px-6">
          <div class="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-[15px]">
            <span class="w-40 font-medium tabular-nums">答卷 {{ i.anonId }}，第 {{ i.number }} 題</span>
            <span v-if="i.locked" class="min-w-0 flex-1 basis-48 tabular-nums">
              {{ i.scores.join('、') }} → 平均 <strong class="font-semibold">{{ i.average }}</strong> / {{ i.points }}
            </span>
            <span v-else class="min-w-0 flex-1 basis-48 text-ink-2 tabular-nums">已評 {{ i.graded }} / {{ i.graders }} 位</span>
            <span v-if="i.flagged" class="rounded-full bg-grade-soft px-2.5 py-0.5 text-[13px] font-medium text-grade">差距較大</span>
            <Button v-if="i.graded > 0 && !results.publishedAt" variant="outline" class="rounded-full" @click="((reopening = i), (reason = ''))">
              重開評分
            </Button>
          </div>
          <form v-if="reopening === i" class="mt-3 flex flex-wrap gap-2" @submit.prevent="reopen">
            <input v-model="reason" class="field min-w-0 flex-1 basis-64" placeholder="重開的理由（必填，會留在操作紀錄）" aria-label="重開的理由" />
            <Button variant="primary" size="md" class="h-11 rounded-full" type="submit" :disabled="!reason.trim()">確定重開</Button>
            <Button size="md" class="h-11 rounded-full" @click="reopening = null">取消</Button>
          </form>
        </li>
      </ul>
    </section>
  </div>

  <!-- 成績 -->
  <section v-else-if="results" class="card">
    <div class="no-print flex flex-wrap items-center justify-between gap-3 px-5 py-4 sm:px-6">
      <p class="min-w-0 flex-1 basis-64 text-[15px] text-ink-2">
        <template v-if="results.publishedAt">成績已於 {{ when(results.publishedAt) }} 公布。</template>
        <template v-else-if="exam.status !== 'ended'">考試結束、申論全部評完後，才能公布成績。</template>
        <template v-else-if="!results.complete">還有 {{ results.itemsTotal - results.itemsLocked }} 份申論答案沒評完，全部評完才能公布。</template>
        <template v-else>全部評分完成，可以公布成績。</template>
        滿分 {{ results.fullMarks }} 分。
      </p>
      <a
        :href="`/api${base}/results.csv`"
        download
        class="inline-flex h-9 items-center gap-1.5 rounded-full border border-line-strong px-3 text-sm font-medium transition-colors hover:border-brand hover:text-brand"
      >
        <Download class="size-4" />匯出 CSV
      </a>
      <Button
        v-if="!results.publishedAt"
        variant="primary"
        class="rounded-full"
        :disabled="!results.complete || exam.status !== 'ended'"
        @click="publish"
      >
        公布成績
      </Button>
    </div>
    <div class="overflow-x-auto border-t border-line">
      <table class="w-full min-w-[560px] text-left text-[15px]">
        <thead class="text-sm text-ink-2">
          <tr>
            <th class="px-5 py-2.5 font-medium sm:px-6">座位</th>
            <th class="px-2 py-2.5 font-medium">考生</th>
            <th class="px-2 py-2.5 text-right font-medium">自動批改</th>
            <th class="px-2 py-2.5 text-right font-medium">申論</th>
            <th class="px-5 py-2.5 text-right font-medium sm:px-6">總分</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-line tabular-nums">
          <tr v-for="r in results.rows" :key="r.ticket">
            <td class="px-5 py-2.5 text-ink-3 sm:px-6">{{ r.seat }}</td>
            <td class="px-2 py-2.5">
              <span class="font-medium">{{ r.name }}</span>
              <span class="ml-2 text-sm text-ink-2">{{ r.ticket }}</span>
            </td>
            <template v-if="r.status === 'DONE'">
              <td class="px-2 py-2.5 text-right">{{ n(r.autoScore) }}</td>
              <td class="px-2 py-2.5 text-right">
                <template v-if="r.pending">評分中（剩 {{ r.pending }} 題）</template><template v-else>{{ n(r.essayScore) }}</template>
              </td>
              <td class="px-5 py-2.5 text-right text-lg font-semibold sm:px-6">{{ n(r.total) }}</td>
            </template>
            <td v-else colspan="3" class="px-5 py-2.5 text-right text-ink-2 sm:px-6">{{ STATUS[r.status] }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="!results.rows.length" class="border-t border-line px-5 py-8 text-[15px] text-ink-2 sm:px-6">名單上還沒有考生。</p>
  </section>
</template>
