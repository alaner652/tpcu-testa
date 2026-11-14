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
  it('GET sends no CSRF header; writes send the token from the cookie', async () => {
    fetchMock.mockImplementation(async () => respond(200, { ok: true }))
    await api('/banks')
    expect(fetchMock.mock.calls[0][1].headers['X-XSRF-TOKEN']).toBeUndefined()
    await api('/banks', { method: 'POST', body: { name: 'x' } })
    const [url, init] = fetchMock.mock.calls[1]
    expect(url).toBe('/api/banks')
    expect(init.headers['X-XSRF-TOKEN']).toBe('abc=')
    expect(init.body).toBe('{"name":"x"}')
  })

  it('fetches a token from the backend first when there is none', async () => {
    vi.stubGlobal('document', { cookie: '' })
    fetchMock.mockImplementation(async () => respond(204))
    await api('/x', { method: 'DELETE' })
    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual(['/api/auth/csrf', '/api/x'])
  })

  it('turns errors into ApiError with the backend message', async () => {
    fetchMock.mockResolvedValue(respond(409, { detail: '題庫已被別人修改。', code: 'version_conflict' }))
    const err = await api('/banks/1', { method: 'PUT', body: {} }).catch((e) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err).toMatchObject({ message: '題庫已被別人修改。', status: 409, code: 'version_conflict' })
  })

  it('uses a default message for non-JSON responses; unreachable is network', async () => {
    fetchMock.mockResolvedValueOnce(new Response('<html>', { status: 502 }))
    await expect(api('/x')).rejects.toMatchObject({ message: '伺服器暫時無法處理，請稍後再試。', status: 502 })
    fetchMock.mockRejectedValueOnce(new TypeError('failed'))
    await expect(api('/x')).rejects.toMatchObject({ status: 0, code: 'network' })
  })

  it('401 triggers the signed-out handler; quiet401 does not', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    fetchMock.mockImplementation(async () => respond(401, { detail: '請先登入。' }))
    await api('/auth/me', { quiet401: true }).catch(() => {})
    expect(handler).not.toHaveBeenCalled()
    await api('/banks').catch(() => {})
    expect(handler).toHaveBeenCalledOnce()
  })
})
