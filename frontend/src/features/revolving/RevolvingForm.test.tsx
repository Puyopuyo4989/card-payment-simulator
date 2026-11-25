import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'
import RevolvingForm from './RevolvingForm'
import type { FieldError } from './types'

test('3項目とも文字列の入力欄にする', () => {
  render(<RevolvingForm onSubmit={vi.fn()} />)

  expect(
    screen.getByRole('textbox', { name: '利用残高（円）' }),
  ).toHaveAttribute('type', 'text')
  expect(
    screen.getByRole('textbox', { name: '毎月の支払額（円）' }),
  ).toHaveAttribute('type', 'text')
  expect(
    screen.getByRole('textbox', { name: '実質年率（%）' }),
  ).toHaveAttribute('type', 'text')
})

test('金額の欄は数字、実質年率の欄は小数のキーボードを出す', () => {
  render(<RevolvingForm onSubmit={vi.fn()} />)

  expect(
    screen.getByRole('textbox', { name: '利用残高（円）' }),
  ).toHaveAttribute('inputmode', 'numeric')
  expect(
    screen.getByRole('textbox', { name: '毎月の支払額（円）' }),
  ).toHaveAttribute('inputmode', 'numeric')
  expect(
    screen.getByRole('textbox', { name: '実質年率（%）' }),
  ).toHaveAttribute('inputmode', 'decimal')
})

test('実行すると、入力されたままの文字列で送信の処理を呼ぶ', async () => {
  const user = userEvent.setup()
  const onSubmit = vi.fn()
  render(<RevolvingForm onSubmit={onSubmit} />)

  await user.type(
    screen.getByRole('textbox', { name: '利用残高（円）' }),
    '100000',
  )
  await user.type(
    screen.getByRole('textbox', { name: '毎月の支払額（円）' }),
    '10000',
  )
  await user.type(
    screen.getByRole('textbox', { name: '実質年率（%）' }),
    '15.0',
  )
  await user.click(screen.getByRole('button', { name: '計算する' }))

  expect(onSubmit).toHaveBeenCalledTimes(1)
  expect(onSubmit).toHaveBeenCalledWith({
    balance: '100000',
    monthlyPayment: '10000',
    annualRate: '15.0',
  })
})

// contracts/revolving-simulation-api.md のメッセージ
const errors: FieldError[] = [
  { field: 'balance', message: '利用残高を入力してください' },
  {
    field: 'monthlyPayment',
    message: '毎月の支払額は利用残高以下で入力してください',
  },
  {
    field: 'annualRate',
    message:
      '実質年率は半角数字で、小数第1位までで入力してください（例：12.5）',
  },
]

