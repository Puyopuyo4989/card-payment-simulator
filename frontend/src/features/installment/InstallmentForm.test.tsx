import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'
import InstallmentForm from './InstallmentForm'
import type { FieldError } from './types'

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

const errors: FieldError[] = [
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
]

test.each([
  ['利用金額（円）', 'textbox', 0],
  ['支払回数', 'combobox', 1],
  ['実質年率（%）', 'textbox', 2],
] as const)(
  'エラーを渡すと、%s の入力欄のすぐ下に赤字で表示し、aria-describedby で結び付ける',
  (name, role, index) => {
    render(<InstallmentForm onSubmit={vi.fn()} errors={errors} />)

    const field = screen.getByRole(role, { name })
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
  render(<InstallmentForm onSubmit={vi.fn()} errors={errors} />)

  expect(alert).not.toHaveBeenCalled()
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  alert.mockRestore()
})

test('エラーのメッセージは role="alert" で、スクリーンリーダーに伝える', () => {
  render(<InstallmentForm onSubmit={vi.fn()} errors={errors} />)

  expect(
    screen.getAllByRole('alert').map((alert) => alert.textContent),
  ).toEqual(errors.map((error) => error.message))
})

test('エラーがない項目には、メッセージも aria-describedby も付けない', () => {
  render(<InstallmentForm onSubmit={vi.fn()} errors={[errors[0]]} />)

  expect(
    screen.getByRole('combobox', { name: '支払回数' }),
  ).not.toHaveAttribute('aria-describedby')
  expect(
    screen.getByRole('textbox', { name: '実質年率（%）' }),
  ).not.toHaveAttribute('aria-describedby')
  expect(screen.queryByText(errors[2].message)).not.toBeInTheDocument()
})

test('実行中（submitting）は「計算する」を押せない', () => {
  render(<InstallmentForm onSubmit={vi.fn()} submitting />)

  expect(screen.getByRole('button', { name: '計算する' })).toBeDisabled()
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

// 【Should】入力欄の表示（FR-015〜FR-018）
const amountBox = () => screen.getByRole('textbox', { name: '利用金額（円）' })
const rateBox = () => screen.getByRole('textbox', { name: '実質年率（%）' })

test('利用金額は、入力中はカンマを差し込まず、フォーカスが外れたら3桁区切りにする（FR-015・FR-018）', async () => {
  const user = userEvent.setup()
  render(<InstallmentForm onSubmit={vi.fn()} />)

  await user.type(amountBox(), '1000000')
  expect(amountBox()).toHaveValue('1000000')

  await user.tab()
  expect(amountBox()).toHaveValue('1,000,000')
})

test('実質年率は、整数ならフォーカスが外れたら小数第1位まで表示する（FR-016）', async () => {
  const user = userEvent.setup()
  render(<InstallmentForm onSubmit={vi.fn()} />)

  await user.type(rateBox(), '15')
  expect(rateBox()).toHaveValue('15')

  await user.tab()
  expect(rateBox()).toHaveValue('15.0')
})

test('カンマ付きの利用金額は、カンマを取り除いて送る（利用者が入れたカンマも。FR-015・FR-017）', async () => {
  const user = userEvent.setup()
  const onSubmit = vi.fn()
  render(<InstallmentForm onSubmit={onSubmit} />)

  await user.type(amountBox(), '1,00,000')
  await user.type(rateBox(), '15')
  await user.click(screen.getByRole('button', { name: '計算する' }))

  expect(amountBox()).toHaveValue('100,000')
  expect(rateBox()).toHaveValue('15.0')
  expect(onSubmit).toHaveBeenLastCalledWith({
    amount: '100000',
    paymentCount: '',
    annualRate: '15.0',
  })
})

test('入力欄で Enter を押して送ったときも、カンマを取り除いて送る（表示はフォーカスがあるので整えない）', async () => {
  const user = userEvent.setup()
  const onSubmit = vi.fn()
  render(<InstallmentForm onSubmit={onSubmit} />)

  await user.type(amountBox(), '100,000{Enter}')

  expect(amountBox()).toHaveValue('100,000')
  expect(onSubmit).toHaveBeenLastCalledWith({
    amount: '100000',
    paymentCount: '',
    annualRate: '',
  })
})

test.each([
  ['abc', '15.5'],
  ['1000.5', '12.25'],
  ['1,000.5', 'abc'],
  ['-1,000', '１５'],
])(
  '整えられない値（利用金額 %j・実質年率 %j）は、入力されたまま残して、そのまま送る（API がエラーにする）',
  async (amount, rate) => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(<InstallmentForm onSubmit={onSubmit} />)

    await user.type(amountBox(), amount)
    await user.type(rateBox(), rate)
    await user.click(screen.getByRole('button', { name: '計算する' }))

    expect(amountBox()).toHaveValue(amount)
    expect(rateBox()).toHaveValue(rate)
    expect(onSubmit).toHaveBeenLastCalledWith({
      amount,
      paymentCount: '',
      annualRate: rate,
    })
  },
)

test('後ろに空白のある「100,000 」（コピーして貼り付けたとき）も、空白とカンマを取り除いて受け付ける', async () => {
  const user = userEvent.setup()
  const onSubmit = vi.fn()
  render(<InstallmentForm onSubmit={onSubmit} />)

  await user.click(amountBox())
  await user.paste('100,000 ')
  await user.click(screen.getByRole('button', { name: '計算する' }))

  expect(amountBox()).toHaveValue('100,000')
  expect(onSubmit).toHaveBeenLastCalledWith({
    amount: '100000',
    paymentCount: '',
    annualRate: '',
  })
})

test('大きな桁の利用金額も、丸めずにカンマを付け、カンマを取り除いて送る（数に変えない）', async () => {
  const user = userEvent.setup()
  const onSubmit = vi.fn()
  render(<InstallmentForm onSubmit={onSubmit} />)

  await user.type(amountBox(), '12345678901234567890')
  await user.click(screen.getByRole('button', { name: '計算する' }))

  expect(amountBox()).toHaveValue('12,345,678,901,234,567,890')
  expect(onSubmit).toHaveBeenLastCalledWith({
    amount: '12345678901234567890',
    paymentCount: '',
    annualRate: '',
  })
})
