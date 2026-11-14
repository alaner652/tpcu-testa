import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, setUnauthorizedHandler } from '@/lib/api'

const respond = (status: number, body?: unknown) =>
  new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

let fetchMock: ReturnType<typeof vi.fn>
beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('document', { cookie: 'other=1; XSRF-TOKEN=abc%3D' })
})
afterEach(() => vi.unstubAllGlobals())

describe('api()', () => {
  it('GET 不帶 CSRF 標頭；寫入請求帶 cookie 裡的 token', async () => {
    fetchMock.mockImplementation(async () => respond(200, { ok: true }))
    await api('/banks')
    expect(fetchMock.mock.calls[0][1].headers['X-XSRF-TOKEN']).toBeUndefined()
    await api('/banks', { method: 'POST', body: { name: 'x' } })
    const [url, init] = fetchMock.mock.calls[1]
    expect(url).toBe('/api/banks')
    expect(init.headers['X-XSRF-TOKEN']).toBe('abc=')
    expect(init.body).toBe('{"name":"x"}')
  })

  it('還沒有 token 時先向後端要一次', async () => {
    vi.stubGlobal('document', { cookie: '' })
    fetchMock.mockImplementation(async () => respond(204))
    await api('/x', { method: 'DELETE' })
    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual(['/api/auth/csrf', '/api/x'])
  })

  it('錯誤轉成 ApiError，訊息用後端給的中文', async () => {
    fetchMock.mockResolvedValue(respond(409, { detail: '題庫已被別人修改。', code: 'version_conflict' }))
    const err = await api('/banks/1', { method: 'PUT', body: {} }).catch((e) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err).toMatchObject({ message: '題庫已被別人修改。', status: 409, code: 'version_conflict' })
  })

  it('後端沒回 JSON 時用預設訊息；連不上是 network', async () => {
    fetchMock.mockResolvedValueOnce(new Response('<html>', { status: 502 }))
    await expect(api('/x')).rejects.toMatchObject({ message: '伺服器暫時無法處理，請稍後再試。', status: 502 })
    fetchMock.mockRejectedValueOnce(new TypeError('failed'))
    await expect(api('/x')).rejects.toMatchObject({ status: 0, code: 'network' })
  })

  it('401 觸發被登出的處理；quiet401 不觸發', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    fetchMock.mockImplementation(async () => respond(401, { detail: '請先登入。' }))
    await api('/auth/me', { quiet401: true }).catch(() => {})
    expect(handler).not.toHaveBeenCalled()
    await api('/banks').catch(() => {})
    expect(handler).toHaveBeenCalledOnce()
  })
})
