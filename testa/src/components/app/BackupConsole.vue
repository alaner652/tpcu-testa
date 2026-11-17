<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { api } from '@/lib/api'
import { bytes, type BackupSchedule, type BackupState } from '@/lib/backups'

/**
 * A small console for administrators who prefer typing. It is not a shell: each command maps to the same API calls
 * as the buttons above, with the same confirmation dialogs, so it cannot do anything the page cannot.
 */
type BackupFile = BackupState['items'][number]

const props = defineProps<{
  backups: BackupState
  actions: {
    refresh: () => Promise<void>
    start: () => Promise<void>
    download: (b: BackupFile) => Promise<void>
    remove: (b: BackupFile) => Promise<void>
    restore: (b: BackupFile) => Promise<void>
    schedule: (patch: Partial<BackupSchedule>) => Promise<boolean>
  }
  describe: (s: BackupSchedule) => string
}>()

interface Line {
  text: string
  tone?: 'in' | 'ok' | 'err' | 'dim'
}

const HELP = `可用指令（<n> 是 ls 列出的編號，1 是最新的一份，也可以打檔名）：
  status              備份狀態
  ls [all]            列出備份
  backup              立即備份
  check <n>           檢查備份檔是否完整
  download <n>        下載備份
  rm <n>              刪除備份（會再確認）
  restore <n>         還原到這份備份（會再確認）
  log                 最近的備份紀錄
  df                  主機空間
  schedule            自動備份設定
  schedule on|off     開啟或關閉自動備份
  schedule every 6|12|24|168   每幾小時一次（24 = 每天，168 = 每週）
  schedule at 0-23    幾點開始
  schedule keep <天>  保留幾天
  clear               清除畫面
↑ ↓ 可以叫出之前打過的指令。`

const lines = ref<Line[]>([{ text: '輸入 help 查看可用的指令。', tone: 'dim' }])
const input = ref('')
const busy = ref(false)
const past: string[] = []
let cursor = 0
const screen = ref<HTMLElement | null>(null)
const field = ref<HTMLInputElement | null>(null)

const print = (text: string, tone?: Line['tone']) => lines.value.push({ text, tone })
const time = (iso: string) =>
  new Date(iso).toLocaleString('zh-TW', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })
const pad = (text: string, width: number) => text + ' '.repeat(Math.max(1, width - [...text].length))

function pick(arg: string | undefined): BackupFile | null {
  const items = props.backups.items
  if (!arg) {
    print('請指定哪一份，例如 check 1。', 'err')
    return null
  }
  const found = /^\d+$/.test(arg) ? items[Number(arg) - 1] : items.find((b) => b.name === arg)
  if (!found) print(`找不到「${arg}」，先用 ls 看編號。`, 'err')
  return found ?? null
}

function list(all: boolean) {
  const items = all ? props.backups.items : props.backups.items.slice(0, 10)
  if (!items.length) return print('目前沒有備份。', 'dim')
  items.forEach((b, i) => print(`${pad(String(i + 1), 4)}${pad(time(b.at), 20)}${pad(bytes(b.size), 10)}${b.name}`))
  if (!all && props.backups.items.length > 10) print(`還有 ${props.backups.items.length - 10} 份，用 ls all 看全部。`, 'dim')
}

async function schedule(args: string[]) {
  const s = props.backups.schedule
  const [what, value] = args
  if (!what) {
    print(s.enabled ? `自動備份：${props.describe(s)}` : '自動備份：已關閉')
    print(`保留：${s.keepDays} 天`)
    if (props.backups.nextRunAt) print(`下次：${time(props.backups.nextRunAt)}`)
    return
  }
  const n = Number(value)
  let patch: Partial<BackupSchedule> | null = null
  if (what === 'on' || what === 'off') patch = { enabled: what === 'on' }
  else if (what === 'every' && [6, 12, 24, 168].includes(n)) patch = { everyHours: n }
  else if (what === 'at' && Number.isInteger(n) && n >= 0 && n <= 23) patch = { hour: n }
  else if (what === 'keep' && Number.isInteger(n) && n >= 1 && n <= 365) patch = { keepDays: n }
  if (!patch) return print('看不懂這個設定，輸入 help 查看寫法。', 'err')
  if (await props.actions.schedule(patch)) print('已儲存。', 'ok')
}

