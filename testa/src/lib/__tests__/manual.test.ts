import { describe, expect, it } from 'vitest'
import { CHAPTERS, parseChapter } from '@/data/manual'

describe('manual', () => {
  it('splits a chapter into ## sections and renders clips, steps and tables', () => {
    const [first, second] = parseChapter('intro is ignored\n## One\n\n![Login](staff-login)\n\n1. a\n2. b\n\n## Two\n\n| a | b |\n| - | - |\n| 1 | 2 |\n')
    expect(first!.title).toBe('One')
    expect(first!.html).toContain('manual/staff-login.mp4')
    expect(first!.html).toContain('<ol>')
    expect(second!.html).toContain('<div class="table-scroll"><table>')
  })

  it('loads every chapter file', () => {
    for (const c of CHAPTERS) expect(c.sections.length, c.id).toBeGreaterThan(0)
  })
})
