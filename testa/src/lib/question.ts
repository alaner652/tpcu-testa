import type { Component } from 'vue'
import { CircleDot, ListPlus, PenLine, TextCursorInput } from 'lucide-vue-next'
import type { Question, QuestionType } from '@/types/question'
import { uid } from './utils'

export const QUESTION_TYPES: { value: QuestionType; label: string; icon: Component }[] = [
  { value: 'single', label: '單選', icon: CircleDot },
  { value: 'multiple', label: '多選', icon: ListPlus },
  { value: 'blank', label: '填空', icon: TextCursorInput },
  { value: 'essay', label: '申論', icon: PenLine },
]

export function typeMeta(type: QuestionType) {
  return QUESTION_TYPES.find((t) => t.value === type)!
}

type Draft = Partial<Omit<Question, 'choices' | 'blanks' | 'correct'>> & {
  choices?: string[]
  /** Indexes of the correct choices */
  correct?: number[]
  blanks?: string[][]
}

export function createQuestion(type: QuestionType, draft: Draft = {}): Question {
  const choices = (draft.choices ?? ['', '', '', '']).map((text) => ({ id: uid('c'), text }))
  return {
    id: uid('q'),
    type,
    stem: draft.stem ?? '',
    points: draft.points ?? (type === 'essay' ? 10 : 5),
    explanation: draft.explanation ?? '',
    choices,
    correct: (draft.correct ?? []).map((i) => choices[i].id),
    blanks: (draft.blanks ?? [[]]).map((answers) => ({ id: uid('b'), answers })),
    caseSensitive: draft.caseSensitive ?? false,
    rubric: draft.rubric ?? '',
    wordLimit: draft.wordLimit ?? null,
    aiGrading: draft.aiGrading ?? false,
    review: draft.review,
  }
}

export function cloneQuestion(q: Question): Question {
  const idMap = new Map<string, string>()
  const choices = q.choices.map((c) => {
    const id = uid('c')
    idMap.set(c.id, id)
    return { ...c, id }
  })
  return {
    ...q,
    id: uid('q'),
    choices,
    correct: q.correct.map((id) => idMap.get(id)!).filter(Boolean),
    blanks: q.blanks.map((b) => ({ id: uid('b'), answers: [...b.answers] })),
  }
}

/** What this question is missing; an empty array means it is ready. */
export function issuesOf(q: Question): string[] {
  const issues: string[] = []
  if (!q.stem.trim()) issues.push('尚未輸入題目')
  if (q.type === 'single' || q.type === 'multiple') {
    if (q.choices.filter((c) => c.text.trim()).length < 2) issues.push('至少需要兩個選項')
    if (q.correct.length === 0) issues.push('尚未設定正確答案')
  }
  if (q.type === 'blank' && q.blanks.some((b) => b.answers.length === 0)) {
    issues.push('有空格尚未設定答案')
  }
  return issues
}
