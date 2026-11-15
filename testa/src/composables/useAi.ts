import { ref } from 'vue'
import { api } from '@/lib/api'

/** Whether this server has AI question import turned on (a model endpoint is configured). Asked once per page load. */
const importEnabled = ref(false)
let asked = false

export function useAi() {
  if (!asked) {
    asked = true
    api<{ questionImport: boolean }>('/ai/status')
      .then((s) => (importEnabled.value = s.questionImport))
      .catch(() => (asked = false))
  }
  return { importEnabled }
}
