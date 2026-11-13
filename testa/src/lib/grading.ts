import type { Question } from '@/types/question'

export interface Attempt {
  choices: string[]
  blanks: Record<string, string>
  essay: string
  flagged: boolean
}

/** 正式考試：作答存到伺服器，計時與交卷也由伺服器決定。 */
export interface RemoteExam {
  /** 之前存過的答案（斷線、換電腦後續考） */
  initial: Record<string, Partial<Attempt>>
  remainingSeconds: number
  /** 存一題的答案，回傳伺服器算的剩餘秒數 */
  save(questionId: string, answer: Pick<Attempt, 'choices' | 'blanks' | 'essay'>): Promise<number>
  submit(): Promise<void>
}

export function emptyAttempt(): Attempt {
  return { choices: [], blanks: {}, essay: '', flagged: false }
}

export function isAnswered(q: Question, a: Attempt | undefined) {
  if (!a) return false
  if (q.type === 'single' || q.type === 'multiple') return a.choices.length > 0
  if (q.type === 'blank') return q.blanks.some((b) => (a.blanks[b.id] ?? '').trim() !== '')
  return a.essay.trim() !== ''
}

export type GradeStatus = 'correct' | 'partial' | 'wrong' | 'unanswered' | 'pending'

export interface Grade {
  status: GradeStatus
  earned: number
}

const normalize = (text: string, caseSensitive: boolean) => {
  const t = text.trim().replace(/\s+/g, '')
  return caseSensitive ? t : t.toLowerCase()
}

/** 自動批改：選擇題需完全相符；填空題依空格數平均給分；申論題留待人工閱卷。 */
export function grade(q: Question, a: Attempt | undefined): Grade {
  if (q.type === 'essay') return { status: isAnswered(q, a) ? 'pending' : 'unanswered', earned: 0 }
  if (!a || !isAnswered(q, a)) return { status: 'unanswered', earned: 0 }

  if (q.type === 'single' || q.type === 'multiple') {
    const right = a.choices.length === q.correct.length && a.choices.every((id) => q.correct.includes(id))
    return right ? { status: 'correct', earned: q.points } : { status: 'wrong', earned: 0 }
  }

  const hits = q.blanks.filter((b) => {
    const given = normalize(a.blanks[b.id] ?? '', q.caseSensitive)
    return given !== '' && b.answers.some((answer) => normalize(answer, q.caseSensitive) === given)
  }).length
  const earned = Math.round((q.points * hits * 100) / q.blanks.length) / 100
  return { status: hits === q.blanks.length ? 'correct' : hits > 0 ? 'partial' : 'wrong', earned }
}
