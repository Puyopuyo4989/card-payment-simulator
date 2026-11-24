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
