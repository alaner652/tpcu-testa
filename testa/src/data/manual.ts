import { Marked } from 'marked'

/**
 * The operation manual shown at /help. Each chapter is a Markdown file in src/manual (Chinese, for end users).
 * - `## Title` starts a section (one card); `#### 注意` is a small sub-heading inside it.
 * - `![label](name)` embeds the clip public/manual/name.mp4, recorded by tools/record-videos.mjs.
 * Keep wording in line with the UI labels; the files are ours, so the rendered HTML is trusted.
 */
export interface ManualSection {
  id: string
  title: string
  html: string
}

export interface ManualChapter {
  id: string
  title: string
  /** Who reads this chapter, shown under the title */
  who: string
  /** Sections fold into a list that opens one at a time (FAQ) */
  accordion?: boolean
  sections: ManualSection[]
}

const marked = new Marked({
  renderer: {
    image({ href, text }) {
      const src = `${import.meta.env.BASE_URL}manual/${href}.mp4#t=0.1`
      return `<video src="${src}" class="no-print" controls muted playsinline preload="metadata" aria-label="${text}的示範影片"></video>`
    },
  },
})

// Wide tables scroll sideways on phones instead of stretching the page
const render = (md: string) =>
  marked.parse(md, { async: false }).replaceAll('<table>', '<div class="table-scroll"><table>').replaceAll('</table>', '</table></div>')

const files = import.meta.glob<string>('../manual/*.md', { query: '?raw', import: 'default', eager: true })

/** Split a chapter file into its `##` sections and render each one. */
export function parseChapter(markdown: string): ManualSection[] {
  return markdown
    .split(/^## /m)
    .slice(1)
    .map((part, i) => {
      const [title, ...body] = part.split('\n')
      return { id: `s${i + 1}`, title: title!.trim(), html: render(body.join('\n').trim()) }
    })
}

const chapter = (id: string, title: string, who: string, accordion = false): ManualChapter => ({
  id,
  title,
  who,
  accordion,
  sections: parseChapter(files[`../manual/${id}.md`] ?? ''),
})

export const CHAPTERS: ManualChapter[] = [
  chapter('start', '開始使用', '所有人'),
  chapter('admin', '系統管理員', '管理帳號、單位與系統的人'),
  chapter('author', '出題老師', '有「出題」角色的人'),
  chapter('officer', '考試承辦人', '有「負責人」或「監考」角色的人'),
  chapter('grader', '閱卷老師', '有「評分」角色的人'),
  chapter('candidate', '考生須知', '考生（可以列印發給考生）'),
  chapter('faq', '常見問題', '所有人', true),
]