test.each([
  ['利用残高（円）', 0],
  ['毎月の支払額（円）', 1],
  ['実質年率（%）', 2],
] as const)(
  'エラーを渡すと、%s の入力欄のすぐ下に赤字で表示し、aria-describedby で結び付ける',
  (name, index) => {
    render(<RevolvingForm onSubmit={vi.fn()} errors={errors} />)

    const field = screen.getByRole('textbox', { name })
    const message = screen.getByText(errors[index].message)
    // すぐ下：入力欄と同じまとまりの中で、入力欄のあとに置く
    expect(message.parentElement).toBe(field.parentElement)
    expect(
      field.compareDocumentPosition(message) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()
    expect(message).toHaveClass('field-error')
    expect(field).toHaveAccessibleDescription(errors[index].message)
    expect(field).toHaveAttribute('aria-invalid', 'true')
  },
)

test('エラーはポップアップ（window.alert・ダイアログ）で出さない', () => {
  const alert = vi.spyOn(window, 'alert').mockImplementation(() => {})
  render(<RevolvingForm onSubmit={vi.fn()} errors={errors} />)

  expect(alert).not.toHaveBeenCalled()
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  alert.mockRestore()
})

test('エラーのメッセージは role="alert" で、スクリーンリーダーに伝える', () => {
  render(<RevolvingForm onSubmit={vi.fn()} errors={errors} />)

  expect(
    screen.getAllByRole('alert').map((alert) => alert.textContent),
  ).toEqual(errors.map((error) => error.message))
})

test('エラーがない項目には、メッセージも aria-describedby も付けない', () => {
  render(<RevolvingForm onSubmit={vi.fn()} errors={[errors[1]]} />)

  expect(
    screen.getByRole('textbox', { name: '利用残高（円）' }),
  ).not.toHaveAttribute('aria-describedby')
  expect(
    screen.getByRole('textbox', { name: '実質年率（%）' }),
  ).not.toHaveAttribute('aria-describedby')
  expect(screen.queryByText(errors[0].message)).not.toBeInTheDocument()
})

test('実行中は「計算する」を押せない', () => {
  render(<RevolvingForm onSubmit={vi.fn()} submitting />)

  expect(screen.getByRole('button', { name: '計算する' })).toBeDisabled()
})

// 【Should】入力欄の表示（FR-016〜FR-019。分割払いの FR-015〜FR-018 と同じ動き）
const balanceBox = () => screen.getByRole('textbox', { name: '利用残高（円）' })
const paymentBox = () =>
  screen.getByRole('textbox', { name: '毎月の支払額（円）' })
const rateBox = () => screen.getByRole('textbox', { name: '実質年率（%）' })

test.each([
  ['利用残高（円）', balanceBox],
  ['毎月の支払額（円）', paymentBox],
])(
  '%s は、入力中はカンマを差し込まず、フォーカスが外れたら3桁区切りにする（FR-016・FR-019）',
  async (_name, box) => {
    const user = userEvent.setup()
    render(<RevolvingForm onSubmit={vi.fn()} />)

    await user.type(box(), '1000000')
    expect(box()).toHaveValue('1000000')

    await user.tab()
    expect(box()).toHaveValue('1,000,000')
  },
)

test('実質年率は、整数ならフォーカスが外れたら小数第1位まで表示する（FR-017）', async () => {
  const user = userEvent.setup()
  render(<RevolvingForm onSubmit={vi.fn()} />)

  await user.type(rateBox(), '15')
  expect(rateBox()).toHaveValue('15')

  await user.tab()
  expect(rateBox()).toHaveValue('15.0')
})

test('カンマ付きの金額は、カンマを取り除いて送る（利用者が入れたカンマも。FR-016・FR-018）', async () => {
  const user = userEvent.setup()
  const onSubmit = vi.fn()
  render(<RevolvingForm onSubmit={onSubmit} />)

  await user.type(balanceBox(), '1,00,000')
  await user.type(paymentBox(), '10,000')
  await user.type(rateBox(), '15')
  await user.click(screen.getByRole('button', { name: '計算する' }))

  expect(balanceBox()).toHaveValue('100,000')
  expect(paymentBox()).toHaveValue('10,000')
  expect(rateBox()).toHaveValue('15.0')
  expect(onSubmit).toHaveBeenLastCalledWith({
    balance: '100000',
    monthlyPayment: '10000',
    annualRate: '15.0',
  })
})

test('入力欄で Enter を押して送ったときも、カンマを取り除いて送る（表示はフォーカスがあるので整えない）', async () => {
  const user = userEvent.setup()
  const onSubmit = vi.fn()
  render(<RevolvingForm onSubmit={onSubmit} />)

  await user.type(balanceBox(), '100,000')
  await user.type(paymentBox(), '10,000{Enter}')

  expect(paymentBox()).toHaveValue('10,000')
  expect(onSubmit).toHaveBeenLastCalledWith({
    balance: '100000',
    monthlyPayment: '10000',
    annualRate: '',
  })
})

test.each([
  ['abc', '1000.5', '15.5'],
  ['1,000.5', '-1,000', '12.25'],
  ['１００００', '10,000円', 'abc'],
])(
  '整えられない値（利用残高 %j・毎月の支払額 %j・実質年率 %j）は、入力されたまま残して、そのまま送る（API がエラーにする）',
  async (balance, monthlyPayment, annualRate) => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(<RevolvingForm onSubmit={onSubmit} />)

    await user.type(balanceBox(), balance)
    await user.type(paymentBox(), monthlyPayment)
    await user.type(rateBox(), annualRate)
    await user.click(screen.getByRole('button', { name: '計算する' }))

    expect(balanceBox()).toHaveValue(balance)
    expect(paymentBox()).toHaveValue(monthlyPayment)
    expect(rateBox()).toHaveValue(annualRate)
    expect(onSubmit).toHaveBeenLastCalledWith({
      balance,
      monthlyPayment,
      annualRate,
    })
  },
)

test('大きな桁の金額も、丸めずにカンマを付け、カンマを取り除いて送る（数に変えない）', async () => {
  const user = userEvent.setup()
  const onSubmit = vi.fn()
  render(<RevolvingForm onSubmit={onSubmit} />)

  await user.type(balanceBox(), '12345678901234567890')
  await user.type(paymentBox(), '98765432109876543210')
  await user.click(screen.getByRole('button', { name: '計算する' }))

  expect(balanceBox()).toHaveValue('12,345,678,901,234,567,890')
  expect(paymentBox()).toHaveValue('98,765,432,109,876,543,210')
  expect(onSubmit).toHaveBeenLastCalledWith({
    balance: '12345678901234567890',
    monthlyPayment: '98765432109876543210',
    annualRate: '',
  })
})
