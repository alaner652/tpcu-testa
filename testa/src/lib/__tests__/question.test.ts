import { describe, expect, it } from 'vitest'
import { cloneQuestion, createQuestion, issuesOf } from '@/lib/question'

describe('question validation', () => {
  it('a complete question has no issues', () => {
    expect(issuesOf(createQuestion('single', { stem: '1+1=?', choices: ['1', '2'], correct: [1] }))).toEqual([])
    expect(issuesOf(createQuestion('blank', { stem: '最高的山是＿', blanks: [['玉山']] }))).toEqual([])
    expect(issuesOf(createQuestion('essay', { stem: '請說明動機' }))).toEqual([])
  })

  it('lists what a choice question is missing', () => {
    expect(issuesOf(createQuestion('single'))).toEqual(['尚未輸入題目', '至少需要兩個選項', '尚未設定正確答案'])
  })

  it('warns when a blank has no answer', () => {
    expect(issuesOf(createQuestion('blank', { stem: '＿' }))).toEqual(['有空格尚未設定答案'])
  })
})

describe('duplicating a question', () => {
  it('replaces every id and correct answers still point to the matching choices', () => {
    const q = createQuestion('multiple', { stem: 'x', choices: ['a', 'b', 'c'], correct: [0, 2] })
    const copy = cloneQuestion(q)
    expect(copy.id).not.toBe(q.id)
    expect(copy.choices.map((c) => c.id)).not.toEqual(q.choices.map((c) => c.id))
    expect(copy.correct).toEqual([copy.choices[0].id, copy.choices[2].id])
  })

  it('blank answers are a new array, so editing the copy leaves the original', () => {
    const q = createQuestion('blank', { blanks: [['玉山']] })
    const copy = cloneQuestion(q)
    copy.blanks[0].answers.push('Yushan')
    expect(q.blanks[0].answers).toEqual(['玉山'])
  })
})
