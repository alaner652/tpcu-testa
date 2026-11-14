import { describe, expect, it } from 'vitest'
import { emptyAttempt, grade, isAnswered } from '@/lib/grading'
import { createQuestion } from '@/lib/question'

const attempt = (patch: Partial<ReturnType<typeof emptyAttempt>>) => ({ ...emptyAttempt(), ...patch })

describe('自動批改（出題者預覽用，正式成績由後端算）', () => {
  it('單選：選對得滿分，選錯零分，沒選是未作答', () => {
    const q = createQuestion('single', { choices: ['1', '2'], correct: [1], points: 5 })
    const [wrong, right] = q.choices.map((c) => c.id)
    expect(grade(q, attempt({ choices: [right] }))).toEqual({ status: 'correct', earned: 5 })
    expect(grade(q, attempt({ choices: [wrong] }))).toEqual({ status: 'wrong', earned: 0 })
    expect(grade(q, undefined)).toEqual({ status: 'unanswered', earned: 0 })
  })

  it('多選：要完全相符，少選或多選都不給分', () => {
    const q = createQuestion('multiple', { choices: ['2', '9', '11'], correct: [0, 2], points: 5 })
    const [a, b, c] = q.choices.map((x) => x.id)
    expect(grade(q, attempt({ choices: [c, a] })).status).toBe('correct')
    expect(grade(q, attempt({ choices: [a] })).status).toBe('wrong')
    expect(grade(q, attempt({ choices: [a, b, c] })).status).toBe('wrong')
  })

  it('填空：依答對的空格數給分，預設不分大小寫、忽略空白', () => {
    const q = createQuestion('blank', { blanks: [['玉山'], ['Taipei', '台北']], points: 5 })
    const [b1, b2] = q.blanks.map((b) => b.id)
    expect(grade(q, attempt({ blanks: { [b1]: ' 玉 山 ', [b2]: 'taipei' } }))).toEqual({ status: 'correct', earned: 5 })
    expect(grade(q, attempt({ blanks: { [b1]: '玉山', [b2]: '高雄' } }))).toEqual({ status: 'partial', earned: 2.5 })
    expect(grade(q, attempt({ blanks: { [b1]: '阿里山' } }))).toEqual({ status: 'wrong', earned: 0 })
  })

  it('填空：開啟區分大小寫後，大小寫不同算錯', () => {
    const q = createQuestion('blank', { blanks: [['Taipei']], caseSensitive: true })
    const id = q.blanks[0].id
    expect(grade(q, attempt({ blanks: { [id]: 'taipei' } })).status).toBe('wrong')
    expect(grade(q, attempt({ blanks: { [id]: 'Taipei' } })).status).toBe('correct')
  })

  it('申論：有寫就是待評，不自動給分；只有空白視為沒作答', () => {
    const q = createQuestion('essay')
    expect(grade(q, attempt({ essay: '我的動機' }))).toEqual({ status: 'pending', earned: 0 })
    expect(isAnswered(q, attempt({ essay: '   ' }))).toBe(false)
  })
})
