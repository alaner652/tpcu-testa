import { toast } from 'vue-sonner'
import type { QuestionType } from '@/types/question'
import { createMockBanks } from '@/data/mock-banks'
import { typeMeta } from '@/lib/question'
import { useBanks } from './useBanks'
import { useConfirm } from './useConfirm'

/** 帶有確認與操作回饋（提示、復原）的動作，供題卡、工具列與快捷鍵共用。 */
export function useQuestionActions() {
  const store = useBanks()
  const { confirm } = useConfirm()

  function add(type: QuestionType, afterId?: string | null) {
    store.add(type, afterId)
    toast.success(`已新增${typeMeta(type).label}題`)
  }

  async function remove(id: string) {
    const number = store.questions.value.findIndex((q) => q.id === id) + 1
    if (number === 0) return
    const ok = await confirm({
      title: `刪除第 ${number} 題？`,
      description: '題目、選項與答案設定會一併刪除。刪除後可以從提示訊息復原。',
      confirmLabel: '刪除',
      tone: 'danger',
    })
    if (!ok) return
    const result = store.remove(id)
    if (!result) return
    toast.success(`已刪除第 ${result.index + 1} 題`, {
      action: {
        label: '復原',
        onClick: () => {
          result.restore()
          toast.success('已復原題目')
        },
      },
    })
  }

  function duplicate(id: string) {
    store.duplicate(id)
    toast.success('已複製題目')
  }

  async function save() {
    if (await store.saveNow()) toast.success('已儲存')
    else toast.error('儲存沒有成功，請檢查網路連線。')
  }

  /** 開發用：把前端內建的示範題庫建立到伺服器上。 */
  async function importDemo() {
    try {
      for (const { name, subject, duration, questions } of createMockBanks()) {
        await store.createBank(undefined, { name, subject, duration, questions })
      }
      toast.success('已匯入示範題庫')
    } catch (e) {
      toast.error((e as Error).message)
    }
  }

  async function createBank() {
    try {
      const created = await store.createBank()
      toast.success('已建立新題庫')
      return created
    } catch (e) {
      toast.error((e as Error).message)
      return null
    }
  }

  return { add, remove, duplicate, save, importDemo, createBank }
}
