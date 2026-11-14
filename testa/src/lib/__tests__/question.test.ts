import { describe, expect, it } from 'vitest'
import { cloneQuestion, createQuestion, issuesOf } from '@/lib/question'

describe('題目檢查', () => {
  it('完整的題目沒有問題', () => {
    expect(issuesOf(createQuestion('single', { stem: '1+1=?', choices: ['1', '2'], correct: [1] }))).toEqual([])
    expect(issuesOf(createQuestion('blank', { stem: '最高的山是＿', blanks: [['玉山']] }))).toEqual([])
    expect(issuesOf(createQuestion('essay', { stem: '請說明動機' }))).toEqual([])
  })

  it('列出選擇題缺少的部分', () => {
    expect(issuesOf(createQuestion('single'))).toEqual(['尚未輸入題目', '至少需要兩個選項', '尚未設定正確答案'])
  })

  it('填空題的空格沒有答案時要提醒', () => {
    expect(issuesOf(createQuestion('blank', { stem: '＿' }))).toEqual(['有空格尚未設定答案'])
  })
})

describe('複製題目', () => {
  it('換掉所有 id，正確答案仍指向對應的選項', () => {
    const q = createQuestion('multiple', { stem: 'x', choices: ['a', 'b', 'c'], correct: [0, 2] })
    const copy = cloneQuestion(q)
    expect(copy.id).not.toBe(q.id)
    expect(copy.choices.map((c) => c.id)).not.toEqual(q.choices.map((c) => c.id))
    expect(copy.correct).toEqual([copy.choices[0].id, copy.choices[2].id])
  })

  it('填空答案是新的陣列，改複本不會動到原題', () => {
    const q = createQuestion('blank', { blanks: [['玉山']] })
    const copy = cloneQuestion(q)
    copy.blanks[0].answers.push('Yushan')
    expect(q.blanks[0].answers).toEqual(['玉山'])
  })
})
