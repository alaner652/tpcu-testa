import { ref } from 'vue'

/** Demo data without a backend: the admin "System settings". In memory only; reloading resets it. */
const settings = ref({ siteName: 'Testa 考試平台', aiImport: true, aiGrading: true })

/** Demo candidate on the login page, matching the demo exam from the backend dev profile (server/.../Bootstrap.java) */
export const DEMO_CANDIDATE = { ticket: 'A115-0001', code: '482915' }

export function useWorkspace() {
  return { settings }
}
