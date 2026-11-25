import { afterEach, describe, expect, test, vi } from 'vitest'
import { changeStatus, getDetail, list, register } from './api'
import type { ApplicationDetail } from './types'

// 値はすべて架空のもの
const detail: ApplicationDetail = {
  applicationNumber: 'A00000001',
  nameKanji: 'テスト 太郎',
  nameKana: 'テスト　タロウ',
  birthDate: '1990-04-01',
  annualIncome: 450,
  employerName: 'テスト株式会社',
  phoneNumber: '0000000000',
  status: 'APPLIED',
  appliedAt: '2026-11-25T01:30:00Z',
  updatedAt: '2026-11-25T01:30:00Z',
  version: 0,
  history: [],
}

const registration = {
  nameKanji: 'テスト 太郎',
  nameKana: 'テスト　タロウ',
  birthDate: '1990-04-01',
  annualIncome: '450',
  employerName: 'テスト株式会社',
  phoneNumber: '0000000000',
}

const statusChange = {
  toStatus: 'UNDER_REVIEW',
  staffName: 'テスト 担当A',
  rejectionReason: '',
  version: '0',
}

afterEach(() => {
  vi.unstubAllGlobals()
})

function stubFetch(response: Response) {
  const fetchMock = vi.fn().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function problem(status: number, extra: object = {}) {
  return Response.json(
    { type: 'about:blank', title: 'テスト', status, ...extra },
    { status },
  )
}

describe('呼び出し方（contracts の URL・メソッド・本文）', () => {
  test('register は6項目を文字列のまま POST /api/card-applications に送る', async () => {
    const fetchMock = stubFetch(Response.json(detail, { status: 201 }))

    await register(registration)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/card-applications')
    expect(init.method).toBe('POST')
    expect(init.headers).toEqual({ 'Content-Type': 'application/json' })
    expect(init.body).toBe(JSON.stringify(registration))
  })

  test('list は状態を指定しないとき、パラメーターを付けずに GET する', async () => {
    const fetchMock = stubFetch(Response.json({ items: [] }))

    await list()
    await list('')

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/card-applications',
      '/api/card-applications',
    ])
    expect(fetchMock.mock.calls[0][1]?.method ?? 'GET').toBe('GET')
  })

  test('list は状態を指定したとき、status を付けて GET する', async () => {
    const fetchMock = stubFetch(Response.json({ items: [] }))

    await list('UNDER_REVIEW')

    expect(fetchMock.mock.calls[0][0]).toBe(
      '/api/card-applications?status=UNDER_REVIEW',
    )
  })

  // 【Should】キーワード検索とページ分け（contracts 6）
  test('list はキーワードとページを付けて GET し、キーワードはエンコードする', async () => {
    const fetchMock = stubFetch(Response.json({ items: [] }))

    await list('APPLIED', { keyword: 'タロウ&x', page: 2 })

    expect(fetchMock.mock.calls[0][0]).toBe(
      '/api/card-applications?status=APPLIED&keyword=%E3%82%BF%E3%83%AD%E3%82%A6%26x&page=2',
    )
  })

  test('list はキーワードが空ならキーワードを付けない', async () => {
    const fetchMock = stubFetch(Response.json({ items: [] }))

    await list('', { keyword: '', page: 1 })

    expect(fetchMock.mock.calls[0][0]).toBe('/api/card-applications?page=1')
  })

  test('getDetail は申込番号の詳細を GET する', async () => {
    const fetchMock = stubFetch(Response.json(detail))

    await getDetail('A00000001')

    expect(fetchMock.mock.calls[0][0]).toBe('/api/card-applications/A00000001')
    expect(fetchMock.mock.calls[0][1]?.method ?? 'GET').toBe('GET')
  })

  test('getDetail は申込番号を URL の一部として安全に埋め込む', async () => {
    const fetchMock = stubFetch(problem(404))

    await getDetail('a/b?c')

    expect(fetchMock.mock.calls[0][0]).toBe('/api/card-applications/a%2Fb%3Fc')
  })

  test('changeStatus は4項目を文字列のまま status-changes に POST する', async () => {
    const fetchMock = stubFetch(Response.json(detail))

    await changeStatus('A00000001', statusChange)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/card-applications/A00000001/status-changes')
    expect(init.method).toBe('POST')
    expect(init.headers).toEqual({ 'Content-Type': 'application/json' })
    expect(init.body).toBe(JSON.stringify(statusChange))
  })
})

describe('結果の種類', () => {
  test('201 は success（登録した詳細）', async () => {
    stubFetch(Response.json(detail, { status: 201 }))

    await expect(register(registration)).resolves.toEqual({
      kind: 'success',
      result: detail,
    })
  })

  test('200 は success', async () => {
    stubFetch(Response.json({ items: [] }))

    await expect(list()).resolves.toEqual({
      kind: 'success',
      result: { items: [] },
    })
  })

  test('errors のある 400 は inputError', async () => {
    const errors = [
      { field: 'staffName', message: '担当者名を入力してください' },
    ]
    stubFetch(problem(400, { errors }))

    await expect(changeStatus('A00000001', statusChange)).resolves.toEqual({
      kind: 'inputError',
      errors,
    })
  })

  test('404 は notFound', async () => {
    stubFetch(problem(404, { title: '申込が見つかりません' }))

    await expect(getDetail('A99999999')).resolves.toEqual({ kind: 'notFound' })
  })

  test('409 は conflict で、title と detail を持つ', async () => {
    stubFetch(
      problem(409, {
        title: '本文のtitle',
        detail: '本文のdetail',
      }),
    )

    await expect(changeStatus('A00000001', statusChange)).resolves.toEqual({
      kind: 'conflict',
      title: '本文のtitle',
      detail: '本文のdetail',
    })
  })

  test('409 で title・detail がないときも、決まった文で conflict を返す', async () => {
    stubFetch(Response.json({ status: 409 }, { status: 409 }))

    await expect(changeStatus('A00000001', statusChange)).resolves.toEqual({
      kind: 'conflict',
      title: 'ほかの担当者が先に状態を変えました',
      detail: '最新の状態を確かめてから、もう一度操作してください',
    })
  })

  test('errors のない 400 は unexpectedError', async () => {
    stubFetch(problem(400))

    await expect(register(registration)).resolves.toEqual({
      kind: 'unexpectedError',
    })
  })

  test('500 は unexpectedError', async () => {
    stubFetch(new Response('Internal Server Error', { status: 500 }))

    await expect(list()).resolves.toEqual({ kind: 'unexpectedError' })
  })

  test('通信に失敗したときは unexpectedError', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed')))

    await expect(getDetail('A00000001')).resolves.toEqual({
      kind: 'unexpectedError',
    })
  })
})
