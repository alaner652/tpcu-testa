#!/usr/bin/env node

/**
 * Screenshots for a UI review: every staff page and admin tab, desktop and phone, scrolled screen by screen.
 * Needs the dev backend (demo accounts) and the app on TESTA_APP (default :4173; use http://localhost:5173/ for vite dev).
 *
 *   node .claude/skills/ui-review/shots.mjs <output dir> [filter]
 *
 * filter keeps only shots whose name contains it, e.g. "admin" or "exams".
 */
import { mkdir, writeFile } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { DESKTOP, PHONE, api, evaluate, pause, staffLogin, viewport, visit, withChrome } from '../../../tools/lib/chrome.mjs'

const OUTPUT = resolve(process.argv[2] ?? 'ui-review-shots')
const FILTER = process.argv[3] ?? ''
const MAX_SCREENS = 4

/** Capture the page screen by screen: the app scrolls inside <main>, not the window */
async function shoot(page, name) {
  if (!name.includes(FILTER)) return
  for (const [size, suffix] of [[DESKTOP, 'desk'], [PHONE, 'phone']]) {
    await viewport(page, size)
    await evaluate(page, `document.querySelector('main')?.scrollTo(0, 0); document.fonts.ready.then(() => true)`)
    for (let i = 0; i < MAX_SCREENS; i++) {
      await pause(400)
      const { data } = await page.send('Page.captureScreenshot', { format: 'png' })
      const file = `${name}-${suffix}${i ? `-${i + 1}` : ''}.png`
      await writeFile(join(OUTPUT, file), Buffer.from(data, 'base64'))
      process.stdout.write(`saved ${file}\n`)
      const more = await evaluate(
        page,
        `(() => { const m = document.querySelector('main'); if (!m || m.scrollTop + m.clientHeight >= m.scrollHeight - 4) return false; m.scrollBy(0, m.clientHeight - 80); return true })()`,
      )
      if (!more) break
    }
  }
  await viewport(page, DESKTOP)
}

async function tab(page, label) {
  await evaluate(page, `[...document.querySelectorAll('[role=tab]')].find((b) => b.textContent.trim() === ${JSON.stringify(label)})?.click()`)
  await pause(1200)
}

await mkdir(OUTPUT, { recursive: true })
await withChrome(async (browser) => {
  const page = await browser.newPage()
  await viewport(page)

  await visit(page, '/login', '准考證號')
  await shoot(page, 'login')
  await visit(page, '/help')
  await shoot(page, 'help')

  await staffLogin(page, 'teacher@testa.tw', 'testa-teacher')
  for (const [path, name] of [['/', 'teacher-home'], ['/banks', 'banks'], ['/grading', 'grading']]) {
    await visit(page, path)
    await pause(800)
    await shoot(page, name)
  }
  const banks = await api(page, '/banks')
  if (banks[0]) {
    await visit(page, `/banks/${banks[0].id}`)
    await pause(1000)
    await shoot(page, 'editor')
  }

  await staffLogin(page, 'officer@testa.tw', 'testa-officer')
  for (const [path, name] of [['/', 'officer-home'], ['/exams', 'exams'], ['/members', 'members']]) {
    await visit(page, path)
    await pause(800)
    await shoot(page, name)
  }
  const exams = await api(page, '/exams')
  if (exams[0]) {
    await visit(page, `/exams/${exams[0].id}`)
    await pause(1000)
    await shoot(page, 'exam-detail')
  }

  await staffLogin(page, 'admin@testa.tw', 'testa-admin', '/admin')
  await pause(1000)
  for (const [label, name] of [
    ['總覽', 'admin-overview'],
    ['使用者', 'admin-staff'],
    ['單位', 'admin-units'],
    ['操作紀錄', 'admin-audit'],
    ['備份', 'admin-backups'],
    ['緊急檢視', 'admin-emergency'],
    ['系統設定', 'admin-settings'],
  ]) {
    await tab(page, label)
    await shoot(page, name)
  }
})
