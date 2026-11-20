import { render, screen, within } from '@testing-library/react'
import { expect, test } from 'vitest'
import ScheduleTable from './ScheduleTable'
import type { ScheduleRow } from './types'

// spec US2 の受け入れシナリオ5（100,000円・12回・15.0%）の1・2・12回目
const schedule: ScheduleRow[] = [
  { number: 1, payment: 9034, principal: 7784, fee: 1250, balanceAfter: 92216 },
  { number: 2, payment: 9025, principal: 7873, fee: 1152, balanceAfter: 84343 },
  { number: 12, payment: 9025, principal: 8909, fee: 116, balanceAfter: 0 },
]

function bodyRows() {
  const [, body] = screen.getAllByRole('rowgroup')
  return within(body).getAllByRole('row')
}

function cellTexts(row: HTMLElement) {
  return within(row)
    .getAllByRole('cell')
    .map((cell) => cell.textContent)
}

test('列は左から、回・支払額・手数料・元金・支払後の残高', () => {
  render(<ScheduleTable schedule={schedule} />)

  expect(
    screen.getAllByRole('columnheader').map((header) => header.textContent),
  ).toEqual(['回', '支払額', '手数料', '元金', '支払後の残高'])
})

test('行数は返済表の要素の数で、金額は3桁区切りで表示する', () => {
  render(<ScheduleTable schedule={schedule} />)

  const rows = bodyRows()
  expect(rows).toHaveLength(3)
  expect(cellTexts(rows[0])).toEqual([
    '1',
    '9,034円',
    '1,250円',
    '7,784円',
    '92,216円',
  ])
  expect(cellTexts(rows[1])).toEqual([
    '2',
    '9,025円',
    '1,152円',
    '7,873円',
    '84,343円',
  ])
  expect(cellTexts(rows[2])).toEqual([
    '12',
    '9,025円',
    '116円',
    '8,909円',
    '0円',
  ])
})
