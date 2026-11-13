import { reactive } from 'vue'

export interface ConfirmOptions {
  title: string
  description?: string
  confirmLabel?: string
  cancelLabel?: string
  /** danger：刪除、清除等無法輕易回復的動作 */
  tone?: 'default' | 'danger'
}

const state = reactive({
  open: false,
  options: { title: '' } as ConfirmOptions,
})

let resolver: ((value: boolean) => void) | null = null

function settle(value: boolean) {
  state.open = false
  resolver?.(value)
  resolver = null
}

/**
 * 需要使用者確認的動作統一走這裡：`if (await confirm({...})) { ... }`。
 * 對話框本體是 App 內唯一的一個 <ConfirmDialog />。
 */
export function useConfirm() {
  function confirm(options: ConfirmOptions) {
    resolver?.(false)
    state.options = options
    state.open = true
    return new Promise<boolean>((resolve) => (resolver = resolve))
  }
  return { state, confirm, settle }
}
