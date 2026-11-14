import { ref } from 'vue'
import { api } from '@/lib/api'
import { useAuth } from './useAuth'

/** 教職員端的考試與考生名單（後端 API）。考生端在第 4 步之前仍用 useWorkspace 的示範資料。 */
export type ExamStatus = 'draft' | 'scheduled' | 'live' | 'ended'
export type ExamMode = 'ONLINE' | 'CLASSROOM'
export interface Exam {
  id: number
  unitId: number
  unitName: string
  name: string
  bankId: number | null
  bankName: string | null
  /** ISO 時間；草稿可以還沒設定 */
  startAt: string | null
  duration: number
  mode: ExamMode
  status: ExamStatus
  /** 發布後才有（發布時凍結的題目） */
  questionCount: number
  totalPoints: number
  takers: number
  inProgress: number
  submitted: number
  /** 我是不是負責人（可以修改、發布、匯入名單）；監考只能看與重設登入碼 */
  canManage: boolean
  resultsPublishedAt: string | null
  /** 封存後唯讀，考生不能再登入 */
  archivedAt: string | null
  /** 資料要保留到什麼時候；封存而且過了這個時間才能刪除（考試結束後才有） */
  retainUntil: string | null
}
export type CandidateStatus = 'IDLE' | 'IN' | 'DONE'
export interface Candidate {
  id: number
  ticket: string
  name: string
  dept: string
  seat: number
  code: string
  status: CandidateStatus
  /** 登入碼輸錯太多次，暫時鎖定 */
  locked: boolean
  /** 作答中離開考試畫面的次數 */
  leaveCount: number
}
export interface RosterRow {
  ticket: string
  name: string
  dept: string
}
export interface RosterPreview {
  file: string
  rows: RosterRow[]
  problems: { row: string; who: string; reason: string }[]
}
export interface ExamInput {
  name: string
  bankId: number
  startAt: string | null
  duration: number
  mode: ExamMode
}

export const EXAM_STATUS: Record<ExamStatus, { label: string; class: string }> = {
  draft: { label: '草稿', class: 'bg-sunken text-ink-2' },
  scheduled: { label: '已排程', class: 'bg-marker text-marker-ink' },
  live: { label: '進行中', class: 'bg-brand text-brand-fg' },
  ended: { label: '已結束', class: 'bg-sunken text-ink-2' },
}
export const CANDIDATE_STATUS: Record<CandidateStatus, { label: string; class: string }> = {
  IDLE: { label: '尚未登入', class: 'bg-sunken text-ink-2' },
  IN: { label: '作答中', class: 'bg-brand text-brand-fg' },
  DONE: { label: '已交卷', class: 'bg-brand-soft text-brand-strong' },
}
export const MODE_LABEL: Record<ExamMode, string> = { CLASSROOM: '電腦教室', ONLINE: '純線上' }

/** 「11 月 20 日 10:00」 */
export function when(iso: string | null) {
  if (!iso) return '尚未設定開始時間'
  const d = new Date(iso)
  const time = d.toLocaleTimeString('zh-TW', { hour: '2-digit', minute: '2-digit', hour12: false })
  const year = d.getFullYear() === new Date().getFullYear() ? '' : `${d.getFullYear()} 年 `
  return `${year}${d.getMonth() + 1} 月 ${d.getDate()} 日 ${time}`
}

/** 保存期限顯示成「2027 年 12 月 31 日」（retainUntil 是隔天 0 點） */
export function retainLabel(iso: string | null) {
  if (!iso) return ''
  const d = new Date(new Date(iso).getTime() - 1000)
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日`
}
export const canPurge = (exam: Exam) => !!exam.archivedAt && !!exam.retainUntil && Date.now() >= new Date(exam.retainUntil).getTime()

/** ISO 時間 → <input type="datetime-local"> 的值（本地時間） */
export function toLocalInput(iso: string | null) {
  if (!iso) return ''
  const d = new Date(iso)
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}

const exams = ref<Exam[]>([])
const loading = ref(true)
const loadError = ref('')

function put(exam: Exam) {
  const i = exams.value.findIndex((e) => e.id === exam.id)
  if (i >= 0) exams.value[i] = exam
  else exams.value.unshift(exam)
  return exam
}

async function load() {
  const { user, ready } = useAuth()
  await ready
  loadError.value = ''
  if (!user.value || user.value.role === 'student') {
    exams.value = []
    loading.value = false
    return
  }
  try {
    exams.value = await api<Exam[]>('/exams')
  } catch (e) {
    loadError.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

export function useExams() {
  return {
    exams,
    loading,
    loadError,
    load,
    refresh: async (id: number) => put(await api<Exam>(`/exams/${id}`)),
    create: async (input: ExamInput) => put(await api<Exam>('/exams', { method: 'POST', body: input })),
    update: async (id: number, input: ExamInput) =>
      put(await api<Exam>(`/exams/${id}`, { method: 'PATCH', body: { ...input, clearStartAt: !input.startAt } })),
    publish: async (id: number) => put(await api<Exam>(`/exams/${id}/publish`, { method: 'POST' })),
    withdraw: async (id: number) => put(await api<Exam>(`/exams/${id}/withdraw`, { method: 'POST' })),
    end: async (id: number) => put(await api<Exam>(`/exams/${id}/end`, { method: 'POST' })),
    archive: async (id: number) => put(await api<Exam>(`/exams/${id}/archive`, { method: 'POST' })),
    unarchive: async (id: number) => put(await api<Exam>(`/exams/${id}/unarchive`, { method: 'POST' })),
    /** 草稿可以直接刪；封存的考試要過了保存期限，而且要填理由 */
    remove: async (id: number, reason?: string) => {
      await api(`/exams/${id}`, { method: 'DELETE', body: reason ? { reason } : undefined })
      exams.value = exams.value.filter((e) => e.id !== id)
    },
    candidates: (examId: number) => api<Candidate[]>(`/exams/${examId}/candidates`),
    preview: (examId: number, file: File) => {
      const form = new FormData()
      form.append('file', file)
      return api<RosterPreview>(`/exams/${examId}/candidates/preview`, { method: 'POST', body: form })
    },
    importRows: (examId: number, rows: RosterRow[]) =>
      api<{ added: number; total: number }>(`/exams/${examId}/candidates`, { method: 'POST', body: { rows } }),
    resetCode: (examId: number, candidateId: number) =>
      api<{ code: string }>(`/exams/${examId}/candidates/${candidateId}/reset-code`, { method: 'POST' }),
    removeCandidate: (examId: number, candidateId: number) =>
      api(`/exams/${examId}/candidates/${candidateId}`, { method: 'DELETE' }),
  }
}
