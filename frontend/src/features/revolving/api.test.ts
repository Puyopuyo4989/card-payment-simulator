import { afterEach, expect, test, vi } from 'vitest'
import { simulate } from './api'
import type { RevolvingSimulationResult } from './types'

// spec US1 の1（100,000円・10,000円・15.0%）。返済表は省略
const result: RevolvingSimulationResult = {
  paymentCount: 11,
  totalFee: 7497,
  totalPayment: 107497,
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

const request = {
  balance: '100000',
  monthlyPayment: '10000',
  annualRate: '15.0',
}

test('3項目を文字列のまま JSON の本文にして POST /api/revolving-simulations を呼ぶ', async () => {
  const fetchMock = stubFetch(Response.json(result))

  await simulate(request)

  expect(fetchMock).toHaveBeenCalledTimes(1)
  const [url, init] = fetchMock.mock.calls[0]
  expect(url).toBe('/api/revolving-simulations')
  expect(init.method).toBe('POST')
  expect(init.body).toBe(
    '{"balance":"100000","monthlyPayment":"10000","annualRate":"15.0"}',
  )
})

test('200 のときは結果を返す', async () => {
  stubFetch(Response.json(result))

  await expect(simulate(request)).resolves.toEqual({
    kind: 'success',
    result,
  })
})
