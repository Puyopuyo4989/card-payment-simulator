import { render, screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import InstallmentSummary from './InstallmentSummary'

/** 項目名（dt）の次にある金額（dd）を返す */
function amountOf(label: string) {
  return screen.getByText(label, { selector: 'dt' }).nextElementSibling
    ?.textContent
}

test('初回と2回目以降が異なるときは、手数料の合計・支払総額と、初回・2回目以降の支払額を分けて表示する', () => {
  // spec US1 の1（100,000円・12回・15.0%）
  render(
    <InstallmentSummary
      result={{
        totalFee: 8309,
        totalPayment: 108309,
        firstPayment: 9034,
        subsequentPayment: 9025,
        schedule: [],
      }}
    />,
  )

  expect(amountOf('手数料の合計')).toBe('8,309円')
  expect(amountOf('支払総額')).toBe('108,309円')
  expect(amountOf('初回の支払額')).toBe('9,034円')
  expect(amountOf('2回目以降の支払額')).toBe('9,025円')
  expect(screen.queryByText('毎月の支払額')).not.toBeInTheDocument()
})

test('初回と2回目以降が同じときは、毎月の支払額として1つだけ表示する', () => {
  // tasks.md T016 の(8)（100,000円・3回・15.0%）
  render(
    <InstallmentSummary
      result={{
        totalFee: 2510,
        totalPayment: 102510,
        firstPayment: 34170,
        subsequentPayment: 34170,
        schedule: [],
      }}
    />,
  )

  expect(amountOf('手数料の合計')).toBe('2,510円')
  expect(amountOf('支払総額')).toBe('102,510円')
  expect(amountOf('毎月の支払額')).toBe('34,170円')
  expect(screen.queryByText('初回の支払額')).not.toBeInTheDocument()
  expect(screen.queryByText('2回目以降の支払額')).not.toBeInTheDocument()
})

test('金額（dd）には、右寄せで桁をそろえる class（num）を付ける（FR-020）', () => {
  render(
    <InstallmentSummary
      result={{
        totalFee: 8309,
        totalPayment: 108309,
        firstPayment: 9034,
        subsequentPayment: 9025,
        schedule: [],
      }}
    />,
  )

  for (const label of [
    '手数料の合計',
    '支払総額',
    '初回の支払額',
    '2回目以降の支払額',
  ]) {
    expect(
      screen.getByText(label, { selector: 'dt' }).nextElementSibling,
    ).toHaveClass('num')
  }
})
