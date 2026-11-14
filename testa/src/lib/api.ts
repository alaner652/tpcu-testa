/**
 * The only way to call the backend API.
 * - Sign-in state lives in a server-side session (HttpOnly cookie); the frontend stores no token.
 * - Writes carry the CSRF token: the backend sets the XSRF-TOKEN cookie and this sends it in the X-XSRF-TOKEN header.
 * - Every error becomes an ApiError whose message can be shown to the user as is.
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
/** What to do when the session expires or the user is forced out (registered by useAuth). */
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
  /** Do not trigger the global signed-out handling on 401 (sign-in, start-up check) */
  quiet401?: boolean
}

export async function api<T = void>(path: string, { method = 'GET', body, quiet401 }: Options = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  let res: Response
  try {
    if (method !== 'GET') headers['X-XSRF-TOKEN'] = await csrfToken()
    // FormData (file upload): let the browser set the multipart Content-Type
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
    // Not JSON (e.g. a 502 from the proxy while the backend is down)
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
