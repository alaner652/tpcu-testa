import { toast } from 'vue-sonner'
import type { QuestionType } from '@/types/question'
import { createMockBanks } from '@/data/mock-banks'
import { typeMeta } from '@/lib/question'
import { useBanks } from './useBanks'
import { useConfirm } from './useConfirm'

/** Actions with confirmation and feedback (toast, undo), shared by question cards, the toolbar and shortcuts. */
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

  /** Several questions picked on the question map; one undo brings them all back. */
  async function removeMany(ids: string[]) {
    if (!ids.length) return false
    const ok = await confirm({
      title: `刪除 ${ids.length} 題？`,
      description: '題目、選項與答案設定會一併刪除。刪除後可以從提示訊息復原。',
      confirmLabel: '刪除',
      tone: 'danger',
    })
    if (!ok) return false
    const removed = ids.map((id) => store.remove(id)).filter((r) => r !== null)
    toast.success(`已刪除 ${removed.length} 題`, {
      action: {
        label: '復原',
        onClick: () => {
          // Put back in reverse order so each lands where it was
          for (const r of [...removed].reverse()) r.restore()
          toast.success('已復原題目')
        },
      },
    })
    return true
  }

  function duplicate(id: string) {
    store.duplicate(id)
    toast.success('已複製題目')
  }

  async function save() {
    if (await store.saveNow()) toast.success('已儲存')
    else toast.error('儲存沒有成功，請檢查網路連線。')
  }

  /** Development only: create the built-in demo bank on the server. */
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

  return { add, remove, removeMany, duplicate, save, importDemo, createBank }
}
