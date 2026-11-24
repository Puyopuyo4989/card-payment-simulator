import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test } from 'vitest'
import App from './App'

test('見出し「カード支払シミュレーション」が表示される', () => {
  render(<App />)

  expect(
    screen.getByRole('heading', { name: 'カード支払シミュレーション' }),
  ).toBeInTheDocument()
})

test('タブ「分割払い」「リボ払い」がある', () => {
  render(<App />)

  const tablist = screen.getByRole('tablist')
  expect(
    within(tablist)
      .getAllByRole('tab')
      .map((tab) => tab.textContent),
  ).toEqual(['分割払い', 'リボ払い'])
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

test('タブと表示中のパネルは aria-controls と aria-labelledby で結び付いている', () => {
  render(<App />)

  const tab = screen.getByRole('tab', { name: '分割払い' })
  const panel = screen.getByRole('tabpanel', { name: '分割払い' })
  expect(tab).toHaveAttribute('aria-controls', panel.id)
})
