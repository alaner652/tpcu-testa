#!/usr/bin/env node

/**
 * Records the short clips for the in-app manual (testa/public/manual/*.mp4): silent, 1280 x 800,
 * with a drawn cursor so viewers can follow each click. Each clip sets up its own demo data, so clips can be
 * re-recorded one at a time after a UI change.
 *
 * Prerequisites: the same as tools/capture-screenshots.mjs (backend in the dev profile, the production build on :4173,
 * Chrome), plus ffmpeg on the PATH.
 *
 * Run from the repository root:
 *   node tools/record-videos.mjs               # every clip
 *   node tools/record-videos.mjs exam-create   # only the named clips
 */
import { mkdir, mkdtemp, stat, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { api, candidateLogin, clearSession, pause, staffLogin, visit, waitFor, withChrome } from './lib/chrome.mjs'
import { CURSOR_SCRIPT, Recorder } from './lib/recorder.mjs'

const OUTPUT = resolve('testa/public/manual')
const SIZE = { width: 1280, height: 800, scale: 1, mobile: false }
const stamp = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Taipei', dateStyle: 'short', timeStyle: 'short' })
  .format(new Date())
  .replace(/\D/g, '')
  .slice(4)

const OFFICER = ['officer@testa.tw', 'testa-officer']
const TEACHER = ['teacher@testa.tw', 'testa-teacher']
const ADMIN = ['admin@testa.tw', 'testa-admin']

/** Demo data shared by several clips, created through the API before recording. */
async function demoBank(page) {
  const me = await staffLogin(page, ...TEACHER)
  const name = '手冊示範題庫'
  const existing = (await api(page, '/banks')).find((b) => b.name === name)
  if (existing) return existing
  return api(page, '/banks', 'POST', {
    unitId: me.units[0].id,
    name,
    subject: '數學',
    duration: 60,
    questions: [
      { id: 'm-q1', type: 'single', stem: '若 $f(x)=x^2-4x+3$，則最小值為何？', points: 5, choices: [{ id: 'a', text: '$-1$' }, { id: 'b', text: '$0$' }, { id: 'c', text: '$1$' }], correct: ['a'] },
      { id: 'm-q2', type: 'blank', stem: '等差數列首項為 3、公差為 4，則第十項為＿＿。', points: 5, blanks: [{ id: 'b1', answers: ['39'] }] },
      { id: 'm-q3', type: 'essay', stem: '請說明你選擇本科系的理由。', points: 10, rubric: '動機具體、與科系相關、條理清楚。', wordLimit: 300 },
    ],
  })
}

/** A published exam that started a minute ago, with a small roster. */
async function liveExam(page, label, rows) {
  const bank = await demoBank(page)
  await staffLogin(page, ...OFFICER)
  const exam = await api(page, '/exams', 'POST', {
    name: `${label} ${stamp}`,
    bankId: bank.id,
    startAt: new Date(Date.now() - 60_000).toISOString(),
    duration: 60,
    mode: 'CLASSROOM',
  })
  await api(page, `/exams/${exam.id}/candidates`, 'POST', { rows })
  await api(page, `/exams/${exam.id}/publish`, 'POST')
  return { exam, roster: await api(page, `/exams/${exam.id}/candidates`) }
}

const people = (prefix, names) =>
  names.map((name, i) => ({ ticket: `${prefix}${stamp}-${String(i + 1).padStart(2, '0')}`, name, dept: '資訊工程科' }))

const clips = {
  /** Staff sign in with email and password. */
  async 'staff-login'(page, rec) {
    await clearSession(page)
    await rec.start()
    await rec.click('教職員')
    await rec.type({ field: '帳號' }, OFFICER[0])
    await rec.type({ field: '密碼' }, OFFICER[1])
    await rec.click('登入')
    await waitFor(page, '王承辦')
    await pause(1200)
  },

  /** An administrator creates one account and adds it to a unit in the same form. */
  async 'admin-create-account'(page, rec) {
    await staffLogin(page, ...ADMIN, '/admin')
    const unit = (await api(page, '/units')).find((u) => u.active)
    await visit(page, '/admin', '需要處理')
    await rec.start()
    await rec.click('使用者', { after: 1000 })
    await rec.click('新增帳號')
    await rec.type({ field: 'Email' }, `teacher${stamp}@school.edu.tw`)
    await rec.type({ field: '姓名' }, '陳老師')
    await rec.choose({ field: '加入單位' }, `加入「${unit.name}」`)
    await rec.choose({ field: '角色' }, '教師（出題＋評分）')
    await rec.click('建立帳號', { after: 2500 })
  },

  /** The administrator reads the overview, then finds what one person did this week in the audit log. */
  async 'admin-audit'(page, rec) {
    await staffLogin(page, ...OFFICER)
    await staffLogin(page, ...ADMIN, '/admin')
    await waitFor(page, '需要處理')
    await rec.start()
    await pause(2000)
    await rec.click('操作紀錄', { after: 1200 })
    await rec.type({ field: '搜尋操作紀錄' }, '王承辦')
    await rec.click('7 天內', { after: 1500 })
    // The newest entry, at the top of the list
    await rec.click({ css: 'section > ul:first-of-type > li:first-child > button[aria-expanded]' }, { after: 2500 })
  },

  /** A teacher starts a bank, writes one choice question and tries it as a candidate. */
  async 'bank-write'(page, rec) {
    await staffLogin(page, ...TEACHER, '/banks')
    await waitFor(page, '新增題庫')
    await rec.start()
    await rec.click('新增題庫', { after: 1200 })
    await rec.type({ field: '題庫名稱' }, '數學科模擬題')
    await rec.type({ field: '科目' }, '數學')
    await rec.click('單選題', { after: 1000 })
    await rec.type({ field: '題目' }, '下列何者是質數？')
    for (const [letter, text] of [['A', '21'], ['B', '23'], ['C', '25'], ['D', '27']]) await rec.type({ field: `選項 ${letter}` }, text)
    await rec.click('將選項 B 設為正確答案', { after: 1200 })
    await rec.click('模擬作答', { after: 2500 })
  },

  /** The officer creates an exam, imports the roster from a spreadsheet and publishes it. */
  async 'exam-create'(page, rec) {
    const bank = await demoBank(page)
    const roster = join(await mkdtemp(join(tmpdir(), 'testa-roster-')), '考生名單.csv')
    const rows = people('M', ['陳怡安', '林柏宇', '張雅婷', '黃冠廷', '李佳穎'])
    await writeFile(roster, ['准考證號,姓名,報考科系', ...rows.map((r) => `${r.ticket},${r.name},${r.dept}`)].join('\n'))
    await staffLogin(page, ...OFFICER, '/exams')
    await waitFor(page, '建立考試')
    const name = `數學科入學測驗 ${stamp}`
    const start = new Date(Date.now() + 86_400_000)
    start.setHours(9, 0, 0, 0)
    const local = `${start.getFullYear()}-${String(start.getMonth() + 1).padStart(2, '0')}-${String(start.getDate()).padStart(2, '0')}T09:00`
    await rec.start()
    await rec.click('建立考試', { after: 1000 })
    await rec.type({ field: '考試名稱' }, name)
    await rec.choose({ field: '使用的題庫' }, `${bank.name}（${bank.questions.length} 題）`)
    await rec.fill({ field: '開始時間' }, local)
    await rec.fill({ field: '作答時間（分鐘）' }, 60)
    await rec.click('建立草稿', { after: 1500 })
    await rec.click({ css: 'a', text: name }, { after: 1500 })
    await rec.upload('匯入名單', roster)
    await rec.click(`匯入 ${rows.length} 位考生`, { after: 2000 })
    await rec.click('登入單', { after: 2000 })
    await rec.click('考試', { after: 1500 })
    await rec.click({ text: '發布', in: name }, { after: 1200 })
    await rec.click({ css: '[role=alertdialog] button', text: '發布' }, { after: 2000 })
  },

  /** During the exam, the officer picks two candidates and gives them extra time at once. */
  async 'exam-proctor'(page, rec) {
    const { exam, roster } = await liveExam(page, '監考示範', people('P', ['陳怡安', '林柏宇', '張雅婷', '黃冠廷']))
    for (const c of roster.slice(0, 3)) {
      await candidateLogin(page, c.ticket, c.code)
      await api(page, '/candidate/start', 'POST')
    }
    await staffLogin(page, ...OFFICER, `/exams/${exam.id}`)
    await waitFor(page, exam.name)
    await rec.start()
    await rec.click(`選取 ${roster[0].name}`)
    await rec.click(`選取 ${roster[1].name}`)
    await rec.click('補時', { after: 900 })
    await rec.choose({ field: '補幾分鐘' }, '補 10 分鐘')
    await rec.type({ field: '補時的原因' }, '電腦當機換座位')
    await rec.click('替 2 位補時', { after: 1000 })
    await rec.confirmIfAsked()
    await pause(1500)
  },

  /** A candidate signs in with the slip, answers and submits. */
  async 'candidate-exam'(page, rec) {
    const { roster } = await liveExam(page, '考生示範', people('S', ['陳怡安']))
    const [me] = roster
    await clearSession(page)
    await rec.start()
    await rec.type({ field: '准考證號' }, me.ticket)
    await rec.type({ field: '登入碼' }, me.code)
    await rec.click('登入', { after: 1500 })
    await rec.click('進入考試', { after: 1500 })
    await rec.click({ css: 'label', text: '我已閱讀考試須知' })
    await rec.click('開始作答', { after: 1500 })
    await rec.click({ css: '[role=radiogroup] li:first-child [role=radio]' })
    await rec.click('下一題', { after: 900 })
    await rec.type({ field: '空格 1' }, '39')
    await rec.click('下一題', { after: 900 })
    await rec.type({ field: '第 3 題作答' }, '我從高中開始寫程式，希望在這裡把興趣變成專業。')
    await rec.click('交卷', { after: 1000 })
    await rec.click({ css: '[role=alertdialog] button', text: '交卷' }, { after: 2500 })
  },

  /** A grader scores an essay without seeing who wrote it. */
  async grading(page, rec) {
    await endedExam(page, '閱卷示範')
    await staffLogin(page, ...TEACHER, '/grading')
    await waitFor(page, '給分')
    await rec.start()
    await rec.type({ field: '給分' }, '8')
    await rec.type({ field: '評語（可以不寫）' }, '動機具體，可以再多說明未來規劃。')
    await rec.click('送出，看下一份', { after: 2500 })
  },

  /** After grading, the officer publishes the results. */
  async 'results-publish'(page, rec) {
    const exam = await endedExam(page, '成績示範')
    await staffLogin(page, ...TEACHER)
    for (const item of await api(page, `/grading/exams/${exam.id}/items`)) {
      await api(page, `/grading/exams/${exam.id}/items/${item.anonId}/${item.questionId}`, 'PUT', { score: 8, comment: '' })
    }
    await staffLogin(page, ...OFFICER, `/exams/${exam.id}`)
    await waitFor(page, exam.name)
    await rec.start()
    await rec.click('成績', { after: 1500 })
    await rec.click('公布成績', { after: 1000 })
    await rec.click({ css: '[role=alertdialog] button', text: '公布成績' }, { after: 2500 })
  },
}

/** An ended exam with one submitted paper and the demo teacher as its grader. */
async function endedExam(page, label) {
  const teacher = await staffLogin(page, ...TEACHER)
  const { exam, roster } = await liveExam(page, label, people('G', ['陳怡安']))
  await candidateLogin(page, roster[0].ticket, roster[0].code)
  await api(page, '/candidate/start', 'POST')
  await api(page, '/candidate/answers/m-q1', 'PUT', { choices: ['a'] })
  await api(page, '/candidate/answers/m-q2', 'PUT', { blanks: { b1: '39' } })
  await api(page, '/candidate/answers/m-q3', 'PUT', { essay: '我從高中開始寫程式，參加過校內的專題競賽，希望在這裡把興趣變成專業，畢業後投入軟體開發。' })
  await api(page, '/candidate/submit', 'POST')
  await staffLogin(page, ...OFFICER)
  await api(page, `/exams/${exam.id}/graders`, 'PUT', { staffIds: [teacher.id] })
  await api(page, `/exams/${exam.id}/end`, 'POST')
  return exam
}

async function main() {
  const wanted = process.argv.slice(2)
  const unknown = wanted.filter((name) => !clips[name])
  if (unknown.length) throw new Error(`Unknown clip: ${unknown.join(', ')}. Clips: ${Object.keys(clips).join(', ')}`)
  await mkdir(OUTPUT, { recursive: true })
  await withChrome(async (browser) => {
    for (const name of wanted.length ? wanted : Object.keys(clips)) {
      const page = await browser.newPage(SIZE, CURSOR_SCRIPT)
      const rec = new Recorder(page)
      await clips[name](page, rec)
      const file = join(OUTPUT, `${name}.mp4`)
      const seconds = await rec.stop(file)
      const { size } = await stat(file)
      process.stdout.write(`saved ${name}.mp4  ${seconds.toFixed(1)} s  ${(size / 1024).toFixed(0)} KB\n`)
      page.close()
    }
  })
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
