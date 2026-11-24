import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'
import RevolvingForm from './RevolvingForm'

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

test('実行中は「計算する」を押せない', () => {
  render(<RevolvingForm onSubmit={vi.fn()} submitting />)

  expect(screen.getByRole('button', { name: '計算する' })).toBeDisabled()
})
