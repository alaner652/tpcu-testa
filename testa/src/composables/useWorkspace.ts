import { ref } from 'vue'

/** 還沒接後端的示範資料：後台的「系統設定」。只存在記憶體，重新整理後回到初始狀態。 */
const settings = ref({ siteName: 'Testa 考試平台', aiImport: true, aiGrading: true })

/** 登入頁的示範考生：對應後端 dev profile 建立的示範考試（server/…/Bootstrap.java） */
export const DEMO_CANDIDATE = { ticket: 'A115-0001', code: '482915' }

export function useWorkspace() {
  return { settings }
}
