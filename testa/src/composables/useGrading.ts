import { computed, ref } from 'vue'
import { api } from '@/lib/api'
import { useAuth } from './useAuth'

/** Graders: exams I am assigned to and essay answers to grade. */
export interface GradingExam {
  examId: number
  name: string
  total: number
  done: number
}
export interface GradingItem {
  /** Anonymous number; graders never see the candidate */
  anonId: string
  questionId: string
  number: number
  stem: string
  rubric: string
  points: number
  answer: string
  myScore: number | null
  myComment: string | null
}

const exams = ref<GradingExam[]>([])
const pending = computed(() => exams.value.reduce((sum, e) => sum + (e.total - e.done), 0))

async function load() {
  const { user, ready } = useAuth()
  await ready
  const grader = user.value?.units?.some((u) => u.roles.includes('GRADER'))
  if (!grader) return void (exams.value = [])
  exams.value = await api<GradingExam[]>('/grading/exams').catch(() => [])
}

export function useGrading() {
  return {
    exams,
    pending,
    load,
    items: (examId: number) => api<GradingItem[]>(`/grading/exams/${examId}/items`),
    score: (examId: number, item: GradingItem, score: number, comment: string) =>
      api(`/grading/exams/${examId}/items/${item.anonId}/${encodeURIComponent(item.questionId)}`, { method: 'PUT', body: { score, comment } }),
  }
}
