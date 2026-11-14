<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ChevronLeft, Eye, RotateCw } from 'lucide-vue-next'
import Button from '@/components/ui/Button.vue'
import { CANDIDATE_STATUS, EXAM_STATUS, when, type Candidate, type Exam } from '@/composables/useExams'
import { api } from '@/lib/api'

/** 系統管理員：負責人授權給我的考試。只能看；每開一次都會留下操作紀錄。 */
interface Grant {
  id: number
  examName: string
  unitName: string
  grantedByName: string
  reason: string
  expiresAt: string
}
interface Row {
  ticket: string
  name: string
  seat: number
  status: Candidate['status']
  autoScore: number | null
  essayScore: number | null
  total: number | null
  pending: number
}
interface View {
  grant: Grant
  exam: Exam
  candidates: Candidate[]
  results: { fullMarks: number; itemsTotal: number; itemsLocked: number; publishedAt: string | null; rows: Row[] }
}

const grants = ref<Grant[] | null>(null)
const view = ref<View | null>(null)
const error = ref('')

async function load() {
  try {
    grants.value = await api<Grant[]>('/admin/emergency-access')
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message
  }
}
onMounted(load)

async function open(g: Grant) {
  try {
    view.value = await api<View>(`/admin/emergency-access/${g.id}`)
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message
    view.value = null
    load()
  }
}

const n = (v: number | null) => (v == null ? '—' : String(v))
const candidate = (ticket: string) => view.value?.candidates.find((c) => c.ticket === ticket)
</script>

<template>
  <section class="card">
    <p v-if="error" class="flex items-center gap-3 px-5 py-4 text-[15px] text-grade sm:px-6" role="alert">
      <span class="min-w-0 flex-1">{{ error }}</span>
      <Button variant="outline" class="rounded-full" @click="load"><RotateCw />重試</Button>
    </p>

    <template v-else-if="view">
      <div class="px-5 py-4 sm:px-6">
        <button type="button" class="inline-flex h-9 items-center gap-1 rounded-full pr-3 text-sm text-ink-2 hover:text-ink" @click="view = null">
          <ChevronLeft class="size-4" />授權給我的考試
        </button>
        <p class="mt-1 flex flex-wrap items-center gap-2">
          <span class="text-xl font-bold">{{ view.exam.name }}</span>
          <span class="rounded-full px-2.5 py-0.5 text-[13px] font-medium" :class="EXAM_STATUS[view.exam.status].class">
            {{ EXAM_STATUS[view.exam.status].label }}
          </span>
        </p>
        <p class="mt-1 text-[15px] text-ink-2 tabular-nums">
          {{ view.exam.unitName }}，{{ when(view.exam.startAt) }}，作答 {{ view.exam.duration }} 分鐘。滿分 {{ view.results.fullMarks }} 分，申論已評完
          {{ view.results.itemsLocked }} / {{ view.results.itemsTotal }} 份，{{ view.results.publishedAt ? `成績已於 ${when(view.results.publishedAt)} 公布` : '成績尚未公布' }}。
        </p>
        <p class="mt-3 rounded-control bg-marker px-4 py-3 text-sm text-marker-ink">
          緊急檢視：{{ view.grant.grantedByName }} 授權，有效到 {{ when(view.grant.expiresAt) }}。理由：{{ view.grant.reason }}。這次檢視已寫入操作紀錄。
        </p>
      </div>
      <div class="overflow-x-auto border-t border-line">
        <table class="w-full min-w-[640px] text-left text-[15px]">
          <thead class="text-sm text-ink-2">
            <tr>
              <th class="px-5 py-2.5 font-medium sm:px-6">座位</th>
              <th class="px-2 py-2.5 font-medium">考生</th>
              <th class="px-2 py-2.5 font-medium">狀態</th>
              <th class="px-2 py-2.5 text-right font-medium">自動批改</th>
              <th class="px-2 py-2.5 text-right font-medium">申論</th>
              <th class="px-5 py-2.5 text-right font-medium sm:px-6">總分</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-line tabular-nums">
            <tr v-for="r in view.results.rows" :key="r.ticket">
              <td class="px-5 py-2.5 text-ink-3 sm:px-6">{{ r.seat }}</td>
              <td class="px-2 py-2.5">
                <span class="font-medium">{{ r.name }}</span>
                <span class="ml-2 text-sm text-ink-2">{{ r.ticket }}</span>
              </td>
              <td class="px-2 py-2.5 text-sm">
                {{ CANDIDATE_STATUS[r.status].label }}
                <span v-if="candidate(r.ticket)?.leaveCount" class="text-ink-2">，離開畫面 {{ candidate(r.ticket)?.leaveCount }} 次</span>
              </td>
              <td class="px-2 py-2.5 text-right">{{ n(r.autoScore) }}</td>
              <td class="px-2 py-2.5 text-right">
                <template v-if="r.pending">評分中（剩 {{ r.pending }} 題）</template><template v-else>{{ n(r.essayScore) }}</template>
              </td>
              <td class="px-5 py-2.5 text-right font-semibold sm:px-6">{{ n(r.total) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="!view.results.rows.length" class="border-t border-line px-5 py-8 text-[15px] text-ink-2 sm:px-6">名單上沒有考生。</p>
    </template>

    <template v-else-if="grants">
      <p class="px-5 py-4 text-[15px] text-ink-2 sm:px-6">
        系統管理員平常看不到考試內容。單位負責人授權之後，這裡會出現那場考試，在時效內可以檢視名單、作答狀態與成績。每開一次都會留下操作紀錄。
      </p>
      <p v-if="!grants.length" class="border-t border-line px-5 py-5 text-[15px] sm:px-6">目前沒有授權給你的考試。</p>
      <ul v-else class="divide-y divide-line border-t border-line">
        <li v-for="g in grants" :key="g.id" class="flex flex-wrap items-center gap-x-4 gap-y-2 px-5 py-3 sm:px-6">
          <div class="min-w-0 flex-1 basis-64 text-[15px]">
            <p class="font-medium">{{ g.examName }}</p>
            <p class="text-sm text-ink-2">{{ g.unitName }}，{{ g.grantedByName }} 授權，有效到 {{ when(g.expiresAt) }}。理由：{{ g.reason }}</p>
          </div>
          <Button variant="outline" class="rounded-full" @click="open(g)"><Eye />檢視</Button>
        </li>
      </ul>
    </template>
  </section>
</template>
