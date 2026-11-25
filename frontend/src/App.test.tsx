import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'
import App from './App'

// 申込管理の一覧は開くと API を呼ぶので、差し替えるのは api.ts だけにする（tasks.md T042）
vi.mock('./features/application/api', () => ({
  list: vi.fn().mockResolvedValue({ kind: 'success', result: { items: [] } }),
  register: vi.fn(),
  getDetail: vi.fn(),
  changeStatus: vi.fn(),
}))

test('見出し「カード支払シミュレーション」が表示される', () => {
  render(<App />)

  expect(
    screen.getByRole('heading', { name: 'カード支払シミュレーション' }),
  ).toBeInTheDocument()
})

test('タブ「分割払い」「リボ払い」「申込管理」がある', () => {
  render(<App />)

  const tablist = screen.getByRole('tablist')
  expect(
    within(tablist)
      .getAllByRole('tab')
      .map((tab) => tab.textContent),
  ).toEqual(['分割払い', 'リボ払い', '申込管理'])
})

test('「申込管理」を押すと、申込管理の画面がタブの中に表示される', async () => {
  const user = userEvent.setup()
  render(<App />)

  await user.click(screen.getByRole('tab', { name: '申込管理' }))

  expect(screen.getByRole('tab', { name: '申込管理' })).toHaveAttribute(
    'aria-selected',
    'true',
  )
  expect(screen.getByRole('tab', { name: '分割払い' })).toHaveAttribute(
    'aria-selected',
    'false',
  )
  // ApplicationPage は差し替えず、本物が「新規登録」のボタンを出すことを確かめる
  const panel = screen.getByRole('tabpanel', { name: '申込管理' })
  expect(
    await within(panel).findByRole('button', { name: '新規登録' }),
  ).toBeInTheDocument()
  expect(
    screen.queryByRole('combobox', { name: '支払回数' }),
  ).not.toBeInTheDocument()
})

test('最初は「分割払い」を選んでいて、分割払いの入力フォームがタブの中に表示される', () => {
  render(<App />)

  expect(screen.getByRole('tab', { name: '分割払い' })).toHaveAttribute(
    'aria-selected',
    'true',
  )
  expect(screen.getByRole('tab', { name: 'リボ払い' })).toHaveAttribute(
    'aria-selected',
    'false',
  )
  const panel = screen.getByRole('tabpanel')
  expect(
    within(panel).getByRole('combobox', { name: '支払回数' }),
  ).toBeInTheDocument()
  expect(
    within(panel).getByRole('button', { name: '計算する' }),
  ).toBeInTheDocument()
})

test('「リボ払い」を押すと選んでいるタブが入れ替わり、分割払いのフォームは表示されない', async () => {
  const user = userEvent.setup()
  render(<App />)

  await user.click(screen.getByRole('tab', { name: 'リボ払い' }))

  expect(screen.getByRole('tab', { name: 'リボ払い' })).toHaveAttribute(
    'aria-selected',
    'true',
  )
  expect(screen.getByRole('tab', { name: '分割払い' })).toHaveAttribute(
    'aria-selected',
    'false',
  )
  expect(
    screen.queryByRole('combobox', { name: '支払回数' }),
  ).not.toBeInTheDocument()
})

test('「リボ払い」を押すと、リボ払いの入力フォームがタブの中に表示される', async () => {
  const user = userEvent.setup()
  render(<App />)

  await user.click(screen.getByRole('tab', { name: 'リボ払い' }))

  const panel = screen.getByRole('tabpanel', { name: 'リボ払い' })
  expect(
    within(panel).getByRole('textbox', { name: '毎月の支払額（円）' }),
  ).toBeInTheDocument()
})

test('「分割払い」に戻すと、分割払いのフォームがまた表示される', async () => {
  const user = userEvent.setup()
  render(<App />)

  await user.click(screen.getByRole('tab', { name: 'リボ払い' }))
  await user.click(screen.getByRole('tab', { name: '分割払い' }))

  expect(screen.getByRole('combobox', { name: '支払回数' })).toBeInTheDocument()
})

test('選んでいるタブだけに、見た目で区別するための CSS のクラスが付く', async () => {
  const user = userEvent.setup()
  render(<App />)

  expect(screen.getByRole('tab', { name: '分割払い' })).toHaveClass(
    'tab-selected',
  )
  expect(screen.getByRole('tab', { name: 'リボ払い' })).not.toHaveClass(
    'tab-selected',
  )

  await user.click(screen.getByRole('tab', { name: 'リボ払い' }))

  expect(screen.getByRole('tab', { name: 'リボ払い' })).toHaveClass(
    'tab-selected',
  )
  expect(screen.getByRole('tab', { name: '分割払い' })).not.toHaveClass(
    'tab-selected',
  )
})

test('タブと表示中のパネルは aria-controls と aria-labelledby で結び付いている', () => {
  render(<App />)

  const tab = screen.getByRole('tab', { name: '分割払い' })
  const panel = screen.getByRole('tabpanel', { name: '分割払い' })
  expect(tab).toHaveAttribute('aria-controls', panel.id)
})
