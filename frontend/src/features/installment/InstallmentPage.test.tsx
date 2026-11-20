import { render, screen, within } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'
import { simulate } from './api'
import InstallmentPage from './InstallmentPage'
import type { InstallmentSimulationResult } from './types'

vi.mock('./api')

afterEach(() => {
  vi.resetAllMocks()
})

// spec US1 の1（100,000円・12回・15.0%）
const twelveTimes: InstallmentSimulationResult = {
  totalFee: 8309,
  totalPayment: 108309,
  firstPayment: 9034,
  subsequentPayment: 9025,
  schedule: [],
}

// spec US2 の受け入れシナリオ5（100,000円・12回・15.0%）。返済表は1・2・12回目だけ
const twelveTimesWithSchedule: InstallmentSimulationResult = {
  ...twelveTimes,
  schedule: [
    {
      number: 1,
      payment: 9034,
      principal: 7784,
      fee: 1250,
      balanceAfter: 92216,
    },
    {
      number: 2,
      payment: 9025,
      principal: 7873,
      fee: 1152,
      balanceAfter: 84343,
    },
    { number: 12, payment: 9025, principal: 8909, fee: 116, balanceAfter: 0 },
  ],
}

// spec US1 の3（100,000円・3回・0.0%）
const threeTimesNoFee: InstallmentSimulationResult = {
  totalFee: 0,
  totalPayment: 100000,
  firstPayment: 33334,
  subsequentPayment: 33333,
  schedule: [],
}

async function fillAndSubmit(
  user: UserEvent,
  amount: string,
  paymentCount: string,
  annualRate: string,
) {
  const amountInput = screen.getByRole('textbox', { name: '利用金額（円）' })
  const annualRateInput = screen.getByRole('textbox', { name: '実質年率（%）' })
  await user.clear(amountInput)
  await user.type(amountInput, amount)
  await user.selectOptions(
    screen.getByRole('combobox', { name: '支払回数' }),
    paymentCount,
  )
  await user.clear(annualRateInput)
  await user.type(annualRateInput, annualRate)
  await user.click(screen.getByRole('button', { name: '計算する' }))
}

test('入力して実行すると、APIの結果を3桁区切りで表示する', async () => {
  const user = userEvent.setup()
  vi.mocked(simulate).mockResolvedValue(twelveTimes)
  render(<InstallmentPage />)

  await fillAndSubmit(user, '100000', '12', '15.0')

  expect(simulate).toHaveBeenCalledWith({
    amount: '100000',
    paymentCount: '12',
    annualRate: '15.0',
  })
  expect(await screen.findByText('8,309円')).toBeInTheDocument()
  expect(screen.getByText('108,309円')).toBeInTheDocument()
  expect(screen.getByText('9,034円')).toBeInTheDocument()
  expect(screen.getByText('9,025円')).toBeInTheDocument()
})

test('結果が出る前は、結果を表示しない', () => {
  render(<InstallmentPage />)

  expect(screen.queryByText('手数料の合計')).not.toBeInTheDocument()
})

test('入力を変えて再実行すると、前回の結果を消して、新しい結果だけを表示する', async () => {
  const user = userEvent.setup()
  vi.mocked(simulate).mockResolvedValueOnce(twelveTimes)
  render(<InstallmentPage />)
  await fillAndSubmit(user, '100000', '12', '15.0')
  expect(await screen.findByText('8,309円')).toBeInTheDocument()

  // 2回目の応答はすぐに返さず、応答の前に前回の結果が消えていることを確かめる
  let respond!: (result: InstallmentSimulationResult) => void
  vi.mocked(simulate).mockReturnValueOnce(
    new Promise((resolve) => {
      respond = resolve
    }),
  )
  await fillAndSubmit(user, '100000', '3', '0.0')
  expect(screen.queryByText('8,309円')).not.toBeInTheDocument()

  respond(threeTimesNoFee)
  expect(await screen.findByText('33,334円')).toBeInTheDocument()
  expect(screen.getByText('0円')).toBeInTheDocument()
  expect(screen.getByText('100,000円')).toBeInTheDocument()
  expect(screen.getByText('33,333円')).toBeInTheDocument()
  expect(screen.queryByText('108,309円')).not.toBeInTheDocument()
  expect(screen.queryByText('9,034円')).not.toBeInTheDocument()
  expect(screen.queryByText('9,025円')).not.toBeInTheDocument()
})

test('実行すると、要約の下に返済表を表示する', async () => {
  const user = userEvent.setup()
  vi.mocked(simulate).mockResolvedValue(twelveTimesWithSchedule)
  render(<InstallmentPage />)

  await fillAndSubmit(user, '100000', '12', '15.0')

  const table = await screen.findByRole('table')
  expect(within(table).getByText('92,216円')).toBeInTheDocument()
  expect(within(table).getByText('84,343円')).toBeInTheDocument()
  const summary = screen.getByText('手数料の合計')
  expect(
    summary.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING,
  ).toBeTruthy()
})

test('再実行すると、前回の返済表を消して、新しい返済表だけを表示する', async () => {
  const user = userEvent.setup()
  vi.mocked(simulate).mockResolvedValueOnce(twelveTimesWithSchedule)
  render(<InstallmentPage />)
  await fillAndSubmit(user, '100000', '12', '15.0')
  expect(await screen.findByRole('table')).toBeInTheDocument()

  let respond!: (result: InstallmentSimulationResult) => void
  vi.mocked(simulate).mockReturnValueOnce(
    new Promise((resolve) => {
      respond = resolve
    }),
  )
  await fillAndSubmit(user, '100000', '3', '0.0')
  expect(screen.queryByRole('table')).not.toBeInTheDocument()

  respond(threeTimesNoFee)
  expect(await screen.findByText('33,334円')).toBeInTheDocument()
  expect(screen.queryByText('92,216円')).not.toBeInTheDocument()
})
