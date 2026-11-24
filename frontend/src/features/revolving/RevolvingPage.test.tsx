import { render, screen, waitFor, within } from '@testing-library/react'
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

// spec US2 の受け入れシナリオ6の表（100,000円・10,000円・15.0% の11行）
const fifteenPercentWithSchedule: RevolvingSimulationResult = {
  ...fifteenPercent,
  schedule: [
    [1, 10000, 1250, 8750, 91250],
    [2, 10000, 1140, 8860, 82390],
    [3, 10000, 1029, 8971, 73419],
    [4, 10000, 917, 9083, 64336],
    [5, 10000, 804, 9196, 55140],
    [6, 10000, 689, 9311, 45829],
    [7, 10000, 572, 9428, 36401],
    [8, 10000, 455, 9545, 26856],
    [9, 10000, 335, 9665, 17191],
    [10, 10000, 214, 9786, 7405],
    [11, 7497, 92, 7405, 0],
  ].map(([number, payment, fee, principal, balanceAfter]) => ({
    number,
    payment,
    principal,
    fee,
    balanceAfter,
  })),
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

function scheduleBodyRows() {
  const [, body] = screen.getAllByRole('rowgroup')
  return within(body).getAllByRole('row')
}

test('実行すると、要約の下に返済表を、APIが返した行の数だけ表示する', async () => {
  vi.mocked(simulate).mockResolvedValue(success(fifteenPercentWithSchedule))
  const user = userEvent.setup()
  render(<RevolvingPage />)

  await fillAndSubmit(user, '100000', '10000', '15.0')

  const table = await screen.findByRole('table')
  const summary = screen.getByText('完済までの支払回数', { selector: 'dt' })
  expect(
    summary.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING,
  ).toBeTruthy()
  const rows = scheduleBodyRows()
  expect(rows).toHaveLength(11)
  expect(
    within(rows[10])
      .getAllByRole('cell')
      .map((cell) => cell.textContent),
  ).toEqual(['11', '7,497円', '92円', '7,405円', '0円'])
})

test('再実行すると、前回の返済表は残らない', async () => {
  vi.mocked(simulate)
    .mockResolvedValueOnce(success(fifteenPercentWithSchedule))
    .mockResolvedValueOnce(success(noFee))
  const user = userEvent.setup()
  render(<RevolvingPage />)

  await fillAndSubmit(user, '100000', '10000', '15.0')
  await screen.findByRole('table')
  await fillAndSubmit(user, '100000', '10000', '0.0')

  await screen.findByText('10回')
  expect(screen.queryAllByRole('row')).toHaveLength(1)
})

// contracts の「払い終わらない」ときのエラー（spec US3、10,000,000円・125,000円・15.0%）
const notPayable: SimulationOutcome = {
  kind: 'inputError',
  errors: [
    {
      field: 'monthlyPayment',
      message:
        '毎月の支払額が1回目の手数料以下のため、残高が減りません。毎月の支払額を増やしてください',
    },
  ],
}

test('入力エラーのときは、結果（要約・返済表）を表示せず、項目ごとのエラーを表示する', async () => {
  vi.mocked(simulate).mockResolvedValue(notPayable)
  const user = userEvent.setup()
  render(<RevolvingPage />)

  await fillAndSubmit(user, '10000000', '125000', '15.0')

  const field = screen.getByRole('textbox', { name: '毎月の支払額（円）' })
  await waitFor(() =>
    expect(field).toHaveAccessibleDescription(
      '毎月の支払額が1回目の手数料以下のため、残高が減りません。毎月の支払額を増やしてください',
    ),
  )
  expect(screen.queryByText('完済までの支払回数')).not.toBeInTheDocument()
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
})

test('正しい結果のあとにエラーの入力で再実行すると、前回の結果を消してエラーだけを表示する', async () => {
  vi.mocked(simulate)
    .mockResolvedValueOnce(success(fifteenPercentWithSchedule))
    .mockResolvedValueOnce(notPayable)
  const user = userEvent.setup()
  render(<RevolvingPage />)

  await fillAndSubmit(user, '100000', '10000', '15.0')
  await screen.findByRole('table')
  await fillAndSubmit(user, '10000000', '125000', '15.0')

  expect(await screen.findByRole('alert')).toHaveTextContent(
    '毎月の支払額が1回目の手数料以下のため',
  )
  expect(screen.queryByText('11回')).not.toBeInTheDocument()
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
})

test('エラーのあとに正しい入力で再実行すると、エラーを消して結果だけを表示する', async () => {
  vi.mocked(simulate)
    .mockResolvedValueOnce(notPayable)
    .mockResolvedValueOnce(success(fifteenPercent))
  const user = userEvent.setup()
  render(<RevolvingPage />)

  await fillAndSubmit(user, '10000000', '125000', '15.0')
  await screen.findByRole('alert')
  await fillAndSubmit(user, '100000', '10000', '15.0')

  expect(await screen.findByText('11回')).toBeInTheDocument()
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

test('予期しないエラーのときは、共通のメッセージを role="alert" で表示し、結果を表示しない', async () => {
  vi.mocked(simulate).mockResolvedValue({ kind: 'unexpectedError' })
  const user = userEvent.setup()
  render(<RevolvingPage />)

  await fillAndSubmit(user, '100000', '10000', '15.0')

  expect(await screen.findByRole('alert')).toHaveTextContent(
    '計算できませんでした。もう一度お試しください',
  )
  expect(screen.queryByText('完済までの支払回数')).not.toBeInTheDocument()
})

test('エラーの応答のあとも「計算する」を押せるようになる', async () => {
  vi.mocked(simulate).mockResolvedValue(notPayable)
  const user = userEvent.setup()
  render(<RevolvingPage />)

  await fillAndSubmit(user, '10000000', '125000', '15.0')

  await screen.findByRole('alert')
  expect(screen.getByRole('button', { name: '計算する' })).toBeEnabled()
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
