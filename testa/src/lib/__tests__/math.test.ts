import { describe, expect, it } from 'vitest'
import { oneLine, renderMathText } from '@/lib/math'

describe('題目文字轉 HTML', () => {
  it('公式以外的文字一律跳脫，不能注入 HTML', () => {
    const html = renderMathText('<img src=x onerror="alert(1)"> & <script>')
    expect(html).not.toContain('<img')
    expect(html).not.toContain('<script')
    expect(html).toContain('&lt;img src=x onerror=&quot;alert(1)&quot;&gt; &amp; &lt;script&gt;')
  })

  it('換行變成 <br>，$…$ 交給 KaTeX', () => {
    const html = renderMathText('第一行\n$x^2$')
    expect(html).toContain('第一行<br>')
    expect(html).toContain('class="katex"')
  })

  it('公式語法錯誤時顯示原文（跳脫過），不丟例外', () => {
    const html = renderMathText('$\\frac{1}{$')
    expect(html).toContain('公式語法有誤')
    expect(html).toContain('$\\frac{1}{$')
  })

  it('oneLine 去掉換行並把區塊公式變成行內', () => {
    expect(oneLine('求\n$$\\dfrac{1}{2}$$')).toBe('求 $\\frac{1}{2}$')
  })
})
