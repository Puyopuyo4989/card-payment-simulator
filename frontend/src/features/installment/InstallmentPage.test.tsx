import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'
import { simulate } from './api'
import InstallmentPage from './InstallmentPage'
import type { InstallmentSimulationResult, SimulationOutcome } from './types'

vi.mock('./api')

afterEach(() => {
  vi.resetAllMocks()
})

function success(result: InstallmentSimulationResult): SimulationOutcome {
  return { kind: 'success', result }
}

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
  vi.mocked(simulate).mockResolvedValue(success(twelveTimes))
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
  vi.mocked(simulate).mockResolvedValueOnce(success(twelveTimes))
  render(<InstallmentPage />)
  await fillAndSubmit(user, '100000', '12', '15.0')
  expect(await screen.findByText('8,309円')).toBeInTheDocument()

  // 2回目の応答はすぐに返さず、応答の前に前回の結果が消えていることを確かめる
  let respond!: (outcome: SimulationOutcome) => void
  vi.mocked(simulate).mockReturnValueOnce(
    new Promise((resolve) => {
      respond = resolve
    }),
  )
  await fillAndSubmit(user, '100000', '3', '0.0')
  expect(screen.queryByText('8,309円')).not.toBeInTheDocument()

  respond(success(threeTimesNoFee))
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
  vi.mocked(simulate).mockResolvedValue(success(twelveTimesWithSchedule))
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
  vi.mocked(simulate).mockResolvedValueOnce(success(twelveTimesWithSchedule))
  render(<InstallmentPage />)
  await fillAndSubmit(user, '100000', '12', '15.0')
  expect(await screen.findByRole('table')).toBeInTheDocument()

  let respond!: (outcome: SimulationOutcome) => void
  vi.mocked(simulate).mockReturnValueOnce(
    new Promise((resolve) => {
      respond = resolve
    }),
  )
  await fillAndSubmit(user, '100000', '3', '0.0')
  expect(screen.queryByRole('table')).not.toBeInTheDocument()

  respond(success(threeTimesNoFee))
  expect(await screen.findByText('33,334円')).toBeInTheDocument()
  expect(screen.queryByText('92,216円')).not.toBeInTheDocument()
})

test('実行中は「計算する」を押せず、古い応答で結果が上書きされない', async () => {
  const user = userEvent.setup()
  let respond!: (outcome: SimulationOutcome) => void
  vi.mocked(simulate).mockReturnValueOnce(
    new Promise((resolve) => {
      respond = resolve
    }),
  )
  render(<InstallmentPage />)

  await fillAndSubmit(user, '100000', '12', '15.0')
  const button = screen.getByRole('button', { name: '計算する' })
  expect(button).toBeDisabled()
  // 押せないので、2回目の API は呼ばれない
  await user.click(button)
  expect(simulate).toHaveBeenCalledTimes(1)

  respond(success(twelveTimes))
  expect(await screen.findByText('8,309円')).toBeInTheDocument()
  expect(button).toBeEnabled()
})

test('実行が終わったら、フォーカスを「計算する」に戻す', async () => {
  const user = userEvent.setup()
  let respond!: (outcome: SimulationOutcome) => void
  vi.mocked(simulate).mockReturnValueOnce(
    new Promise((resolve) => {
      respond = resolve
    }),
  )
  render(<InstallmentPage />)
  await fillAndSubmit(user, '100000', '12', '15.0')
  const button = screen.getByRole('button', { name: '計算する' })
  // ブラウザーは押せなくなったボタンからフォーカスを外すが、jsdom は外さないので、ここで外す
  button.blur()
  expect(button).not.toHaveFocus()

  respond(success(twelveTimes))

  expect(await screen.findByText('8,309円')).toBeInTheDocument()
  // フォーカスは useEffect（useReturnFocus）で戻すので、結果の文字が出たあとの別のタスクで動くことがある。
  // 文字が出た直後に確かめると、CI のような遅い環境でまれに落ちるので、戻るまで待つ
  await waitFor(() => expect(button).toHaveFocus())
})

test('入力欄で Enter を押して実行したら、フォーカスは入力欄のまま', async () => {
  const user = userEvent.setup()
  vi.mocked(simulate).mockResolvedValueOnce(success(twelveTimes))
  render(<InstallmentPage />)
  await user.type(
    screen.getByRole('textbox', { name: '利用金額（円）' }),
    '100000',
  )
  await user.selectOptions(
    screen.getByRole('combobox', { name: '支払回数' }),
    '12',
  )
  const annualRateInput = screen.getByRole('textbox', { name: '実質年率（%）' })

  await user.type(annualRateInput, '15.0{Enter}')

  expect(await screen.findByText('8,309円')).toBeInTheDocument()
  expect(annualRateInput).toHaveFocus()
})

test('エラーの応答のあとも「計算する」を押せるようになる', async () => {
  const user = userEvent.setup()
  vi.mocked(simulate).mockResolvedValueOnce({ kind: 'unexpectedError' })
  render(<InstallmentPage />)

  await fillAndSubmit(user, '100000', '12', '15.0')

  expect(
    await screen.findByText('計算できませんでした。もう一度お試しください'),
  ).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '計算する' })).toBeEnabled()
})

