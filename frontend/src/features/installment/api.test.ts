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

test('200 のときは結果を返す', async () => {
  stubFetch(Response.json(result))

  await expect(
    simulate({ amount: '100000', paymentCount: '12', annualRate: '15.0' }),
  ).resolves.toEqual(result)
})
