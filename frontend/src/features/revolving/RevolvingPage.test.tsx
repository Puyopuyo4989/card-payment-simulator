import { render, screen } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'
import { simulate } from './api'
import RevolvingPage from './RevolvingPage'
import type { RevolvingSimulationResult, SimulationOutcome } from './types'

vi.mock('./api')

afterEach(() => {
  vi.resetAllMocks()
})

function success(result: RevolvingSimulationResult): SimulationOutcome {
  return { kind: 'success', result }
}

// spec US1 の1（100,000円・10,000円・15.0%）
const fifteenPercent: RevolvingSimulationResult = {
  paymentCount: 11,
  totalFee: 7497,
  totalPayment: 107497,
  schedule: [],
}

// spec US1 の2（100,000円・10,000円・0.0%）
const noFee: RevolvingSimulationResult = {
  paymentCount: 10,
  totalFee: 0,
  totalPayment: 100000,
  schedule: [],
}

async function fillAndSubmit(
  user: UserEvent,
  balance: string,
  monthlyPayment: string,
  annualRate: string,
) {
  for (const [name, value] of [
    ['利用残高（円）', balance],
    ['毎月の支払額（円）', monthlyPayment],
    ['実質年率（%）', annualRate],
  ]) {
    const input = screen.getByRole('textbox', { name })
    await user.clear(input)
    await user.type(input, value)
  }
  await user.click(screen.getByRole('button', { name: '計算する' }))
}

test('入力して実行すると、APIの結果を表示する', async () => {
  vi.mocked(simulate).mockResolvedValue(success(fifteenPercent))
  const user = userEvent.setup()
  render(<RevolvingPage />)

  await fillAndSubmit(user, '100000', '10000', '15.0')

  expect(simulate).toHaveBeenCalledWith({
    balance: '100000',
    monthlyPayment: '10000',
    annualRate: '15.0',
  })
  expect(await screen.findByText('11回')).toBeInTheDocument()
  expect(screen.getByText('7,497円')).toBeInTheDocument()
  expect(screen.getByText('107,497円')).toBeInTheDocument()
})

test('結果が出る前は、結果を表示しない', () => {
  render(<RevolvingPage />)

  expect(screen.queryByText('完済までの支払回数')).not.toBeInTheDocument()
})

test('入力を変えて再実行すると、前回の結果を消して、新しい結果だけを表示する', async () => {
  vi.mocked(simulate)
    .mockResolvedValueOnce(success(fifteenPercent))
    .mockResolvedValueOnce(success(noFee))
  const user = userEvent.setup()
  render(<RevolvingPage />)

  await fillAndSubmit(user, '100000', '10000', '15.0')
  await screen.findByText('11回')
  await fillAndSubmit(user, '100000', '10000', '0.0')

  expect(await screen.findByText('10回')).toBeInTheDocument()
  expect(screen.queryByText('11回')).not.toBeInTheDocument()
  expect(screen.queryByText('7,497円')).not.toBeInTheDocument()
})