test('API の呼び出しが例外を投げても、予期しないエラーを表示し、「計算する」を押せるようになる', async () => {
  const user = userEvent.setup()
  vi.mocked(simulate).mockRejectedValueOnce(new Error('想定外'))
  render(<InstallmentPage />)

  await fillAndSubmit(user, '100000', '12', '15.0')

  expect(
    await screen.findByText('計算できませんでした。もう一度お試しください'),
  ).toBeInTheDocument()
  await waitFor(() =>
    expect(screen.getByRole('button', { name: '計算する' })).toBeEnabled(),
  )
})

// spec US3 の3（利用金額が未入力・4回・12.25%）
const inputError: SimulationOutcome = {
  kind: 'inputError',
  errors: [
    { field: 'amount', message: '利用金額を入力してください' },
    {
      field: 'paymentCount',
      message:
        '支払回数は3・5・6・10・12・15・18・20・24・30・36回から選んでください',
    },
    {
      field: 'annualRate',
      message:
        '実質年率は半角数字で、小数第1位までで入力してください（例：12.5）',
    },
  ],
}

const unexpectedMessage = '計算できませんでした。もう一度お試しください'

test('入力エラーのときは、結果を表示せず、項目ごとのエラーを表示する', async () => {
  const user = userEvent.setup()
  vi.mocked(simulate).mockResolvedValue(inputError)
  render(<InstallmentPage />)

  await fillAndSubmit(user, '100000', '12', '15.0')

  expect(
    await screen.findByText('利用金額を入力してください'),
  ).toBeInTheDocument()
  expect(
    screen.getByText(
      '支払回数は3・5・6・10・12・15・18・20・24・30・36回から選んでください',
    ),
  ).toBeInTheDocument()
  expect(
    screen.getByText(
      '実質年率は半角数字で、小数第1位までで入力してください（例：12.5）',
    ),
  ).toBeInTheDocument()
  expect(screen.queryByText('手数料の合計')).not.toBeInTheDocument()
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
})

test('正しい結果のあとにエラーの入力で再実行すると、前回の結果を消してエラーだけを表示する', async () => {
  const user = userEvent.setup()
  vi.mocked(simulate)
    .mockResolvedValueOnce(success(twelveTimesWithSchedule))
    .mockResolvedValueOnce(inputError)
  render(<InstallmentPage />)
  await fillAndSubmit(user, '100000', '12', '15.0')
  expect(await screen.findByText('8,309円')).toBeInTheDocument()

  await fillAndSubmit(user, '100000', '12', '12.25')

  expect(
    await screen.findByText('利用金額を入力してください'),
  ).toBeInTheDocument()
  expect(screen.queryByText('8,309円')).not.toBeInTheDocument()
  expect(screen.queryByText('手数料の合計')).not.toBeInTheDocument()
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
})

test('エラーのあとに正しい入力で再実行すると、エラーを消して結果だけを表示する', async () => {
  const user = userEvent.setup()
  vi.mocked(simulate).mockResolvedValueOnce(inputError)
  render(<InstallmentPage />)
  await fillAndSubmit(user, '100000', '12', '12.25')
  expect(
    await screen.findByText('利用金額を入力してください'),
  ).toBeInTheDocument()

  // 応答の前に、前回のエラーが消えていることを確かめる
  let respond!: (outcome: SimulationOutcome) => void
  vi.mocked(simulate).mockReturnValueOnce(
    new Promise((resolve) => {
      respond = resolve
    }),
  )
  await fillAndSubmit(user, '100000', '12', '15.0')
  expect(
    screen.queryByText('利用金額を入力してください'),
  ).not.toBeInTheDocument()

  respond(success(twelveTimes))
  expect(await screen.findByText('8,309円')).toBeInTheDocument()
  expect(
    screen.queryByText('利用金額を入力してください'),
  ).not.toBeInTheDocument()
})

test('予期しないエラーのときは、共通のメッセージを表示し、結果を表示しない', async () => {
  const user = userEvent.setup()
  vi.mocked(simulate)
    .mockResolvedValueOnce(success(twelveTimes))
    .mockResolvedValueOnce({ kind: 'unexpectedError' })
  render(<InstallmentPage />)
  await fillAndSubmit(user, '100000', '12', '15.0')
  expect(await screen.findByText('8,309円')).toBeInTheDocument()

  await fillAndSubmit(user, '100000', '12', '15.0')

  expect(await screen.findByText(unexpectedMessage)).toBeInTheDocument()
  expect(screen.queryByText('8,309円')).not.toBeInTheDocument()
})

test('予期しないエラーのメッセージは role="alert" で、スクリーンリーダーに伝える', async () => {
  const user = userEvent.setup()
  vi.mocked(simulate).mockResolvedValueOnce({ kind: 'unexpectedError' })
  render(<InstallmentPage />)

  await fillAndSubmit(user, '100000', '12', '15.0')

  expect(await screen.findByRole('alert')).toHaveTextContent(unexpectedMessage)
})

test('予期しないエラーのあとに再実行すると、共通のメッセージを消す', async () => {
  const user = userEvent.setup()
  vi.mocked(simulate)
    .mockResolvedValueOnce({ kind: 'unexpectedError' })
    .mockResolvedValueOnce(success(twelveTimes))
  render(<InstallmentPage />)
  await fillAndSubmit(user, '100000', '12', '15.0')
  expect(await screen.findByText(unexpectedMessage)).toBeInTheDocument()

  await fillAndSubmit(user, '100000', '12', '15.0')

  expect(await screen.findByText('8,309円')).toBeInTheDocument()
  expect(screen.queryByText(unexpectedMessage)).not.toBeInTheDocument()
})
