import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'
import InstallmentForm from './InstallmentForm'

test('利用金額と実質年率は文字列の入力欄にする', () => {
  render(<InstallmentForm onSubmit={vi.fn()} />)

  expect(
    screen.getByRole('textbox', { name: '利用金額（円）' }),
  ).toHaveAttribute('type', 'text')
  expect(
    screen.getByRole('textbox', { name: '実質年率（%）' }),
  ).toHaveAttribute('type', 'text')
})

test('支払回数は FR-003 の11種類と、空の「選んでください」から選ぶ', () => {
  render(<InstallmentForm onSubmit={vi.fn()} />)

  const options = screen.getAllByRole('option')
  expect(options.map((option) => option.textContent)).toEqual([
    '選んでください',
    '3回',
    '5回',
    '6回',
    '10回',
    '12回',
    '15回',
    '18回',
    '20回',
    '24回',
    '30回',
    '36回',
  ])
  expect(options.map((option) => option.getAttribute('value'))).toEqual([
    '',
    '3',
    '5',
    '6',
    '10',
    '12',
    '15',
    '18',
    '20',
    '24',
    '30',
    '36',
  ])
  expect(screen.getByRole('combobox', { name: '支払回数' })).toHaveValue('')
})

test('実行すると、入力されたままの文字列で送信の処理を呼ぶ', async () => {
  const user = userEvent.setup()
  const onSubmit = vi.fn()
  render(<InstallmentForm onSubmit={onSubmit} />)

  await user.type(
    screen.getByRole('textbox', { name: '利用金額（円）' }),
    '100000',
  )
  await user.selectOptions(
    screen.getByRole('combobox', { name: '支払回数' }),
    '12',
  )
  await user.type(
    screen.getByRole('textbox', { name: '実質年率（%）' }),
    '15.0',
  )
  await user.click(screen.getByRole('button', { name: '計算する' }))

  expect(onSubmit).toHaveBeenCalledTimes(1)
  expect(onSubmit).toHaveBeenCalledWith({
    amount: '100000',
    paymentCount: '12',
    annualRate: '15.0',
  })
})

test('入力のチェックはサーバーが行うので、空のままでも送信の処理を呼ぶ', async () => {
  const user = userEvent.setup()
  const onSubmit = vi.fn()
  render(<InstallmentForm onSubmit={onSubmit} />)

  await user.click(screen.getByRole('button', { name: '計算する' }))

  expect(onSubmit).toHaveBeenCalledWith({
    amount: '',
    paymentCount: '',
    annualRate: '',
  })
})
