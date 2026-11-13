import { computed, nextTick, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import type { Bank, Question, QuestionType, SaveState } from '@/types/question'
import { api, ApiError } from '@/lib/api'
import { cloneQuestion, createQuestion, issuesOf } from '@/lib/question'
import { useAuth } from './useAuth'

/**
 * 題庫狀態（教職員）。題庫存在後端：編輯後自動儲存（整份題庫送 PUT /api/banks/:id）。
 * 考生永遠拿不到題庫，考生的題目來自 useCandidate（去掉答案的試卷）。
 */
interface ApiBank extends Omit<Bank, 'id'> {
  id: number
  unitId: number
  unitName: string
  version: number
  editable: boolean
}

const banks = ref<Bank[]>([])
const bankId = ref('')
const activeId = ref<string | null>(null)
const loading = ref(true)
const loadError = ref('')
const saveState = ref<SaveState>('saved')
const savedAt = ref(new Date())

let hydrated = false
let mock = false
let debounceTimer: ReturnType<typeof setTimeout> | undefined
let retryTimer: ReturnType<typeof setTimeout> | undefined
let flushing: Promise<void> | null = null
/** 每份題庫上次成功存到伺服器的內容 */
const lastSaved = new Map<string, string>()
/** 不再自動儲存的題庫（版本衝突、已被刪除、沒有權限），等使用者重新整理 */
const blocked = new Set<string>()

const fromApi = (b: ApiBank): Bank => ({ ...b, id: String(b.id) })
const content = (b: Bank) => ({ name: b.name, subject: b.subject, duration: b.duration, questions: b.questions })
const snapshot = (b: Bank) => JSON.stringify(content(b))
const savable = (b: Bank) => b.editable !== false && !blocked.has(b.id)
const isDirty = () => !mock && banks.value.some((b) => savable(b) && snapshot(b) !== lastSaved.get(b.id))

async function flush(): Promise<void> {
  clearTimeout(debounceTimer)
  clearTimeout(retryTimer)
  if (flushing) {
    // 等目前這一輪存完，再把期間的新修改存一次
    await flushing
    return flush()
  }
  if (!isDirty()) {
    saveState.value = blocked.size ? 'error' : 'saved'
    return
  }
  saveState.value = 'saving'
  flushing = (async () => {
    let failed = false
    for (const b of banks.value) {
      if (!savable(b)) continue
      const body = snapshot(b)
      if (body === lastSaved.get(b.id)) continue
      try {
        const saved = await api<ApiBank>(`/banks/${b.id}`, { method: 'PUT', body: { version: b.version, ...JSON.parse(body) } })
        b.version = saved.version
        lastSaved.set(b.id, body)
      } catch (e) {
        failed = true
        const err = e as ApiError
        // 409 別人先改了、404 被刪了、403 權限被撤、400 資料有問題：重試也沒用，停下來告訴使用者
        if ([400, 403, 404, 409].includes(err.status)) {
          blocked.add(b.id)
          toast.error(`「${b.name || '未命名題庫'}」沒有儲存：${err.message}`, { duration: 12000 })
        }
      }
    }
    if (failed) {
      saveState.value = 'error'
      // 網路或伺服器的暫時問題：稍後自動重試
      if (isDirty()) retryTimer = setTimeout(flush, 5000)
    } else {
      savedAt.value = new Date()
      saveState.value = isDirty() ? 'dirty' : 'saved'
    }
  })()
  try {
    await flushing
  } finally {
    flushing = null
  }
  // 儲存期間又有新的修改
  if ((saveState.value as SaveState) === 'dirty') scheduleSave()
}

function scheduleSave() {
  if (mock) return
  clearTimeout(debounceTimer)
  if (!isDirty()) return
  if (saveState.value !== 'saving') saveState.value = 'dirty'
  debounceTimer = setTimeout(flush, 900)
}

watch(banks, () => hydrated && scheduleSave(), { deep: true })

// 還有沒存好的修改時，關閉分頁前提醒
window.addEventListener('beforeunload', (event) => {
  if (!mock && saveState.value !== 'saved' && isDirty()) event.preventDefault()
})

const bank = computed(() => banks.value.find((b) => b.id === bankId.value) ?? null)

const questions = computed<Question[]>({
  get: () => bank.value?.questions ?? [],
  set: (list) => {
    if (bank.value) bank.value.questions = list
  },
})

const activeIndex = computed(() => questions.value.findIndex((q) => q.id === activeId.value))
const activeQuestion = computed(() => questions.value[activeIndex.value] ?? null)
const totalPoints = computed(() => questions.value.reduce((sum, q) => sum + (q.points || 0), 0))
const incompleteCount = computed(() => questions.value.filter((q) => issuesOf(q).length > 0).length)
const reviewCount = computed(() => questions.value.filter((q) => q.review).length)

/** 我有出題權限的單位（可以在這些單位建立題庫）。 */
const authorUnits = computed(() => (useAuth().user.value?.units ?? []).filter((u) => u.roles.includes('AUTHOR')))

/** 依目前登入的身分載入題庫。登入、登出後都要呼叫。 */
async function load() {
  const { user, ready } = useAuth()
  hydrated = false
  loading.value = true
  loadError.value = ''
  clearTimeout(debounceTimer)
  clearTimeout(retryTimer)
  lastSaved.clear()
  blocked.clear()
  await ready

  mock = user.value?.role === 'student'
  if (!user.value || mock) {
    banks.value = []
  } else {
    try {
      banks.value = (await api<ApiBank[]>('/banks')).map(fromApi)
      banks.value.forEach((b) => lastSaved.set(b.id, snapshot(b)))
    } catch (e) {
      banks.value = []
      loadError.value = (e as Error).message
    }
  }
  if (!banks.value.some((b) => b.id === bankId.value)) bankId.value = banks.value[0]?.id ?? ''
  activeId.value = questions.value[0]?.id ?? null
  loading.value = false
  savedAt.value = new Date()
  saveState.value = 'saved'
  await nextTick()
  hydrated = true
}

function select(id: string | null) {
  activeId.value = id
}

function selectBank(id: string) {
  bankId.value = id
  activeId.value = questions.value[0]?.id ?? null
}

/** 建立題庫並切換過去。可以直接帶入內容（匯入示範題庫用）。 */
async function createBank(unitId = authorUnits.value[0]?.id, init?: Pick<Bank, 'name' | 'subject' | 'duration' | 'questions'>) {
  if (unitId == null) throw new Error('你沒有任何單位的出題權限，請洽單位負責人。')
  const created = fromApi(await api<ApiBank>('/banks', { method: 'POST', body: { unitId, ...init } }))
  lastSaved.set(created.id, snapshot(created))
  banks.value.push(created)
  selectBank(created.id)
  return created
}

async function removeBank(id: string) {
  await flush()
  await api(`/banks/${id}`, { method: 'DELETE' })
  lastSaved.delete(id)
  blocked.delete(id)
  banks.value = banks.value.filter((b) => b.id !== id)
  if (bankId.value === id) selectBank(banks.value[0]?.id ?? '')
}

function insert(list: Question[], afterId?: string | null) {
  if (!bank.value) return
  const at = afterId ? questions.value.findIndex((q) => q.id === afterId) + 1 : questions.value.length
  bank.value.questions.splice(at, 0, ...list)
  activeId.value = list[0]?.id ?? activeId.value
}

function add(type: QuestionType, afterId?: string | null) {
  const created = createQuestion(type)
  insert([created], afterId)
  return created
}

function duplicate(id: string) {
  const source = questions.value.find((q) => q.id === id)
  if (!source) return
  insert([cloneQuestion(source)], id)
}

/** 刪除題目並回傳復原函式，供「復原」提示使用。 */
function remove(id: string) {
  if (!bank.value) return null
  const list = bank.value.questions
  const index = list.findIndex((q) => q.id === id)
  if (index < 0) return null
  const [removed] = list.splice(index, 1)
  if (activeId.value === id) activeId.value = (list[index] ?? list[index - 1])?.id ?? null
  const owner = bank.value
  return {
    index,
    restore: () => {
      owner.questions.splice(Math.min(index, owner.questions.length), 0, removed)
      if (bankId.value === owner.id) activeId.value = removed.id
    },
  }
}

function move(id: string, delta: -1 | 1) {
  if (!bank.value) return
  const list = bank.value.questions
  const from = list.findIndex((q) => q.id === id)
  const to = from + delta
  if (from < 0 || to < 0 || to >= list.length) return
  const [item] = list.splice(from, 1)
  list.splice(to, 0, item)
}

function step(delta: -1 | 1) {
  const next = questions.value[activeIndex.value + delta]
  if (next) activeId.value = next.id
}

export function useBanks() {
  return {
    banks,
    bank,
    bankId,
    questions,
    activeId,
    activeIndex,
    activeQuestion,
    totalPoints,
    incompleteCount,
    reviewCount,
    authorUnits,
    loading,
    loadError,
    saveState,
    savedAt,
    load,
    select,
    selectBank,
    createBank,
    removeBank,
    add,
    insert,
    duplicate,
    remove,
    move,
    step,
    /** 立刻儲存；回傳是否全部存成功。 */
    saveNow: async () => {
      await flush()
      return saveState.value === 'saved'
    },
  }
}
