import { afterEach, expect, test, vi } from 'vitest'
import { simulate } from './api'
import type { InstallmentSimulationResult } from './types'

const result: InstallmentSimulationResult = {
  totalFee: 8309,
  totalPayment: 108309,
  firstPayment: 9034,
  subsequentPayment: 9025,
  schedule: [],
}

afterEach(() => {
  vi.unstubAllGlobals()
})

function stubFetch(response: Response) {
  const fetchMock = vi.fn().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

test('3項目を文字列のまま JSON の本文にして POST する', async () => {
  const fetchMock = stubFetch(Response.json(result))

  await simulate({ amount: '100000', paymentCount: '12', annualRate: '15.0' })

  expect(fetchMock).toHaveBeenCalledTimes(1)
  const [url, init] = fetchMock.mock.calls[0]
  expect(url).toBe('/api/installment-simulations')
  expect(init.method).toBe('POST')
  expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
  expect(init.body).toBe(
    '{"amount":"100000","paymentCount":"12","annualRate":"15.0"}',
  )
})

const request = { amount: '100000', paymentCount: '12', annualRate: '15.0' }

test('200 のときは結果を返す', async () => {
  stubFetch(Response.json(result))

  await expect(simulate(request)).resolves.toEqual({
    kind: 'success',
    result,
  })
})

test('400 で errors があるときは、項目ごとのエラーを返す', async () => {
  const errors = [
    { field: 'amount', message: '利用金額を入力してください' },
    {
      field: 'annualRate',
      message:
        '実質年率は半角数字で、小数第1位までで入力してください（例：12.5）',
    },
  ]
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

  await expect(simulate(request)).resolves.toEqual({
    kind: 'inputError',
    errors,
  })
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

  await expect(simulate(request)).resolves.toEqual({
    kind: 'unexpectedError',
  })
})

test('500 のときは、予期しないエラーを返す', async () => {
  stubFetch(new Response('Internal Server Error', { status: 500 }))

  await expect(simulate(request)).resolves.toEqual({
    kind: 'unexpectedError',
  })
})

test('通信に失敗したときは、予期しないエラーを返す', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed')))

  await expect(simulate(request)).resolves.toEqual({
    kind: 'unexpectedError',
  })
})