async function run(command: string) {
  const [name, ...args] = command.trim().split(/\s+/)
  switch (name) {
    case 'help':
      return print(HELP)
    case 'clear':
      lines.value = []
      return
    case 'status': {
      await props.actions.refresh()
      print(props.backups.running ? '正在執行備份操作…' : props.backups.lastFailed ? '上次的備份操作失敗。' : '待命中。', props.backups.lastFailed ? 'err' : undefined)
      print(`共 ${props.backups.items.length} 份，${bytes(props.backups.totalSize)}；最新一份 ${props.backups.items[0] ? time(props.backups.items[0].at) : '—'}`)
      return schedule([])
    }
    case 'ls':
      await props.actions.refresh()
      return list(args[0] === 'all')
    case 'backup':
      print('開始備份，完成後會通知。', 'dim')
      return props.actions.start()
    case 'check': {
      const file = pick(args[0])
      if (!file) return
      print(`正在檢查 ${file.name}…`, 'dim')
      const result = await api<{ ok: boolean; message: string }>(`/admin/backups/${encodeURIComponent(file.name)}/check`)
      return print(result.message, result.ok ? 'ok' : 'err')
    }
    case 'download': {
      const file = pick(args[0])
      return file && props.actions.download(file)
    }
    case 'rm': {
      const file = pick(args[0])
      return file && props.actions.remove(file)
    }
    case 'restore': {
      const file = pick(args[0])
      return file && props.actions.restore(file)
    }
    case 'log': {
      await props.actions.refresh()
      const KIND: Record<string, string> = { auto: '自動備份', manual: '手動備份', delete: '刪除', restore: '還原' }
      if (!props.backups.history.length) return print('還沒有紀錄。', 'dim')
      for (const r of props.backups.history) print(`${pad(time(r.at), 20)}${pad(KIND[r.kind] ?? r.kind, 10)}${pad(r.ok ? '成功' : '失敗', 6)}${r.name}`, r.ok ? undefined : 'err')
      return
    }
    case 'df':
      await props.actions.refresh()
      if (!props.backups.disk) return print('讀不到主機空間。', 'err')
      return print(`剩餘 ${bytes(props.backups.disk.free)}，共 ${bytes(props.backups.disk.total)}；備份占 ${bytes(props.backups.totalSize)}`)
    case 'schedule':
      return schedule(args)
    default:
      return print(`沒有「${name}」這個指令，輸入 help 查看。`, 'err')
  }
}

async function submit() {
  const command = input.value.trim()
  input.value = ''
  if (!command || busy.value) return
  past.push(command)
  cursor = past.length
  print(`> ${command}`, 'in')
  busy.value = true
  try {
    await run(command)
  } catch (e) {
    print((e as Error).message, 'err')
  } finally {
    busy.value = false
    await nextTick()
    screen.value?.scrollTo({ top: screen.value.scrollHeight })
    field.value?.focus()
  }
}

function recall(step: number) {
  if (!past.length) return
  cursor = Math.min(past.length, Math.max(0, cursor + step))
  input.value = past[cursor] ?? ''
}
</script>

<template>
  <div class="bg-[#15191a] font-mono text-[13px] leading-relaxed text-[#d7ddd9]" @click="field?.focus()">
    <div ref="screen" class="max-h-80 overflow-y-auto px-5 pt-3 sm:px-6">
      <pre
        v-for="(line, i) in lines"
        :key="i"
        class="whitespace-pre-wrap"
        :class="{ 'text-[#8fd6b8]': line.tone === 'ok', 'text-[#f09a8c]': line.tone === 'err', 'text-[#8a948f]': line.tone === 'dim', 'text-white': line.tone === 'in' }"
      >{{ line.text }}</pre>
    </div>
    <form class="flex items-center gap-2 px-5 pt-1 pb-3 sm:px-6" @submit.prevent="submit">
      <span class="text-[#8fd6b8]" aria-hidden="true">&gt;</span>
      <input
        ref="field"
        v-model="input"
        class="min-w-0 flex-1 bg-transparent outline-none placeholder:text-[#5f6864]"
        aria-label="備份指令"
        autocomplete="off"
        autocapitalize="off"
        spellcheck="false"
        :placeholder="busy ? '執行中…' : 'help'"
        @keydown.up.prevent="recall(-1)"
        @keydown.down.prevent="recall(1)"
      />
    </form>
  </div>
</template>
