import type { BadgeTone } from '@/components/ui/Badge.vue'

/** GET /admin/backups */
export interface BackupState {
  configured: boolean
  triggerSupported: boolean
  running: boolean
  lastFailed: boolean
  maintenance: boolean
  operation: string
  keepDays: number
  items: { name: string; size: number; at: string }[]
  totalSize: number
  disk: Disk | null
  nextRunAt: string | null
  /** Newest first: auto, manual, delete or restore */
  history: { at: string; kind: string; ok: boolean; name: string }[]
  lastDownloadAt: string | null
  schedule: BackupSchedule
  keepDaysDefault: number
}
/** everyHours: 6, 12, 24 or 168; weekday 1 = Monday */
export interface BackupSchedule {
  enabled: boolean
  everyHours: number
  hour: number
  weekday: number
  keepDays: number
}
export interface Disk {
  free: number
  total: number
}

export const bytes = (n: number) => {
  if (n < 1024 * 1024) return `${Math.max(1, Math.round(n / 1024))} KB`
  if (n < 1024 ** 3) return `${(n / 1024 ** 2).toFixed(1)} MB`
  return `${(n / 1024 ** 3).toFixed(1)} GB`
}

/** Less than a tenth of the disk left */
export const diskLow = (d: Disk) => d.total > 0 && d.free / d.total < 0.1

/** Two hours past the interval without a new backup means the schedule stopped */
const staleMs = (s: BackupSchedule) => (s.everyHours + 2) * 3600 * 1000

/** One line about the backups, for the backup tab and the overview. */
export function backupHealth(b: BackupState): { tone: BadgeTone; label: string; advice: string | null } {
  if (!b.configured) return { tone: 'danger', label: '無法讀取備份狀態', advice: '請聯絡系統維護人員檢查備份設定。' }
  if (!b.items.length) {
    return b.lastFailed
      ? { tone: 'danger', label: '備份建立失敗', advice: '請再試一次；如果仍然失敗，請聯絡系統維護人員。' }
      : { tone: 'warn', label: '還沒有任何備份', advice: '如果系統已運作超過一天，請聯絡系統維護人員。' }
  }
  if (b.lastFailed) return { tone: 'danger', label: '上次備份操作失敗', advice: '請再試一次；如果仍然失敗，請查看伺服器日誌。' }
  if (!b.schedule.enabled) return { tone: 'warn', label: '自動備份已關閉', advice: '只有按「立即備份」才會備份。' }
  if (Date.now() - new Date(b.items[0]!.at).getTime() > staleMs(b.schedule)) {
    return { tone: 'danger', label: '需要注意', advice: '超過預定時間沒有新的備份，請聯絡系統維護人員。' }
  }
  if (b.disk && diskLow(b.disk)) {
    return { tone: 'warn', label: '空間快不夠了', advice: '主機剩餘空間不到一成。請減少備份保留天數，或請維護人員擴充硬碟。' }
  }
  return { tone: 'done', label: '備份正常', advice: null }
}
