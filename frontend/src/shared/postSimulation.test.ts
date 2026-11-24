import { afterEach, expect, test, vi } from 'vitest'
import { postSimulation } from './postSimulation'

type Result = { total: number }
type Field = 'first' | 'second'

afterEach(() => {
  vi.unstubAllGlobals()
})

function stubFetch(response: Response) {
  const fetchMock = vi.fn().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const url = '/api/example-simulations'
const request = { first: '1', second: '2' }

test('渡された URL に、本文を JSON にして POST する', async () => {
  const fetchMock = stubFetch(Response.json({ total: 3 }))

  await postSimulation<typeof request, Result, Field>(url, request)

  expect(fetchMock).toHaveBeenCalledTimes(1)
  const [calledUrl, init] = fetchMock.mock.calls[0]
  expect(calledUrl).toBe(url)
  expect(init.method).toBe('POST')
  expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
  expect(init.body).toBe('{"first":"1","second":"2"}')
})

test('200 のときは結果を返す', async () => {
  stubFetch(Response.json({ total: 3 }))

  await expect(
    postSimulation<typeof request, Result, Field>(url, request),
  ).resolves.toEqual({ kind: 'success', result: { total: 3 } })
})

test('400 で errors があるときは、項目ごとのエラーを返す', async () => {
  const errors = [{ field: 'second', message: '2つ目を入力してください' }]
  stubFetch(
    Response.json(
      {
        type: 'about:blank',
        title: '入力に誤りがあります',
        status: 400,
        errors,
      },
      { status: 400 },
    ),
  )

  await expect(
    postSimulation<typeof request, Result, Field>(url, request),
  ).resolves.toEqual({ kind: 'inputError', errors })
})

test('errors がない 400 のときは、予期しないエラーを返す', async () => {
  stubFetch(
    Response.json(
      {
        type: 'about:blank',
        title: 'リクエストの形式が正しくありません',
        status: 400,
      },
      { status: 400 },
    ),
  )

  await expect(
    postSimulation<typeof request, Result, Field>(url, request),
  ).resolves.toEqual({ kind: 'unexpectedError' })
})

test('500 のときは、予期しないエラーを返す', async () => {
  stubFetch(new Response('Internal Server Error', { status: 500 }))

  await expect(
    postSimulation<typeof request, Result, Field>(url, request),
  ).resolves.toEqual({ kind: 'unexpectedError' })
})

test('通信に失敗したときは、予期しないエラーを返す', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed')))

  await expect(
    postSimulation<typeof request, Result, Field>(url, request),
  ).resolves.toEqual({ kind: 'unexpectedError' })
})
