/**
 * 後端 API 的唯一出入口。
 * - 登入狀態靠伺服器端 Session（HttpOnly cookie），前端不保存任何 token。
 * - 寫入請求要帶 CSRF token：後端放在 XSRF-TOKEN cookie，這裡讀出來放進 X-XSRF-TOKEN 標頭。
 * - 錯誤一律轉成 ApiError，message 是可以直接顯示給使用者的中文。
 */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
    readonly errors?: Record<string, string>,
  ) {
    super(message)
  }
}

let onUnauthorized: (() => void) | null = null
/** Session 過期或被強制登出時要做的事（由 useAuth 註冊）。 */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

const xsrfCookie = () => {
  const m = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)
  return m ? decodeURIComponent(m[1]) : ''
}

async function csrfToken() {
  if (!xsrfCookie()) await fetch('/api/auth/csrf', { credentials: 'same-origin' })
  return xsrfCookie()
}

interface Options {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  /** 401 時不要觸發全域的「被登出」處理（登入、開機檢查用） */
  quiet401?: boolean
}

export async function api<T = void>(path: string, { method = 'GET', body, quiet401 }: Options = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  let res: Response
  try {
    if (method !== 'GET') headers['X-XSRF-TOKEN'] = await csrfToken()
    // FormData（上傳檔案）讓瀏覽器自己帶 multipart 的 Content-Type
    const form = body instanceof FormData
    if (body !== undefined && !form) headers['Content-Type'] = 'application/json'
    res = await fetch(`/api${path}`, {
      method,
      headers,
      credentials: 'same-origin',
      body: body === undefined ? undefined : form ? (body as FormData) : JSON.stringify(body),
    })
  } catch {
    throw new ApiError('無法連線到伺服器，請檢查網路後再試一次。', 0, 'network')
  }

  if (res.ok) {
    if (res.status === 204) return undefined as T
    return (await res.json()) as T
  }

  let problem: { detail?: string; code?: string; errors?: Record<string, string> } = {}
  try {
    problem = await res.json()
  } catch {
    // 不是 JSON（例如後端沒啟動時代理回的 502）
  }
  if (res.status === 401 && !quiet401) onUnauthorized?.()
  const fallback =
    res.status === 429
      ? '現在同時使用的人比較多，請等幾秒再試一次。'
      : res.status >= 500
        ? '伺服器暫時無法處理，請稍後再試。'
        : '操作沒有成功，請再試一次。'
  throw new ApiError(problem.detail ?? fallback, res.status, problem.code, problem.errors)
}
